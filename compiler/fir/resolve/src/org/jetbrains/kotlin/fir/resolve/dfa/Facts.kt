/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.resolve.dfa

import com.google.common.collect.HashMultimap
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ConeTypeContext
import org.jetbrains.kotlin.fir.types.ConeTypeIntersector
import org.jetbrains.kotlin.fir.types.render
import org.jetbrains.kotlin.resolve.calls.NewCommonSuperTypeCalculator
import org.jetbrains.kotlin.types.model.TypeSystemCommonSuperTypesContext

// TODO: rename
interface WithLevel {
    val level: Int
}

enum class ConditionValue(val token: String) {
    True("true"), False("false");

    override fun toString(): String {
        return token
    }
}

enum class ConditionOperator(val token: String) {
    Eq("=="), NotEq("!=");

    override fun toString(): String {
        return token
    }
}

data class ConditionRHS(val operator: ConditionOperator, val value: ConditionValue) {
    override fun toString(): String {
        return "$operator $value"
    }
}

class Condition(val variable: DataFlowVariable, val rhs: ConditionRHS) {
    val operator: ConditionOperator get() = rhs.operator
    val value: ConditionValue get() = rhs.value

    constructor(
        variable: DataFlowVariable,
        operator: ConditionOperator,
        value: ConditionValue
    ) : this(variable, ConditionRHS(operator, value))

    override fun toString(): String {
        return "$variable $rhs"
    }
}

data class UnapprovedFirDataFlowInfo(
    val condition: ConditionRHS,
    val variable: DataFlowVariable,
    val info: FirDataFlowInfo
) {
    override fun toString(): String {
        return "$condition -> $variable: ${info.exactType?.render()}, ${info.exactNotType?.render()}"
    }
}

data class FirDataFlowInfo(
    val exactType: ConeKotlinType?,
    val exactNotType: ConeKotlinType?
)

interface DataFlowInferenceContext : ConeTypeContext, TypeSystemCommonSuperTypesContext {
    private fun myCommonSuperType(types: List<ConeKotlinType>): ConeKotlinType? {
        return when (types.size) {
            0 -> null
            1 -> types.first()
            else -> with(NewCommonSuperTypeCalculator) {
                commonSuperType(types) as ConeKotlinType
            }
        }
    }

    private fun myIntersectTypes(types: List<ConeKotlinType>): ConeKotlinType? {
        return when (types.size) {
            0 -> null
            1 -> types.first()
            else -> ConeTypeIntersector.intersectTypes(this, types)
        }
    }

    fun or(infos: Collection<FirDataFlowInfo>): FirDataFlowInfo {
        infos.singleOrNull()?.let { return it }
        val exactType = orTypes(infos.map { it.exactType })
        val exactNotType = orTypes(infos.map { it.exactNotType })
        return FirDataFlowInfo(exactType, exactNotType)
    }

    private fun orTypes(types: Collection<ConeKotlinType?>): ConeKotlinType? {
        if (types.any { it == null }) return null
        @Suppress("UNCHECKED_CAST")
        return myCommonSuperType(types as List<ConeKotlinType>)
    }

    fun and(infos: Collection<FirDataFlowInfo>): FirDataFlowInfo {
        infos.singleOrNull()?.let { return it }
        val exactType = myIntersectTypes(infos.mapNotNull { it.exactType })
        val exactNotType = myIntersectTypes(infos.mapNotNull { it.exactNotType })
        return FirDataFlowInfo(exactType, exactNotType)
    }
}

typealias Level = Int

class DataFlowStatementsStorage(
    val approvedFacts: MutableMap<DataFlowVariable, FirDataFlowInfo> = mutableMapOf(),
    val notApprovedFacts: HashMultimap<DataFlowVariable, UnapprovedFirDataFlowInfo> = HashMultimap.create(),
    state: State = State.Building
) {
    var state: State = state
        private set

    fun freeze() {
        state = State.Frozen
    }

    fun addApprovedFact(variable: DataFlowVariable, info: FirDataFlowInfo): DataFlowStatementsStorage {
        if (state == State.Frozen) return copyForBuilding().addApprovedFact(variable, info)
        approvedFacts.put(variable, info)
        return this
    }

    fun addNotApprovedFact(variable: DataFlowVariable, info: UnapprovedFirDataFlowInfo): DataFlowStatementsStorage {
        if (state == State.Frozen) return copyForBuilding().addNotApprovedFact(variable, info)
        notApprovedFacts.put(variable, info)
        return this
    }

    fun copyNotApprovedFacts(from: DataFlowVariable, to: DataFlowVariable): DataFlowStatementsStorage {
        if (state == State.Frozen) copyForBuilding().copyNotApprovedFacts(from, to)
        val facts = if (from.isSynthetic) {
            notApprovedFacts.removeAll(from)
        } else {
            notApprovedFacts[from]
        }
        notApprovedFacts.putAll(to, facts)
        return this
    }

    fun approvedFacts(variable: DataFlowVariable): FirDataFlowInfo? {
        return approvedFacts[variable]
    }

    fun removeVariable(variable: DataFlowVariable): DataFlowStatementsStorage {
        if (state == State.Frozen) return copyForBuilding().removeVariable(variable)
        notApprovedFacts.removeAll(variable)
        approvedFacts.remove(variable)
        return this
    }

    companion object {
        val EMPTY = DataFlowStatementsStorage(mutableMapOf(), HashMultimap.create(), State.Frozen)
    }

    enum class State {
        Building, Frozen
    }

    fun copy(): DataFlowStatementsStorage {
        return when (state) {
            State.Frozen -> this
            State.Building -> copyForBuilding()
        }
    }

    fun copyForBuilding(): DataFlowStatementsStorage {
        return DataFlowStatementsStorage(approvedFacts.toMutableMap(), notApprovedFacts.copy(), State.Building)
    }
}

