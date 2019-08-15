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
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.*
import org.jetbrains.kotlin.fir.resolve.transformers.FirBodyResolveTransformer
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import javax.xml.crypto.Data

class FirDataFlowAnalyzerImpl(transformer: FirBodyResolveTransformer) : FirDataFlowAnalyzer(), BodyResolveComponents by transformer {
    private val context: DataFlowInferenceContext get() = inferenceComponents.ctx as DataFlowInferenceContext

    private val graphBuilder = ControlFlowGraphBuilder()
    private val logicSystem = LogicSystem(context)
    private val variableStorage = DataFlowVariableStorage()
    private val edges = mutableMapOf<CFGNode<*>, DataFlowStatementsStorage>().withDefault { DataFlowStatementsStorage.EMPTY }

    override fun getTypeUsingSmartcastInfo(qualifiedAccessExpression: FirQualifiedAccessExpression): ConeKotlinType? {
        val symbol: FirBasedSymbol<*> = (qualifiedAccessExpression.calleeReference as? FirResolvedCallableReference)?.coneSymbol as? FirBasedSymbol<*> ?: return null
        val variable = variableStorage[symbol] ?: return null
        val smartCastType = graphBuilder.lastNode.flow.approvedFacts(variable)?.exactType ?: return null
        val originalType = qualifiedAccessExpression.typeRef.coneTypeSafe<ConeKotlinType>() ?: return null
        return ConeTypeIntersector.intersectTypesFromSmartcasts(context, originalType, smartCastType)
    }

    // ----------------------------------- Named function -----------------------------------

    override fun enterNamedFunction(namedFunction: FirNamedFunction) {
        variableStorage.reset()
        graphBuilder.enterNamedFunction(namedFunction).also {
            it.flow = DataFlowStatementsStorage()
        }

        for (valueParameter in namedFunction.valueParameters) {
            getRealVariable(valueParameter.symbol)
        }
    }

    override fun exitNamedFunction(namedFunction: FirNamedFunction): ControlFlowGraph {
        val graph = graphBuilder.exitNamedFunction(namedFunction)
        return graph
    }

    // ----------------------------------- Block -----------------------------------

    override fun enterBlock(block: FirBlock) {
        val node = graphBuilder.enterBlock(block).also { passFlow(it, false) }

        val previousNode = node.usefulPreviousNodes.singleOrNull() as? WhenBranchConditionExitNode
        if (previousNode != null) {
            node.flow = logicSystem.approveFact(previousNode.trueCondition, node.flow)
        }
        node.flow.freeze()
    }

    override fun exitBlock(block: FirBlock) {
        graphBuilder.exitBlock(block).also(this::passFlow)
    }

    // ----------------------------------- Type operator call -----------------------------------

    override fun exitTypeOperatorCall(typeOperatorCall: FirTypeOperatorCall) {
        val node = graphBuilder.exitTypeOperatorCall(typeOperatorCall).also { passFlow(it, false) }

        try {
            if (typeOperatorCall.operation == FirOperation.IS) {
                val symbol: FirCallableSymbol<*> = typeOperatorCall.argument.toResolvedCallableSymbol() as? FirCallableSymbol<*> ?: return
                val type = typeOperatorCall.conversionTypeRef.coneTypeSafe<ConeKotlinType>() ?: return

                val expressionVariable = getSyntheticVariable(typeOperatorCall)
                val varVariable = getRealVariable(symbol)

                var flow = node.flow
                flow = flow.addNotApprovedFact(
                    expressionVariable,
                    UnapprovedFirDataFlowInfo(
                        ConditionRHS(ConditionOperator.Eq, ConditionValue.True), varVariable, FirDataFlowInfo(type, null)
                    )
                )
                flow = flow.addNotApprovedFact(
                    expressionVariable,
                    UnapprovedFirDataFlowInfo(
                        ConditionRHS(ConditionOperator.Eq, ConditionValue.False), varVariable, FirDataFlowInfo(null, type)
                    )
                )
                node.flow = flow
            }

            if (typeOperatorCall.operation == FirOperation.NOT_IS) {
                val symbol: FirCallableSymbol<*> = typeOperatorCall.argument.toResolvedCallableSymbol() as? FirCallableSymbol<*> ?: return
                val type = typeOperatorCall.conversionTypeRef.coneTypeSafe<ConeKotlinType>() ?: return

                val expressionVariable = getSyntheticVariable(typeOperatorCall)
                val varVariable = getRealVariable(symbol)

                var flow = node.flow
                flow = flow.addNotApprovedFact(
                    expressionVariable,
                    UnapprovedFirDataFlowInfo(
                        ConditionRHS(ConditionOperator.Eq, ConditionValue.True), varVariable, FirDataFlowInfo(null, type)
                    )
                )
                flow = flow.addNotApprovedFact(
                    expressionVariable,
                    UnapprovedFirDataFlowInfo(
                        ConditionRHS(ConditionOperator.Eq, ConditionValue.False), varVariable, FirDataFlowInfo(type, null)
                    )
                )
                node.flow = flow
            }

        } finally {
            node.flow.freeze()
        }
    }

