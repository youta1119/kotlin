/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.resolve.dfa.cfg

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.resolve.dfa.Condition

class ControlFlowGraph {
    val nodes = mutableListOf<CFGNode<*>>()
    lateinit var enterNode: FunctionEnterNode
    lateinit var exitNode: FunctionExitNode
}

sealed class CFGNode<out E : FirElement>(val owner: ControlFlowGraph) {
    val previousNodes = mutableListOf<CFGNode<*>>()
    val followingNodes = mutableListOf<CFGNode<*>>()

    abstract val fir: E
    var isDead: Boolean = false
}

val CFGNode<*>.usefullFollowingNodes: List<CFGNode<*>> get() = followingNodes.filterNot { it.isDead }
val CFGNode<*>.usefullPreviousNodes: List<CFGNode<*>> get() = followingNodes.filterNot { it.isDead }

private fun ControlFlowGraph.init(node: CFGNode<*>) {
    nodes += node
}

interface ReturnableNothingNode {
    val returnsNothing: Boolean
}

class FunctionEnterNode(owner: ControlFlowGraph, override val fir: FirFunction) : CFGNode<FirFunction>(owner)
class FunctionExitNode(owner: ControlFlowGraph, override val fir: FirFunction) : CFGNode<FirFunction>(owner)

class BlockEnterNode(owner: ControlFlowGraph, override val fir: FirBlock) : CFGNode<FirBlock>(owner)
class BlockExitNode(owner: ControlFlowGraph, override val fir: FirBlock) : CFGNode<FirBlock>(owner)

class WhenEnterNode(owner: ControlFlowGraph, override val fir: FirWhenExpression) : CFGNode<FirWhenExpression>(owner)
class WhenExitNode(owner: ControlFlowGraph, override val fir: FirWhenExpression) : CFGNode<FirWhenExpression>(owner)
class WhenBranchConditionEnterNode(owner: ControlFlowGraph, override val fir: FirWhenBranch) : CFGNode<FirWhenBranch>(owner)
class WhenBranchConditionExitNode(owner: ControlFlowGraph, override val fir: FirWhenBranch, val condition: Condition) :
    CFGNode<FirWhenBranch>(owner)

class WhenBranchResultExitNode(owner: ControlFlowGraph, override val fir: FirWhenBranch) : CFGNode<FirWhenBranch>(owner)

class LoopEnterNode(owner: ControlFlowGraph, override val fir: FirLoop) : CFGNode<FirLoop>(owner)
class LoopBlockEnterNode(owner: ControlFlowGraph, override val fir: FirLoop) : CFGNode<FirLoop>(owner)
class LoopBlockExitNode(owner: ControlFlowGraph, override val fir: FirLoop) : CFGNode<FirLoop>(owner)
class LoopConditionEnterNode(owner: ControlFlowGraph, override val fir: FirLoop) : CFGNode<FirLoop>(owner)
class LoopConditionExitNode(owner: ControlFlowGraph, override val fir: FirLoop) : CFGNode<FirLoop>(owner)
class LoopExitNode(owner: ControlFlowGraph, override val fir: FirLoop) : CFGNode<FirLoop>(owner)

class TryExpressionEnterNode(owner: ControlFlowGraph, override val fir: FirTryExpression) : CFGNode<FirTryExpression>(owner)
class TryMainBlockEnterNode(owner: ControlFlowGraph, override val fir: FirTryExpression) : CFGNode<FirTryExpression>(owner)
class TryMainBlockExitNode(owner: ControlFlowGraph, override val fir: FirTryExpression) : CFGNode<FirTryExpression>(owner)
class CatchClauseEnterNode(owner: ControlFlowGraph, override val fir: FirCatch) : CFGNode<FirCatch>(owner)
class CatchClauseExitNode(owner: ControlFlowGraph, override val fir: FirCatch) : CFGNode<FirCatch>(owner)
class FinallyBlockEnterNode(owner: ControlFlowGraph, override val fir: FirTryExpression) : CFGNode<FirTryExpression>(owner)
class FinallyBlockExitNode(owner: ControlFlowGraph, override val fir: FirTryExpression) : CFGNode<FirTryExpression>(owner)
class FinallyProxyEnterNode(owner: ControlFlowGraph, override val fir: FirTryExpression) : CFGNode<FirTryExpression>(owner)
class FinallyProxyExitNode(owner: ControlFlowGraph, override val fir: FirTryExpression) : CFGNode<FirTryExpression>(owner)
class TryExpressionExitNode(owner: ControlFlowGraph, override val fir: FirTryExpression) : CFGNode<FirTryExpression>(owner)

