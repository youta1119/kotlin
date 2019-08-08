/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.resolve.dfa

import com.google.common.collect.LinkedHashMultimap
import com.google.common.collect.Multimap
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.declarations.FirTypedDeclaration
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.render
import org.jetbrains.kotlin.fir.resolve.transformers.resultType
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.resolve.calls.NewCommonSuperTypeCalculator
import org.jetbrains.kotlin.types.model.TypeSystemCommonSuperTypesContext
import kotlin.math.min


/*
 * isSynthetic = false for variables that represents actual variables in fir
 * isSynthetic = true for complex expressions (like when expression)
 */
data class DataFlowVariable(
    val name: String,
    val type: FirTypeRef,
    val isSynthetic: Boolean
) {
    override fun toString(): String {
        return "${if (isSynthetic) "synth " else ""}DFVar: $name: ${type.coneTypeUnsafe<ConeKotlinType>()}"
    }
}

class DataFlowVariableStorage {
    private val dfi2FirMap: Multimap<DataFlowVariable, FirElement> = LinkedHashMultimap.create()
    private val fir2DfiMap: MutableMap<FirElement, DataFlowVariable> = mutableMapOf()

    fun createNewRealVariable(fir: FirElement): DataFlowVariable {
        return DataFlowVariable(fir.render(), fir.type, false).also { storeVariable(it, fir) }
    }

    fun createNewSyntheticVariable(fir: FirElement): DataFlowVariable {
        return DataFlowVariable(fir.render(), fir.type, true).also { storeVariable(it, fir) }
    }

    fun removeVariable(variable: DataFlowVariable) {
        val firExpressions = dfi2FirMap.removeAll(variable)
        firExpressions.forEach(fir2DfiMap::remove)
    }

    private fun storeVariable(variable: DataFlowVariable, fir: FirElement) {
        dfi2FirMap.put(variable, fir)
        fir2DfiMap.put(fir, variable)
    }

    operator fun get(variable: DataFlowVariable): Collection<FirElement> {
        return dfi2FirMap[variable]
    }

    operator fun get(firElement: FirElement): DataFlowVariable? {
        return fir2DfiMap[firElement]
    }

    fun reset() {
        dfi2FirMap.clear()
        fir2DfiMap.clear()
    }
}

private val FirElement.type: FirTypeRef
    get() = when (this) {
        is FirExpression -> this.resultType
        is FirTypedDeclaration -> this.returnTypeRef
        else -> TODO()
    }

// --------------------------------------------------------------------------------------

sealed class Condition {
    abstract fun canBeVerifiedBy(other: Condition): Boolean
    abstract fun canBeRejectedBy(other: Condition): Boolean

    abstract fun negate(): Condition
}

// variable == value
data class BooleanCondition(val variable: DataFlowVariable, val value: Boolean) : Condition() {
    override fun canBeVerifiedBy(other: Condition): Boolean {
        if (other !is BooleanCondition) return false
        return this == other
    }

    override fun canBeRejectedBy(other: Condition): Boolean {
        if (other !is BooleanCondition) return false
        return variable == other.variable && value != other.value
    }

    override fun negate(): Condition {
        return BooleanCondition(variable, !value)
    }

    override fun toString(): String {
        return "$variable == $value"
    }
}

// --------------------------------------------------------------------------------------

data class FirDataFlowInfo(
    val variable: DataFlowVariable,
    val exactTypes: Set<ConeKotlinType>,
    val exactNotTypes: Set<ConeKotlinType>
)

data class UnverifiedInfo(
    val condition: Condition,
    val dataFlowInfo: FirDataFlowInfo
)

data class VerifiedInfo(
    val dataFlowInfo: FirDataFlowInfo,
    val level: Int
)

// --------------------------------------------------------------------------------------

typealias UnverifiedInfos = Collection<UnverifiedInfo>
typealias VerifiedInfos = Collection<VerifiedInfo>

class Facts(
    val unverifiedInfos: UnverifiedInfos,
    val verifiedInfos: VerifiedInfos
) {
    companion object {
        val EMPTY = Facts(emptyList(), emptyList())
    }

    val unverifiedInfoMap: Map<Condition, List<FirDataFlowInfo>>
        get() = unverifiedInfos.groupByTo(mutableMapOf(), { it.condition }) { it.dataFlowInfo }

    operator fun plus(info: VerifiedInfo): Facts = Facts(unverifiedInfos, verifiedInfos.toMutableSet().also { it += info })

    operator fun plus(info: UnverifiedInfo): Facts = Facts(unverifiedInfos.toMutableSet().also { it += info }, verifiedInfos)

    @JvmName("plusVerifiedInfos")
    operator fun plus(infos: VerifiedInfos): Facts = Facts(unverifiedInfos, verifiedInfos.toMutableSet().also { it.addAll(infos) })

    @JvmName("plusUnverifiedInfos")
    operator fun plus(infos: UnverifiedInfos): Facts = Facts(unverifiedInfos.toMutableSet().also { it.addAll(infos) }, verifiedInfos)
}

