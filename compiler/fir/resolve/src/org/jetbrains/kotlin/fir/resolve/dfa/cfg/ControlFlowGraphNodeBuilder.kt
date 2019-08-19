/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.resolve.dfa.cfg

import org.jetbrains.kotlin.fir.declarations.FirAnonymousInitializer
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.expressions.*

abstract class ControlFlowGraphNodeBuilder {
    protected abstract val graph: ControlFlowGraph
    protected abstract var levelCounter: Int

    protected fun createStubNode(): StubNode = StubNode(
        graph,
        levelCounter
    ).also { graph.init(it) }

    protected fun createLoopExitNode(fir: FirLoop): LoopExitNode = LoopExitNode(graph, fir, levelCounter).also { graph.init(it) }

    protected fun createLoopEnterNode(fir: FirLoop): LoopEnterNode = LoopEnterNode(graph, fir, levelCounter).also { graph.init(it) }

    protected fun createInitBlockExitNode(fir: FirAnonymousInitializer): InitBlockExitNode =
        InitBlockExitNode(graph, fir, levelCounter).also { graph.init(it) }

    protected fun createInitBlockEnterNode(fir: FirAnonymousInitializer): InitBlockEnterNode =
        InitBlockEnterNode(graph, fir, levelCounter).also { graph.init(it) }

    protected fun createTypeOperatorCallNode(fir: FirTypeOperatorCall): TypeOperatorCallNode =
        TypeOperatorCallNode(graph, fir, levelCounter).also { graph.init(it) }

    protected fun createOperatorCallNode(fir: FirOperatorCall): OperatorCallNode =
        OperatorCallNode(graph, fir, levelCounter).also { graph.init(it) }

    protected fun createWhenBranchConditionExitNode(fir: FirWhenBranch): WhenBranchConditionExitNode =
        WhenBranchConditionExitNode(graph, fir, levelCounter).also { graph.init(it) }

    protected fun createJumpNode(fir: FirJump<*>): JumpNode =
        JumpNode(graph, fir, levelCounter).also { graph.init(it) }

    protected fun createQualifiedAccessNode(
        fir: FirQualifiedAccessExpression,
        returnsNothing: Boolean
    ): QualifiedAccessNode = QualifiedAccessNode(graph, fir, returnsNothing, levelCounter).also { graph.init(it) }

    protected fun createBlockEnterNode(fir: FirBlock): BlockEnterNode = BlockEnterNode(graph, fir, levelCounter).also { graph.init(it) }

    protected fun createBlockExitNode(fir: FirBlock): BlockExitNode = BlockExitNode(graph, fir, levelCounter).also { graph.init(it) }

    protected fun createPropertyExitNode(fir: FirProperty): PropertyExitNode = PropertyExitNode(graph, fir, levelCounter).also { graph.init(it) }

    protected fun createPropertyEnterNode(fir: FirProperty): PropertyEnterNode = PropertyEnterNode(graph, fir, levelCounter).also { graph.init(it) }

    protected fun createFunctionEnterNode(fir: FirFunction): FunctionEnterNode =
        FunctionEnterNode(graph, fir, levelCounter).also {
            graph.init(it)
            graph.enterNode = it
        }

    protected fun createFunctionExitNode(fir: FirFunction): FunctionExitNode = FunctionExitNode(graph, fir, levelCounter).also {
        graph.init(it)
        graph.exitNode = it
    }

    protected fun createBinaryOrEnterNode(fir: FirBinaryLogicExpression): BinaryOrEnterNode =
        BinaryOrEnterNode(graph, fir, levelCounter).also { graph.init(it) }

    protected fun createBinaryOrExitLeftOperandNode(fir: FirBinaryLogicExpression): BinaryOrExitLeftOperandNode =
        BinaryOrExitLeftOperandNode(graph, fir, levelCounter).also { graph.init(it) }

    protected fun createBinaryOrExitNode(fir: FirBinaryLogicExpression): BinaryOrExitNode =
        BinaryOrExitNode(graph, fir, levelCounter).also { graph.init(it) }

    protected fun createBinaryAndExitNode(fir: FirBinaryLogicExpression): BinaryAndExitNode =
        BinaryAndExitNode(graph, fir, levelCounter).also { graph.init(it) }

    protected fun createBinaryAndEnterNode(fir: FirBinaryLogicExpression): BinaryAndEnterNode =
        BinaryAndEnterNode(graph, fir, levelCounter).also { graph.init(it) }

    protected fun createWhenBranchConditionEnterNode(fir: FirWhenBranch): WhenBranchConditionEnterNode =
        WhenBranchConditionEnterNode(graph, fir, levelCounter).also { graph.init(it) }