private fun <K, V> HashMultimap<K, V>.copy(): HashMultimap<K, V> = HashMultimap.create(this)

class LogicSystem(private val context: DataFlowInferenceContext) {
    private fun <E> List<Set<E>>.intersectSets(): Set<E> = takeIf { isNotEmpty() }?.reduce { x, y -> x.intersect(y) } ?: emptySet()

    fun or(storages: Collection<DataFlowStatementsStorage>): DataFlowStatementsStorage {
        storages.singleOrNull()?.let {
            return when (it.state) {
                DataFlowStatementsStorage.State.Frozen -> it
                DataFlowStatementsStorage.State.Building -> it.copy()
            }
        }
        val approvedFacts = mutableMapOf<DataFlowVariable, FirDataFlowInfo>().apply {
            storages.map { it.approvedFacts.keys }
                .intersectSets()
                .forEach { variable ->
                    val infos = storages.map { it.approvedFacts[variable]!! }
                    if (infos.isNotEmpty()) {
                        this[variable] = context.or(infos)
                    }
                }
        }

        val notApprovedFacts = HashMultimap.create<DataFlowVariable, UnapprovedFirDataFlowInfo>().apply {
            storages.map { it.notApprovedFacts.keySet() }
                .intersectSets()
                .forEach { variable ->
                    val infos = storages.map { it.notApprovedFacts[variable] }.intersectSets()
                    if (infos.isNotEmpty()) {
                        this.putAll(variable, infos)
                    }
                }
        }
        return DataFlowStatementsStorage(approvedFacts, notApprovedFacts)
    }

    fun andForVerifiedFacts(left: Map<DataFlowVariable, FirDataFlowInfo>?, right: Map<DataFlowVariable, FirDataFlowInfo>?): Map<DataFlowVariable, FirDataFlowInfo>? {
        if (left == null) return right
        if (right == null) return left

        val map = mutableMapOf<DataFlowVariable, FirDataFlowInfo>()
        for (variable in left.keys.union(right.keys)) {
            val leftInfo = left[variable]
            val rightInfo = right[variable]
            map[variable] = context.and(listOfNotNull(leftInfo, rightInfo))
        }
        return map
    }

    fun orForVerifiedFacts(left: Map<DataFlowVariable, FirDataFlowInfo>?, right: Map<DataFlowVariable, FirDataFlowInfo>?): Map<DataFlowVariable, FirDataFlowInfo>? {
        if (left == null || right == null) return null
        val map = mutableMapOf<DataFlowVariable, FirDataFlowInfo>()
        for (variable in left.keys.intersect(right.keys)) {
            val leftInfo = left[variable]!!
            val rightInfo = right[variable]!!
            map[variable] = context.or(listOf(leftInfo, rightInfo))
        }
        return map
    }

    fun approveFactsInsideFlow(proof: Condition, flow: DataFlowStatementsStorage): DataFlowStatementsStorage {
        val notApprovedFacts: Set<UnapprovedFirDataFlowInfo> = flow.notApprovedFacts[proof.variable]
        if (notApprovedFacts.isEmpty()) {
            return flow
        }
        @Suppress("NAME_SHADOWING")
        val flow = flow.copyForBuilding()
        val newFacts = HashMultimap.create<DataFlowVariable, FirDataFlowInfo>()
        notApprovedFacts.forEach {
            if (it.condition == proof.rhs) {
                newFacts.put(it.variable, it.info)
            }
        }
        newFacts.asMap().forEach { (variable, infos) ->
            @Suppress("NAME_SHADOWING")
            val infos = ArrayList(infos)
            flow.approvedFacts[variable]?.let {
                infos.add(it)
            }
            flow.approvedFacts[variable] = context.and(infos)
        }
        return flow
    }

    fun approveFact(proof: Condition, flow: DataFlowStatementsStorage): Map<DataFlowVariable, FirDataFlowInfo>? {
        val notApprovedFacts: Set<UnapprovedFirDataFlowInfo> = flow.notApprovedFacts[proof.variable]
        if (notApprovedFacts.isEmpty()) {
            return emptyMap()
        }
        val newFacts = HashMultimap.create<DataFlowVariable, FirDataFlowInfo>()
        notApprovedFacts.forEach {
            if (it.condition == proof.rhs) {
                newFacts.put(it.variable, it.info)
            }
        }
        return newFacts.asMap().mapValues { (_, infos) -> context.and(infos) }
    }
}