class TypeOperatorCallNode(owner: ControlFlowGraph, override val fir: FirTypeOperatorCall) : CFGNode<FirTypeOperatorCall>(owner)
class JumpNode(owner: ControlFlowGraph, override val fir: FirJump<*>) : CFGNode<FirJump<*>>(owner)
class ConstExpressionNode(owner: ControlFlowGraph, override val fir: FirConstExpression<*>) : CFGNode<FirConstExpression<*>>(owner)
class VariableDeclarationNode(owner: ControlFlowGraph, override val fir: FirVariable<*>) : CFGNode<FirVariable<*>>(owner)
class VariableAssignmentNode(owner: ControlFlowGraph, override val fir: FirVariableAssignment) : CFGNode<FirVariableAssignment>(owner)

class QualifiedAccessNode(
    owner: ControlFlowGraph,
    override val fir: FirQualifiedAccessExpression,
    override val returnsNothing: Boolean
) : CFGNode<FirQualifiedAccessExpression>(owner), ReturnableNothingNode

class FunctionCallNode(
    owner: ControlFlowGraph,
    override val fir: FirFunctionCall,
    override val returnsNothing: Boolean
) : CFGNode<FirFunctionCall>(owner), ReturnableNothingNode

class ThrowExceptionNode(
    owner: ControlFlowGraph,
    override val fir: FirThrowExpression
) : CFGNode<FirThrowExpression>(owner), ReturnableNothingNode {
    override val returnsNothing: Boolean get() = true
}

class StubNode(owner: ControlFlowGraph) : CFGNode<FirStub>(owner) {
    init {
        isDead = true
    }

    override val fir: FirStub get() = FirStub
}

object FirStub : FirElement {
    override val psi: PsiElement? get() = null
}

// -----------------------------------------------------------------

fun ControlFlowGraph.createStubNode(): StubNode = StubNode(this).also(this::init)

fun ControlFlowGraph.createLoopExitNode(loop: FirLoop): LoopExitNode = LoopExitNode(this, loop).also(this::init)
fun ControlFlowGraph.createLoopEnterNode(loop: FirLoop): LoopEnterNode = LoopEnterNode(this, loop).also(this::init)

fun ControlFlowGraph.createTypeOperatorCallNode(fir: FirTypeOperatorCall): TypeOperatorCallNode =
    TypeOperatorCallNode(this, fir).also(this::init)

fun ControlFlowGraph.createWhenBranchConditionExitNode(fir: FirWhenBranch, condition: Condition): WhenBranchConditionExitNode =
    WhenBranchConditionExitNode(this, fir, condition).also(this::init)

fun ControlFlowGraph.createJumpNode(fir: FirJump<*>): JumpNode = JumpNode(this, fir).also(this::init)

fun ControlFlowGraph.createQualifiedAccessNode(fir: FirQualifiedAccessExpression, returnsNothing: Boolean): QualifiedAccessNode =
    QualifiedAccessNode(this, fir, returnsNothing).also(this::init)

fun ControlFlowGraph.createBlockEnterNode(fir: FirBlock): BlockEnterNode = BlockEnterNode(this, fir).also(this::init)

fun ControlFlowGraph.createBlockExitNode(fir: FirBlock): BlockExitNode = BlockExitNode(this, fir).also(this::init)


fun ControlFlowGraph.createFunctionEnterNode(fir: FirFunction): FunctionEnterNode = FunctionEnterNode(this, fir).also {
    init(it)
    enterNode = it
}

fun ControlFlowGraph.createFunctionExitNode(fir: FirFunction): FunctionExitNode = FunctionExitNode(this, fir).also {
    init(it)
    exitNode = it
}