    protected fun createWhenEnterNode(fir: FirWhenExpression): WhenEnterNode =
        WhenEnterNode(graph, fir, levelCounter).also { graph.init(it) }

    protected fun createWhenExitNode(fir: FirWhenExpression): WhenExitNode =
        WhenExitNode(graph, fir, levelCounter).also { graph.init(it) }

    protected fun createWhenBranchResultExitNode(fir: FirWhenBranch): WhenBranchResultExitNode =
        WhenBranchResultExitNode(graph, fir, levelCounter).also { graph.init(it) }

    protected fun createLoopConditionExitNode(fir: FirLoop): LoopConditionExitNode =
        LoopConditionExitNode(graph, fir, levelCounter).also { graph.init(it) }

    protected fun createLoopConditionEnterNode(fir: FirLoop): LoopConditionEnterNode =
        LoopConditionEnterNode(graph, fir, levelCounter).also { graph.init(it) }

    protected fun createLoopBlockEnterNode(fir: FirLoop): LoopBlockEnterNode =
        LoopBlockEnterNode(graph, fir, levelCounter).also { graph.init(it) }

    protected fun createLoopBlockExitNode(fir: FirLoop): LoopBlockExitNode =
        LoopBlockExitNode(graph, fir, levelCounter).also { graph.init(it) }

    protected fun createFunctionCallNode(fir: FirFunctionCall, returnsNothing: Boolean): FunctionCallNode =
        FunctionCallNode(graph, fir, returnsNothing, levelCounter).also { graph.init(it) }

    protected fun createVariableAssignmentNode(fir: FirVariableAssignment): VariableAssignmentNode =
        VariableAssignmentNode(graph, fir, levelCounter).also { graph.init(it) }

    protected fun createAnnotationExitNode(fir: FirAnnotationCall): AnnotationExitNode =
        AnnotationExitNode(graph, fir, levelCounter).also { graph.init(it) }

    protected fun createAnnotationEnterNode(fir: FirAnnotationCall): AnnotationEnterNode =
        AnnotationEnterNode(graph, fir, levelCounter).also { graph.init(it) }

    protected fun createVariableDeclarationNode(fir: FirVariable<*>): VariableDeclarationNode =
        VariableDeclarationNode(graph, fir, levelCounter).also { graph.init(it) }

    protected fun createConstExpressionNode(fir: FirConstExpression<*>): ConstExpressionNode =
        ConstExpressionNode(graph, fir, levelCounter).also { graph.init(it) }

    protected fun createThrowExceptionNode(fir: FirThrowExpression): ThrowExceptionNode =
        ThrowExceptionNode(graph, fir, levelCounter).also { graph.init(it) }

    protected fun createFinallyProxyExitNode(fir: FirTryExpression): FinallyProxyExitNode =
        FinallyProxyExitNode(graph, fir, levelCounter).also { graph.init(it) }

    protected fun createFinallyProxyEnterNode(fir: FirTryExpression): FinallyProxyEnterNode =
        FinallyProxyEnterNode(graph, fir, levelCounter).also { graph.init(it) }

    protected fun createFinallyBlockExitNode(fir: FirTryExpression): FinallyBlockExitNode =
        FinallyBlockExitNode(graph, fir, levelCounter).also { graph.init(it) }

    protected fun createFinallyBlockEnterNode(fir: FirTryExpression): FinallyBlockEnterNode =
        FinallyBlockEnterNode(graph, fir, levelCounter).also { graph.init(it) }

    protected fun createCatchClauseExitNode(fir: FirCatch): CatchClauseExitNode =
        CatchClauseExitNode(graph, fir, levelCounter).also { graph.init(it) }

    protected fun createTryMainBlockExitNode(fir: FirTryExpression): TryMainBlockExitNode =
        TryMainBlockExitNode(graph, fir, levelCounter).also { graph.init(it) }

    protected fun createTryMainBlockEnterNode(fir: FirTryExpression): TryMainBlockEnterNode =
        TryMainBlockEnterNode(graph, fir, levelCounter).also { graph.init(it) }

    protected fun createCatchClauseEnterNode(fir: FirCatch): CatchClauseEnterNode =
        CatchClauseEnterNode(graph, fir, levelCounter).also { graph.init(it) }

    protected fun createTryExpressionEnterNode(fir: FirTryExpression): TryExpressionEnterNode =
        TryExpressionEnterNode(graph, fir, levelCounter).also { graph.init(it) }

    protected fun createTryExpressionExitNode(fir: FirTryExpression): TryExpressionExitNode =
        TryExpressionExitNode(graph, fir, levelCounter).also { graph.init(it) }

    private fun ControlFlowGraph.init(node: CFGNode<*>) {
        nodes += node
    }

}