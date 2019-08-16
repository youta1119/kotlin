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
import org.jetbrains.kotlin.fir.resolve.dfa.ConditionValue.*
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.*
import org.jetbrains.kotlin.fir.resolve.transformers.FirBodyResolveTransformer
import org.jetbrains.kotlin.fir.symbols.CallableId
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class FirDataFlowAnalyzerImpl(transformer: FirBodyResolveTransformer) : FirDataFlowAnalyzer(), BodyResolveComponents by transformer {
    companion object {
        private val KOTLIN_BOOLEAN_NOT = CallableId(FqName("kotlin"), FqName("Boolean"), Name.identifier("not"))
    }

    private val context: DataFlowInferenceContext get() = inferenceComponents.ctx as DataFlowInferenceContext

    private val graphBuilder = ControlFlowGraphBuilder()
    private val logicSystem = LogicSystem(context)
    private val variableStorage = DataFlowVariableStorage()
    private val edges = mutableMapOf<CFGNode<*>, Flow>().withDefault { Flow.EMPTY }

    override fun getTypeUsingSmartcastInfo(qualifiedAccessExpression: FirQualifiedAccessExpression): ConeKotlinType? {
        val symbol: FirBasedSymbol<*> = (qualifiedAccessExpression.calleeReference as? FirResolvedCallableReference)?.coneSymbol as? FirBasedSymbol<*> ?: return null
        val variable = variableStorage[symbol] ?: return null
        val smartCastTypes  = graphBuilder.lastNode.flow.approvedFacts(variable)?.exactType ?: return null
        val smartCastType = context.myIntersectTypes(smartCastTypes.toList()) ?: return null
        val originalType = qualifiedAccessExpression.typeRef.coneTypeSafe<ConeKotlinType>() ?: return null
        return ConeTypeIntersector.intersectTypesFromSmartcasts(context, originalType, smartCastType)
    }

    // ----------------------------------- Named function -----------------------------------

    override fun enterNamedFunction(namedFunction: FirNamedFunction) {
        variableStorage.reset()
        graphBuilder.enterNamedFunction(namedFunction).also {
            it.flow = Flow()
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
            node.flow = logicSystem.approveFactsInsideFlow(previousNode.trueCondition, node.flow)
        }
        node.flow.freeze()
    }

    override fun exitBlock(block: FirBlock) {
        graphBuilder.exitBlock(block).also(this::passFlow)
    }

    // ----------------------------------- Operator call -----------------------------------

    override fun exitTypeOperatorCall(typeOperatorCall: FirTypeOperatorCall) {
        val node = graphBuilder.exitTypeOperatorCall(typeOperatorCall).also { passFlow(it, false) }
        try {
            if (typeOperatorCall.operation !in FirOperation.TYPES) return
            val symbol: FirCallableSymbol<*> = typeOperatorCall.argument.toResolvedCallableSymbol() as? FirCallableSymbol<*> ?: return
            val type = typeOperatorCall.conversionTypeRef.coneTypeSafe<ConeKotlinType>() ?: return
            val varVariable = getRealVariable(symbol)

            var flow = node.flow
            when (typeOperatorCall.operation) {
                FirOperation.IS, FirOperation.NOT_IS -> {
                    val expressionVariable = getSyntheticVariable(typeOperatorCall)

                    val trueInfo = FirDataFlowInfo(setOf(type), emptySet())
                    val falseInfo = FirDataFlowInfo(emptySet(), setOf(type))

                    fun chooseInfo(trueBranch: Boolean) = if ((typeOperatorCall.operation == FirOperation.IS) == trueBranch) trueInfo else falseInfo

                    flow = flow.addNotApprovedFact(
                        expressionVariable,
                        UnapprovedFirDataFlowInfo(
                            ConditionRHS(ConditionOperator.Eq, True), varVariable, chooseInfo(true)
                        )
                    )

                    flow = flow.addNotApprovedFact(
                        expressionVariable,
                        UnapprovedFirDataFlowInfo(
                            ConditionRHS(ConditionOperator.Eq, False), varVariable, chooseInfo(false)
                        )
                    )
                }

                FirOperation.AS -> {
                    flow = flow.addApprovedFact(varVariable, FirDataFlowInfo(setOf(type), emptySet()))
                }

                FirOperation.SAFE_AS -> {
                    // TODO
                }

                else -> throw IllegalStateException()
            }

            node.flow = flow
        } finally {
            node.flow.freeze()
        }
    }

    override fun exitOperatorCall(operatorCall: FirOperatorCall) {
        val node = graphBuilder.exitOperatorCall(operatorCall).also { passFlow(it, false) }
        try {
            when (operatorCall.operation) {
                FirOperation.EQ, FirOperation.NOT_EQ -> {

                }
                else -> {}
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
            node.flow = logicSystem.approveFactsInsideFlow(previousNode.falseCondition, node.flow)
        }
        node.flow.freeze()
    }

    override fun exitWhenBranchCondition(whenBranch: FirWhenBranch) {
        val node = graphBuilder.exitWhenBranchCondition(whenBranch).also(this::passFlow)

        val conditionVariable = getVariable(whenBranch.condition)
        node.trueCondition = Condition(conditionVariable, ConditionOperator.Eq, True)
        node.falseCondition = Condition(conditionVariable, ConditionOperator.Eq, False)
    }

    override fun exitWhenBranchResult(whenBranch: FirWhenBranch) {
        val node = graphBuilder.exitWhenBranchResult(whenBranch).also { passFlow(it, false) }
        val conditionVariable = getVariable(whenBranch.condition)
        node.flow = node.flow.removeSyntheticVariable(conditionVariable)
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
        val node = graphBuilder.exitFunctionCall(functionCall).also { passFlow(it, false) }
        if (functionCall.isBooleanNot()) {
            exitBooleanNot(functionCall, node)
            return
        }
    }

    private fun FirFunctionCall.isBooleanNot(): Boolean {
        val symbol = calleeReference.safeAs<FirResolvedCallableReference>()?.coneSymbol as? FirNamedFunctionSymbol ?: return false
        return symbol.callableId == KOTLIN_BOOLEAN_NOT
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
        graphBuilder.enterBinaryAnd(binaryLogicExpression).also(this::passFlow)
    }

    override fun exitLeftBinaryAndArgument(binaryLogicExpression: FirBinaryLogicExpression) {
        graphBuilder.exitLeftBinaryAndArgument(binaryLogicExpression)
    }

    override fun exitBinaryAnd(binaryLogicExpression: FirBinaryLogicExpression) {
        val node = graphBuilder.exitBinaryAnd(binaryLogicExpression).also { passFlow(it, false) }
        val (leftVariable, rightVariable) = binaryLogicExpression.getVariables()

        val flowFromLeft = node.leftOperandNode.flow
        val flowFromRight = node.rightOperandNode.flow
        val flow = node.flow

        val andVariable = getVariable(binaryLogicExpression)

        val leftIsTrue = approveFact(leftVariable, True, flowFromRight)
        val leftIsFalse = approveFact(leftVariable, False, flowFromRight)
        val rightIsTrue = approveFact(rightVariable, True, flowFromRight) ?: mutableMapOf()
        val rightIsFalse = approveFact(rightVariable, False, flowFromRight)

        flowFromRight.approvedFacts.forEach { (variable, info) ->
            val actualInfo = flowFromLeft.approvedFacts[variable]?.let { info - it } ?: info
            if (actualInfo.isNotEmpty) rightIsTrue.compute(variable) { _, existingInfo -> info + existingInfo}
        }

        logicSystem.andForVerifiedFacts(leftIsTrue, rightIsTrue)?.let {
            for ((variable, info) in it) {
                flow.addNotApprovedFact(andVariable, UnapprovedFirDataFlowInfo(ConditionRHS(ConditionOperator.Eq, True), variable, info))
            }
        }
        logicSystem.orForVerifiedFacts(leftIsFalse, rightIsFalse)?.let {
            for ((variable, info) in it) {
                flow.addNotApprovedFact(andVariable, UnapprovedFirDataFlowInfo(ConditionRHS(ConditionOperator.Eq, False), variable, info))
            }
        }
        node.flow = flow.removeSyntheticVariable(leftVariable).removeSyntheticVariable(rightVariable).also { it.freeze() }
    }

    override fun enterBinaryOr(binaryLogicExpression: FirBinaryLogicExpression) {
        graphBuilder.enterBinaryOr(binaryLogicExpression)
    }

    override fun exitLeftBinaryOrArgument(binaryLogicExpression: FirBinaryLogicExpression) {
        val node = graphBuilder.exitLeftBinaryOrArgument(binaryLogicExpression).also { passFlow(it, false) }
        val leftOperandVariable = getVariable(node.previousNodes.first().fir)
        node.flow = logicSystem.approveFactsInsideFlow(Condition(leftOperandVariable, ConditionOperator.Eq, False), node.flow).also { it.freeze() }
    }

    override fun exitBinaryOr(binaryLogicExpression: FirBinaryLogicExpression) {
        val node = graphBuilder.exitBinaryOr(binaryLogicExpression).also { passFlow(it, false) }
        val (leftVariable, rightVariable) = binaryLogicExpression.getVariables()

        val flowFromLeft = node.leftOperandNode.flow
        val flowFromRight = node.rightOperandNode.flow
        val flow = node.flow

        val orVariable = getVariable(binaryLogicExpression)

        val leftIsTrue = approveFact(leftVariable, True, flowFromLeft)
        val leftIsFalse = approveFact(leftVariable, False, flowFromLeft)
        val rightIsTrue = approveFact(rightVariable, True, flowFromRight)
        val rightIsFalse = approveFact(rightVariable, False, flowFromRight)

        logicSystem.orForVerifiedFacts(leftIsTrue, rightIsTrue)?.let {
            for ((variable, info) in it) {
                flow.addNotApprovedFact(orVariable, UnapprovedFirDataFlowInfo(ConditionRHS(ConditionOperator.Eq, True), variable, info))
            }
        }
        logicSystem.andForVerifiedFacts(leftIsFalse, rightIsFalse)?.let {
            for ((variable, info) in it) {
                flow.addNotApprovedFact(orVariable, UnapprovedFirDataFlowInfo(ConditionRHS(ConditionOperator.Eq, False), variable, info))
            }
        }
        node.flow = flow.removeSyntheticVariable(leftVariable).removeSyntheticVariable(rightVariable).also { it.freeze() }
    }

    private fun exitBooleanNot(functionCall: FirFunctionCall, node: FunctionCallNode) {
        val booleanExpressionVariable = getVariable(node.previousNodes.first().fir)
        val variable = getVariable(functionCall)
        node.flow = node.flow.copyNotApprovedFacts(booleanExpressionVariable, variable) { it.invert() }.also { it.freeze() }
    }

    private fun approveFact(variable: DataFlowVariable, value: ConditionValue, flow: Flow): MutableMap<DataFlowVariable, FirDataFlowInfo>? =
        logicSystem.approveFact(Condition(variable, ConditionOperator.Eq, value), flow)

    private fun FirBinaryLogicExpression.getVariables(): Pair<DataFlowVariable, DataFlowVariable> =
        getVariable(leftOperand) to getVariable(rightOperand)

    // -------------------------------------------------------------------------------------------------------------------------

    private var CFGNode<*>.flow: Flow
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

    private fun Flow.removeSyntheticVariable(variable: DataFlowVariable): Flow {
        if (!variable.isSynthetic) return this
        variableStorage.removeVariable(variable)
        return removeVariableFromFlow(variable)
    }
}