    // ----------------------------------- Jump -----------------------------------

    override fun exitJump(jump: FirJump<*>) {
        graphBuilder.exitJump(jump).also(this::passFlow)
    }

    // ----------------------------------- When -----------------------------------

    override fun enterWhenExpression(whenExpression: FirWhenExpression) {
        graphBuilder.enterWhenExpression(whenExpression).also(this::passFlow)
    }

    override fun enterWhenBranchCondition(whenBranch: FirWhenBranch) {
        val node = graphBuilder.enterWhenBranchCondition(whenBranch).also { passFlow(it, false) }
        val previousNode = node.previousNodes.single()
        if (previousNode is WhenBranchConditionExitNode) {
            node.flow = logicSystem.approveFact(previousNode.falseCondition, node.flow)
        }
        node.flow.freeze()
    }

    override fun exitWhenBranchCondition(whenBranch: FirWhenBranch) {
        val node = graphBuilder.exitWhenBranchCondition(whenBranch).also(this::passFlow)

        val conditionVariable = getVariable(whenBranch.condition)
        node.trueCondition = Condition(conditionVariable, ConditionOperator.Eq, ConditionValue.True)
        node.falseCondition = Condition(conditionVariable, ConditionOperator.Eq, ConditionValue.False)
    }

    override fun exitWhenBranchResult(whenBranch: FirWhenBranch) {
        val node = graphBuilder.exitWhenBranchResult(whenBranch).also { passFlow(it, false) }
        node.flow = removeVariable(getVariable(whenBranch.condition), node.flow)
    }

    override fun exitWhenExpression(whenExpression: FirWhenExpression) {
        val node = graphBuilder.exitWhenExpression(whenExpression)
        val flow = logicSystem.or(node.usefulPreviousNodes.map { it.flow })
        node.flow = flow
    }

    // ----------------------------------- While Loop -----------------------------------

    override fun enterWhileLoop(loop: FirLoop) {
        graphBuilder.enterWhileLoop(loop).also(this::passFlow)
    }

    override fun exitWhileLoopCondition(loop: FirLoop) {
        graphBuilder.exitWhileLoopCondition(loop).also(this::passFlow)
    }

    override fun exitWhileLoop(loop: FirLoop) {
        graphBuilder.exitWhileLoop(loop).also(this::passFlow)
    }

    // ----------------------------------- Do while Loop -----------------------------------

    override fun enterDoWhileLoop(loop: FirLoop) {
        graphBuilder.enterDoWhileLoop(loop).also(this::passFlow)
    }

    override fun enterDoWhileLoopCondition(loop: FirLoop) {
        val (loopBlockExitNode, loopConditionEnterNode) = graphBuilder.enterDoWhileLoopCondition(loop)
        passFlow(loopBlockExitNode)
        passFlow(loopConditionEnterNode)
    }

    override fun exitDoWhileLoop(loop: FirLoop) {
        graphBuilder.exitDoWhileLoop(loop).also(this::passFlow)
    }

    // ----------------------------------- Try-catch-finally -----------------------------------

    override fun enterTryExpression(tryExpression: FirTryExpression) {
        graphBuilder.enterTryExpression(tryExpression).also(this::passFlow)
    }

    override fun exitTryMainBlock(tryExpression: FirTryExpression) {
        graphBuilder.exitTryMainBlock(tryExpression).also(this::passFlow)
    }

    override fun enterCatchClause(catch: FirCatch) {
        graphBuilder.enterCatchClause(catch).also(this::passFlow)
    }

    override fun exitCatchClause(catch: FirCatch) {
        graphBuilder.exitCatchClause(catch).also(this::passFlow)
    }

    override fun enterFinallyBlock(tryExpression: FirTryExpression) {
        // TODO
        graphBuilder.enterFinallyBlock(tryExpression)
    }