fun ControlFlowGraph.createWhenBranchConditionEnterNode(fir: FirWhenBranch): WhenBranchConditionEnterNode =
    WhenBranchConditionEnterNode(this, fir).also(this::init)

fun ControlFlowGraph.createWhenEnterNode(fir: FirWhenExpression): WhenEnterNode = WhenEnterNode(this, fir).also(this::init)

fun ControlFlowGraph.createWhenExitNode(fir: FirWhenExpression): WhenExitNode =
    WhenExitNode(this, fir).also(this::init)

fun ControlFlowGraph.createWhenBranchResultExitNode(fir: FirWhenBranch): WhenBranchResultExitNode =
    WhenBranchResultExitNode(this, fir).also(this::init)

fun ControlFlowGraph.createLoopConditionExitNode(fir: FirLoop): LoopConditionExitNode = LoopConditionExitNode(this, fir).also(this::init)
fun ControlFlowGraph.createLoopConditionEnterNode(fir: FirLoop): LoopConditionEnterNode = LoopConditionEnterNode(this, fir).also(this::init)
fun ControlFlowGraph.createLoopBlockEnterNode(fir: FirLoop): LoopBlockEnterNode = LoopBlockEnterNode(this, fir).also(this::init)
fun ControlFlowGraph.createLoopBlockExitNode(fir: FirLoop): LoopBlockExitNode = LoopBlockExitNode(this, fir).also(this::init)
fun ControlFlowGraph.createFunctionCallNode(fir: FirFunctionCall, returnsNothing: Boolean): FunctionCallNode = FunctionCallNode(this, fir, returnsNothing).also(this::init)
fun ControlFlowGraph.createVariableAssignmentNode(fir: FirVariableAssignment): VariableAssignmentNode =
    VariableAssignmentNode(this, fir).also(this::init)

fun ControlFlowGraph.createVariableDeclarationNode(fir: FirVariable<*>): VariableDeclarationNode =
    VariableDeclarationNode(this, fir).also(this::init)

fun ControlFlowGraph.createConstExpressionNode(fir: FirConstExpression<*>): ConstExpressionNode =
    ConstExpressionNode(this, fir).also(this::init)

fun ControlFlowGraph.createThrowExceptionNode(fir: FirThrowExpression): ThrowExceptionNode = ThrowExceptionNode(this, fir).also(this::init)

fun ControlFlowGraph.createFinallyProxyExitNode(fir: FirTryExpression): FinallyProxyExitNode = FinallyProxyExitNode(this, fir).also(this::init)
fun ControlFlowGraph.createFinallyProxyEnterNode(fir: FirTryExpression): FinallyProxyEnterNode = FinallyProxyEnterNode(this, fir).also(this::init)
fun ControlFlowGraph.createFinallyBlockExitNode(fir: FirTryExpression): FinallyBlockExitNode = FinallyBlockExitNode(this, fir).also(this::init)
fun ControlFlowGraph.createFinallyBlockEnterNode(fir: FirTryExpression): FinallyBlockEnterNode = FinallyBlockEnterNode(this, fir).also(this::init)
fun ControlFlowGraph.createCatchClauseExitNode(fir: FirCatch): CatchClauseExitNode = CatchClauseExitNode(this, fir).also(this::init)
fun ControlFlowGraph.createTryMainBlockExitNode(fir: FirTryExpression): TryMainBlockExitNode = TryMainBlockExitNode(this, fir).also(this::init)
fun ControlFlowGraph.createTryMainBlockEnterNode(fir: FirTryExpression): TryMainBlockEnterNode = TryMainBlockEnterNode(this, fir).also(this::init)
fun ControlFlowGraph.createCatchClauseEnterNode(fir: FirCatch): CatchClauseEnterNode = CatchClauseEnterNode(this, fir).also(this::init)
fun ControlFlowGraph.createTryExpressionEnterNode(fir: FirTryExpression): TryExpressionEnterNode = TryExpressionEnterNode(this, fir).also(this::init)
fun ControlFlowGraph.createTryExpressionExitNode(fir: FirTryExpression): TryExpressionExitNode = TryExpressionExitNode(this, fir).also(this::init)