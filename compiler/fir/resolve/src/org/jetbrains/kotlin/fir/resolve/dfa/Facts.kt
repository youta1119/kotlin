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

enum class ConditionValue(val token: String) {
    True("true"), False("false"), Null("null");

    override fun toString(): String {
        return token
    }

    fun invert(): ConditionValue? = when (this) {
        True -> False
        False -> True
        else -> null
    }
}

enum class ConditionOperator(val token: String) {
    Eq("=="), NotEq("!=");

    fun invert(): ConditionOperator = when (this) {
        Eq -> NotEq
        NotEq -> Eq
    }

    override fun toString(): String {
        return token
    }
}

data class ConditionRHS(val operator: ConditionOperator, val value: ConditionValue) {
    fun invert(): ConditionRHS {
        val newValue = value.invert()
        return if (newValue != null) {
            ConditionRHS(operator, newValue)
        } else {
            ConditionRHS(operator.invert(), value)
        }
    }

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
    fun invert(): UnapprovedFirDataFlowInfo {
        return UnapprovedFirDataFlowInfo(condition.invert(), variable, info)
    }

    override fun toString(): String {
        return "$condition -> $variable: ${info.exactType.render()}, ${info.exactNotType.render()}"
    }

    private fun Set<ConeKotlinType>.render(): String = joinToString { it.render() }
}

data class FirDataFlowInfo(
    val exactType: Set<ConeKotlinType>,
    val exactNotType: Set<ConeKotlinType>
) {
    operator fun plus(other: FirDataFlowInfo): FirDataFlowInfo = FirDataFlowInfo(
        exactType + other.exactType,
        exactNotType + other.exactNotType
    )

    operator fun minus(other: FirDataFlowInfo): FirDataFlowInfo = FirDataFlowInfo(
        exactType - other.exactType,
        exactNotType - other.exactNotType
    )

    val isNotEmpty: Boolean get() = exactType.isNotEmpty() || exactNotType.isNotEmpty()

    fun invert(): FirDataFlowInfo = FirDataFlowInfo(exactNotType, exactType)
}

operator fun FirDataFlowInfo.plus(other: FirDataFlowInfo?): FirDataFlowInfo = other?.let { this + other } ?: this

interface DataFlowInferenceContext : ConeTypeContext, TypeSystemCommonSuperTypesContext {
    fun myCommonSuperType(types: List<ConeKotlinType>): ConeKotlinType? {
        return when (types.size) {
            0 -> null
            1 -> types.first()
            else -> with(NewCommonSuperTypeCalculator) {
                commonSuperType(types) as ConeKotlinType
            }
        }
    }

