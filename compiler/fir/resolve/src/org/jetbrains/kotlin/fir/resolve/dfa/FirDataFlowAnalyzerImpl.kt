/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.resolve.dfa

import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirResolvedCallableReference
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.resolve.BodyResolveComponents
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.CFGNode
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.ControlFlowGraph
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.ControlFlowGraphBuilder
import org.jetbrains.kotlin.fir.resolve.transformers.FirBodyResolveTransformer
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ConeTypeContext
import org.jetbrains.kotlin.fir.types.ConeTypeIntersector
import org.jetbrains.kotlin.fir.types.coneTypeSafe

class FirDataFlowAnalyzerImpl(transformer: FirBodyResolveTransformer) : FirDataFlowAnalyzer(), BodyResolveComponents by transformer {
    private val context: ConeTypeContext get() = inferenceComponents.ctx as ConeTypeContext
    private val factSystem: FactSystem = FactSystem(context)

    private val graphBuilder = ControlFlowGraphBuilder()

    private val variableStorage = DataFlowVariableStorage()
    private val edges = mutableMapOf<CFGNode<*>, Facts>().withDefault { Facts.EMPTY }
    private val outputEdges = mutableMapOf<CFGNode<*>, Facts>()

    private val conditionVariables: Stack<DataFlowVariable> =
        stackOf()

    override fun getTypeUsingSmartcastInfo(qualifiedAccessExpression: FirQualifiedAccessExpression): ConeKotlinType? {
        val lastNode = graphBuilder.lastNode
        val fir = (((qualifiedAccessExpression.calleeReference as? FirResolvedCallableReference)?.coneSymbol) as? FirBasedSymbol<*>)?.fir
            ?: return null
        val types = factSystem.squashExactTypes(lastNode.facts[fir]).takeIf { it.isNotEmpty() } ?: return null
        val originalType = qualifiedAccessExpression.typeRef.coneTypeSafe<ConeKotlinType>() ?: return null
        return ConeTypeIntersector.intersectTypesFromSmartcasts(context, originalType, types)
    }

    private operator fun Facts.get(fir: FirElement): Collection<FirDataFlowInfo> =
        variableStorage[fir]?.let { this[it] } ?: emptyList()

    private operator fun Facts.get(variable: DataFlowVariable): Collection<FirDataFlowInfo> =
        verifiedInfos.map { it.dataFlowInfo }.filter { it.variable == variable }

    // ----------------------------------- Named function -----------------------------------

    override fun enterNamedFunction(namedFunction: FirNamedFunction) {
        variableStorage.reset()
        graphBuilder.enterNamedFunction(namedFunction)

        for (valueParameter in namedFunction.valueParameters) {
            variableStorage.createNewRealVariable(valueParameter)
        }
    }

    override fun exitNamedFunction(namedFunction: FirNamedFunction): ControlFlowGraph {
        return graphBuilder.exitNamedFunction(namedFunction)
    }

    // ----------------------------------- Block -----------------------------------

    override fun enterBlock(block: FirBlock) {
        graphBuilder.enterBlock(block)
    }

    override fun exitBlock(block: FirBlock) {
        graphBuilder.exitBlock(block)
    }

    // ----------------------------------- Type operator call -----------------------------------

    override fun exitTypeOperatorCall(typeOperatorCall: FirTypeOperatorCall) {
        graphBuilder.exitTypeOperatorCall(typeOperatorCall)
    }

    // ----------------------------------- Jump -----------------------------------

    override fun exitJump(jump: FirJump<*>) {
        graphBuilder.exitJump(jump)
    }

    // ----------------------------------- When -----------------------------------

    override fun enterWhenExpression(whenExpression: FirWhenExpression) {
        graphBuilder.enterWhenExpression(whenExpression)
    }

    override fun enterWhenBranchCondition(whenBranch: FirWhenBranch) {
        graphBuilder.enterWhenBranchCondition(whenBranch)
        conditionVariables.push(variableStorage.createNewSyntheticVariable(whenBranch.condition))
    }

    override fun exitWhenBranchCondition(whenBranch: FirWhenBranch) {
        val node = graphBuilder.exitWhenBranchCondition(whenBranch)

        val conditionVariable = conditionVariables.pop()
        val trueCondition = BooleanCondition(conditionVariable, true)
        node.condition = trueCondition
    }