    override fun exitFinallyBlock(tryExpression: FirTryExpression) {
        // TODO
        graphBuilder.exitFinallyBlock(tryExpression)
    }

    override fun exitTryExpression(tryExpression: FirTryExpression) {
        // TODO
        graphBuilder.exitTryExpression(tryExpression)
    }

    // ----------------------------------- Resolvable call -----------------------------------

    override fun exitQualifiedAccessExpression(qualifiedAccessExpression: FirQualifiedAccessExpression) {
        graphBuilder.exitQualifiedAccessExpression(qualifiedAccessExpression).also(this::passFlow)
    }

    override fun enterFunctionCall(functionCall: FirFunctionCall) {
        // TODO: add processing in-place lambdas
    }

    override fun exitFunctionCall(functionCall: FirFunctionCall) {
        graphBuilder.exitFunctionCall(functionCall).also(this::passFlow)
    }

    override fun exitConstExpresion(constExpression: FirConstExpression<*>) {
        graphBuilder.exitConstExpresion(constExpression).also(this::passFlow)
    }

    override fun exitVariableDeclaration(variable: FirVariable<*>) {
        val node = graphBuilder.exitVariableDeclaration(variable).also { passFlow(it, false) }
        try {
            val initializerVariable = variableStorage[variable.initializer ?: return] ?: return
            val realVariable = getRealVariable(variable.symbol)
            node.flow = node.flow.copyNotApprovedFacts(initializerVariable, realVariable)
        } finally {
            node.flow.freeze()
        }
    }

    override fun exitVariableAssignment(assignment: FirVariableAssignment) {
        graphBuilder.exitVariableAssignment(assignment).also { passFlow(it) }
    }

    override fun exitThrowExceptionNode(throwExpression: FirThrowExpression) {
        graphBuilder.exitThrowExceptionNode(throwExpression).also(this::passFlow)
    }

    // ----------------------------------- Boolean operators -----------------------------------

    override fun enterBinaryAnd(binaryLogicExpression: FirBinaryLogicExpression) {
        graphBuilder.enterBinaryAnd(binaryLogicExpression)
    }

    override fun exitLeftBinaryAndArgument(binaryLogicExpression: FirBinaryLogicExpression) {
        graphBuilder.exitLeftBinaryAndArgument(binaryLogicExpression)
    }

    override fun exitBinaryAnd(binaryLogicExpression: FirBinaryLogicExpression) {
        graphBuilder.exitBinaryAnd(binaryLogicExpression)
    }

    override fun enterBinaryOr(binaryLogicExpression: FirBinaryLogicExpression) {
        graphBuilder.enterBinaryOr(binaryLogicExpression)
    }

    override fun exitLeftBinaryOrArgument(binaryLogicExpression: FirBinaryLogicExpression) {
        graphBuilder.exitLeftBinaryOrArgument(binaryLogicExpression)
    }

    override fun exitBinaryOr(binaryLogicExpression: FirBinaryLogicExpression) {
        graphBuilder.exitBinaryOr(binaryLogicExpression)
    }

    // -------------------------------------------------------------------------------------------------------------------------

    private var CFGNode<*>.flow: DataFlowStatementsStorage
        get() = edges.getValue(this)
        set(value) {
            edges[this] = value
        }

    private fun passFlow(node: CFGNode<*>, shouldFreeze: Boolean = true) {
        node.flow = logicSystem.or(node.usefulPreviousNodes.map { it.flow }).also {
            if (shouldFreeze) it.freeze()
        }
    }

    private fun getSyntheticVariable(fir: FirElement): DataFlowVariable = variableStorage.getOrCreateNewSyntheticVariable(fir)
    private fun getRealVariable(symbol: FirBasedSymbol<*>): DataFlowVariable = variableStorage.getOrCreateNewRealVariable(symbol)

    private fun getVariable(fir: FirElement): DataFlowVariable {
        val symbol = fir.safeAs<FirQualifiedAccessExpression>()
            ?.calleeReference?.safeAs<FirResolvedCallableReference>()
            ?.coneSymbol as? FirBasedSymbol<*>
        return if (symbol == null)
            getSyntheticVariable(fir)
        else
            getRealVariable(symbol)
    }

    private fun removeVariable(variable: DataFlowVariable, flow: DataFlowStatementsStorage): DataFlowStatementsStorage {
        variableStorage.removeVariable(variable)
        return flow.removeVariable(variable)
    }
}