    fun myIntersectTypes(types: List<ConeKotlinType>): ConeKotlinType? {
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

    private fun orTypes(types: Collection<Set<ConeKotlinType>>): Set<ConeKotlinType> {
        if (types.any { it.isEmpty() }) return emptySet()
        val allTypes = types.flatMapTo(mutableSetOf()) { it }
        val commonTypes = allTypes.toMutableSet()
        types.forEach { commonTypes.retainAll(it) }
        val differentTypes = allTypes - commonTypes
        myCommonSuperType(differentTypes.toList())?.let { commonTypes += it }
        return commonTypes
    }

    fun and(infos: Collection<FirDataFlowInfo>): FirDataFlowInfo {
        infos.singleOrNull()?.let { return it }
        val exactType = infos.flatMapTo(mutableSetOf()) { it.exactType }
        val exactNotType = infos.flatMapTo(mutableSetOf()) { it.exactNotType }
        return FirDataFlowInfo(exactType, exactNotType)
    }
}

class Flow(
    val approvedFacts: MutableMap<DataFlowVariable, FirDataFlowInfo> = mutableMapOf(),
    val notApprovedFacts: HashMultimap<DataFlowVariable, UnapprovedFirDataFlowInfo> = HashMultimap.create(),
    private var state: State = State.Building
) {
    private val isFrozen: Boolean get() = state == State.Frozen

    fun freeze() {
        state = State.Frozen
    }

    fun addApprovedFact(variable: DataFlowVariable, info: FirDataFlowInfo): Flow {
        if (isFrozen) return copyForBuilding().addApprovedFact(variable, info)
        approvedFacts.compute(variable) { _, existingInfo ->
            if (existingInfo == null) info
            else existingInfo + info
        }
        return this
    }

    fun addNotApprovedFact(variable: DataFlowVariable, info: UnapprovedFirDataFlowInfo): Flow {
        if (isFrozen) return copyForBuilding().addNotApprovedFact(variable, info)
        notApprovedFacts.put(variable, info)
        return this
    }

    fun copyNotApprovedFacts(
        from: DataFlowVariable,
        to: DataFlowVariable,
        transform: ((UnapprovedFirDataFlowInfo) -> UnapprovedFirDataFlowInfo)? = null
    ): Flow {
        if (isFrozen) copyForBuilding().copyNotApprovedFacts(from, to)
        var facts = if (from.isSynthetic) {
            notApprovedFacts.removeAll(from)
        } else {
            notApprovedFacts[from]
        }
        if (transform != null) {
            facts = facts.mapTo(mutableSetOf(), transform)
        }
        notApprovedFacts.putAll(to, facts)
        return this
    }

    fun approvedFacts(variable: DataFlowVariable): FirDataFlowInfo? {
        return approvedFacts[variable]
    }

    fun removeVariableFromFlow(variable: DataFlowVariable): Flow {
        if (isFrozen) return copyForBuilding().removeVariableFromFlow(variable)
        notApprovedFacts.removeAll(variable)
        approvedFacts.remove(variable)
        return this
    }

    companion object {
        val EMPTY = Flow(mutableMapOf(), HashMultimap.create(), State.Frozen)
    }

    enum class State {
        Building, Frozen
    }

    fun copy(): Flow {
        return when (state) {
            State.Frozen -> this
            State.Building -> copyForBuilding()
        }
    }

    fun copyForBuilding(): Flow {
        return Flow(approvedFacts.toMutableMap(), notApprovedFacts.copy(), State.Building)
    }
}

private fun <K, V> HashMultimap<K, V>.copy(): HashMultimap<K, V> = HashMultimap.create(this)

class LogicSystem(private val context: DataFlowInferenceContext) {
    private fun <E> List<Set<E>>.intersectSets(): Set<E> = takeIf { isNotEmpty() }?.reduce { x, y -> x.intersect(y) } ?: emptySet()

    fun or(storages: Collection<Flow>): Flow {
        storages.singleOrNull()?.let {
            return it.copy()
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
        return Flow(approvedFacts, notApprovedFacts)
    }

    fun andForVerifiedFacts(left: Map<DataFlowVariable, FirDataFlowInfo>?, right: Map<DataFlowVariable, FirDataFlowInfo>?): Map<DataFlowVariable, FirDataFlowInfo>? {
        if (left.isNullOrEmpty()) return right
        if (right.isNullOrEmpty()) return left

        val map = mutableMapOf<DataFlowVariable, FirDataFlowInfo>()
        for (variable in left.keys.union(right.keys)) {
            val leftInfo = left[variable]
            val rightInfo = right[variable]
            map[variable] = context.and(listOfNotNull(leftInfo, rightInfo))
        }
        return map
    }

    fun orForVerifiedFacts(left: Map<DataFlowVariable, FirDataFlowInfo>?, right: Map<DataFlowVariable, FirDataFlowInfo>?): Map<DataFlowVariable, FirDataFlowInfo>? {
        if (left.isNullOrEmpty() || right.isNullOrEmpty()) return null
        val map = mutableMapOf<DataFlowVariable, FirDataFlowInfo>()
        for (variable in left.keys.intersect(right.keys)) {
            val leftInfo = left[variable]!!
            val rightInfo = right[variable]!!
            map[variable] = context.or(listOf(leftInfo, rightInfo))
        }
        return map
    }

    fun approveFactsInsideFlow(proof: Condition, flow: Flow): Flow {
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

    fun approveFact(proof: Condition, flow: Flow): MutableMap<DataFlowVariable, FirDataFlowInfo>? {
        val notApprovedFacts: Set<UnapprovedFirDataFlowInfo> = flow.notApprovedFacts[proof.variable]
        if (notApprovedFacts.isEmpty()) {
            return mutableMapOf()
        }
        val newFacts = HashMultimap.create<DataFlowVariable, FirDataFlowInfo>()
        notApprovedFacts.forEach {
            if (it.condition == proof.rhs) {
                newFacts.put(it.variable, it.info)
            }
        }
        return newFacts.asMap().mapValuesTo(mutableMapOf()) { (_, infos) -> context.and(infos) }
    }
}