class FactSystem(val context: ConeTypeContext) {
    fun verifyFacts(facts: Facts, conditionToAccept: Condition, level: Int): Facts {
        val verifiedInfos = facts.verifiedInfos.toMutableSet()
        val unverifiedInfos = mutableSetOf<UnverifiedInfo>()
        for (unverifiedInfo in facts.unverifiedInfos) {
            val (condition, info) = unverifiedInfo
            when {
                condition.canBeVerifiedBy(conditionToAccept) -> verifiedInfos += VerifiedInfo(info, level)
                // TODO: think about it
                !condition.canBeRejectedBy(conditionToAccept) -> unverifiedInfos += unverifiedInfo
            }
        }
        return Facts(unverifiedInfos, verifiedInfos)
    }

    fun removeStaleFacts(facts: Facts, currentLevel: Int): Facts {
        val cleanedInfos = facts.verifiedInfos.filter { it.level > currentLevel }
        if (cleanedInfos.size == facts.verifiedInfos.size) return facts
        return Facts(facts.unverifiedInfos, cleanedInfos)
    }

    fun foldFacts(facts: Collection<Facts>): Facts = facts.singleOrNull() ?: facts.reduce { x, y -> x or y}

    infix fun Facts.or(other: Facts): Facts {
        val unverifiedInfos = this.orForUnverifiedFacts(other)
        val verifiedInfos = this.orForVerifiedFacts(other)
        return Facts(unverifiedInfos, verifiedInfos)
    }

    private fun Facts.orForVerifiedFacts(other: Facts): Collection<VerifiedInfo> {
        return verifiedInfos.cartesianProduct(other.verifiedInfos)
            .mapNotNull { (x, y) ->
                val dfi = x.dataFlowInfo.or(y.dataFlowInfo) ?: return@mapNotNull null
                VerifiedInfo(dfi, min(x.level, y.level))
            }
    }

    private fun Facts.orForUnverifiedFacts(other: Facts): Collection<UnverifiedInfo> {
        val unverifiedInfoMap = this.unverifiedInfoMap
        val otherUnverifiedInfoMap = other.unverifiedInfoMap
        val commonConditions = unverifiedInfoMap.keys.intersect(otherUnverifiedInfoMap.keys)
        val resultUnverifiedInfos = mutableListOf<UnverifiedInfo>()
        for (condition in commonConditions) {
            val myInfos = unverifiedInfoMap[condition]!!
            val otherInfos = otherUnverifiedInfoMap[condition]!!
            myInfos.cartesianProduct(otherInfos).asSequence()
                .mapNotNull { (x, y) -> x.or(y) }
                .map { UnverifiedInfo(condition, it) }
                .forEach { resultUnverifiedInfos += it }
        }
        return resultUnverifiedInfos
    }

    fun squashExactTypes(infos: Collection<FirDataFlowInfo>): Collection<ConeKotlinType> {
        if (infos.isEmpty()) return emptyList()
        val variable = infos.first().variable
        assert(infos.all { it.variable == variable })
        val exactTypes = infos.flatMap { it.exactTypes }
        val exactNotTypes = infos.flatMap { it.exactNotTypes }
        return exactTypes - exactNotTypes
    }

    infix fun FirDataFlowInfo.or(other: FirDataFlowInfo): FirDataFlowInfo? {
        if (variable != other.variable) return null

        val exactTypes = intersectTypes(exactTypes.toList(), other.exactTypes.toList())
        val exactNotTypes = intersectTypes(exactNotTypes.toList(), other.exactNotTypes.toList())

        val clashes = exactTypes.intersect(exactNotTypes)
        val simplifiedExactTypes = exactTypes - clashes
        val simplifiedExactNotTypes = exactNotTypes - clashes
        if (simplifiedExactTypes.isEmpty() && simplifiedExactNotTypes.isEmpty()) return null
        return FirDataFlowInfo(variable, simplifiedExactTypes, simplifiedExactNotTypes)
    }

    private fun intersectTypes(aTypes: List<ConeKotlinType>, bTypes: List<ConeKotlinType>): Set<ConeKotlinType> {
        if (aTypes.isEmpty() || bTypes.isEmpty()) return emptySet()
        val commonSuperType = with(NewCommonSuperTypeCalculator) {
            with(context as TypeSystemCommonSuperTypesContext) {
                commonSuperType(listOf(intersectTypes(aTypes), intersectTypes(bTypes)))
            }
        } as ConeKotlinType

        return if (commonSuperType is ConeIntersectionType)
            commonSuperType.intersectedTypes.toSet()
        else
            setOf(commonSuperType)
    }

}

infix fun <T, V> Collection<T>.cartesianProduct(other: Collection<V>): List<Pair<T, V>> {
    val result = ArrayList<Pair<T, V>>(this.size * other.size)
    for (x in this) {
        for (y in other) {
            result += x to y
        }
    }
    return result
}