    override fun exitWhenBranchResult(whenBranch: FirWhenBranch) {
        graphBuilder.exitWhenBranchResult(whenBranch)
    }

    override fun exitWhenExpression(whenExpression: FirWhenExpression) {
        val whenExitNode = graphBuilder.exitWhenExpression(whenExpression)
        intersectFactsFromPreviousNodes(whenExitNode)
    }

    // ----------------------------------- While Loop -----------------------------------

    override fun enterWhileLoop(loop: FirLoop) {
        graphBuilder.enterWhileLoop(loop)
    }

    override fun exitWhileLoopCondition(loop: FirLoop) {
        graphBuilder.exitWhileLoopCondition(loop)
    }

    override fun exitWhileLoop(loop: FirLoop) {
        graphBuilder.exitWhileLoop(loop)
    }

    // ----------------------------------- Do while Loop -----------------------------------

    override fun enterDoWhileLoop(loop: FirLoop) {
        graphBuilder.enterDoWhileLoop(loop)
    }

    override fun enterDoWhileLoopCondition(loop: FirLoop) {
        graphBuilder.enterDoWhileLoopCondition(loop)
    }

    override fun exitDoWhileLoop(loop: FirLoop) {
        graphBuilder.exitDoWhileLoop(loop)
    }

    // ----------------------------------- Try-catch-finally -----------------------------------

    override fun enterTryExpression(tryExpression: FirTryExpression) {
        graphBuilder.enterTryExpression(tryExpression)
    }

    override fun exitTryMainBlock(tryExpression: FirTryExpression) {
        graphBuilder.exitTryMainBlock(tryExpression)
    }

    override fun enterCatchClause(catch: FirCatch) {
        graphBuilder.enterCatchClause(catch)
    }

    override fun exitCatchClause(catch: FirCatch) {
        graphBuilder.exitCatchClause(catch)
    }

    override fun enterFinallyBlock(tryExpression: FirTryExpression) {
        graphBuilder.enterFinallyBlock(tryExpression)
    }

    override fun exitFinallyBlock(tryExpression: FirTryExpression) {
        graphBuilder.exitFinallyBlock(tryExpression)
    }

    override fun exitTryExpression(tryExpression: FirTryExpression) {
        graphBuilder.exitTryExpression(tryExpression)
    }

    // ----------------------------------- Resolvable call -----------------------------------

    override fun exitQualifiedAccessExpression(qualifiedAccessExpression: FirQualifiedAccessExpression) {
        graphBuilder.exitQualifiedAccessExpression(qualifiedAccessExpression)
    }

    override fun enterFunctionCall(functionCall: FirFunctionCall) {
        // TODO: add processing in-place lambdas
    }

    override fun exitFunctionCall(functionCall: FirFunctionCall) {
        graphBuilder.exitFunctionCall(functionCall)
    }

    override fun exitConstExpresion(constExpression: FirConstExpression<*>) {
        graphBuilder.exitConstExpresion(constExpression)
    }

    override fun exitVariableDeclaration(variable: FirVariable<*>) {
        graphBuilder.exitVariableDeclaration(variable)
    }

    override fun exitVariableAssignment(assignment: FirVariableAssignment) {
        graphBuilder.exitVariableAssignment(assignment)
    }

    override fun exitThrowExceptionNode(throwExpression: FirThrowExpression) {
        graphBuilder.exitThrowExceptionNode(throwExpression)
    }

    // -------------------------------------------------------------------------------------------------------------------------

    private fun intersectFactsFromPreviousNodes(node: CFGNode<*>) {
        node.facts = factSystem.foldFacts(node.previousNodes.map { outputEdges[it] ?: it.facts })
    }

    private var CFGNode<*>.facts: Facts
        get() = edges.getValue(this)
        set(value) {
            edges[this] = value
        }

    private val CFGNode<*>.verifiedInfos: Collection<VerifiedInfo>
        get() = facts.verifiedInfos

    private val CFGNode<*>.previousFacts: List<Facts> get() = previousNodes.map { it.facts }
}