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

abstract class CFGNode<E : FirElement>(val owner: ControlFlowGraph) {
    val previousNodes = mutableListOf<CFGNode<*>>()
    val followingNodes = mutableListOf<CFGNode<*>>()

    abstract val fir: E
}

private fun ControlFlowGraph.init(node: CFGNode<*>) {
    nodes += node
}

class FunctionEnterNode(owner: ControlFlowGraph, override val fir: FirFunction) : CFGNode<FirFunction>(owner)
class FunctionExitNode(owner: ControlFlowGraph, override val fir: FirFunction) : CFGNode<FirFunction>(owner)

class BlockEnterNode(owner: ControlFlowGraph, override val fir: FirBlock) : CFGNode<FirBlock>(owner)
class BlockExitNode(owner: ControlFlowGraph, override val fir: FirBlock) : CFGNode<FirBlock>(owner)

class WhenEnterNode(owner: ControlFlowGraph, override val fir: FirWhenExpression) : CFGNode<FirWhenExpression>(owner)
class WhenExitNode(owner: ControlFlowGraph, override val fir: FirWhenExpression) : CFGNode<FirWhenExpression>(owner)
class WhenBranchConditionEnterNode(owner: ControlFlowGraph, override val fir: FirWhenBranch) : CFGNode<FirWhenBranch>(owner)
class WhenBranchConditionExitNode(owner: ControlFlowGraph, override val fir: FirWhenBranch, val condition: Condition) : CFGNode<FirWhenBranch>(owner)
class WhenBranchResultExitNode(owner: ControlFlowGraph, override val fir: FirWhenBranch) : CFGNode<FirWhenBranch>(owner)

class LoopEnterNode(owner: ControlFlowGraph, override val fir: FirLoop) : CFGNode<FirLoop>(owner)
class LoopBlockEnterNode(owner: ControlFlowGraph, override val fir: FirLoop) : CFGNode<FirLoop>(owner)
class LoopBlockExitNode(owner: ControlFlowGraph, override val fir: FirLoop) : CFGNode<FirLoop>(owner)
class LoopConditionEnterNode(owner: ControlFlowGraph, override val fir: FirLoop) : CFGNode<FirLoop>(owner)
class LoopConditionExitNode(owner: ControlFlowGraph, override val fir: FirLoop) : CFGNode<FirLoop>(owner)
class LoopExitNode(owner: ControlFlowGraph, override val fir: FirLoop) : CFGNode<FirLoop>(owner)

class VariableAccessNode(owner: ControlFlowGraph, override val fir: FirQualifiedAccessExpression) : CFGNode<FirQualifiedAccessExpression>(owner)
class TypeOperatorCallNode(owner: ControlFlowGraph, override val fir: FirTypeOperatorCall) : CFGNode<FirTypeOperatorCall>(owner)
class JumpNode(owner: ControlFlowGraph, override val fir: FirJump<*>) : CFGNode<FirJump<*>>(owner)

class StubNode(owner: ControlFlowGraph) : CFGNode<FirStub>(owner) {
    override val fir: FirStub get() = FirStub
}

object FirStub : FirElement {
    override val psi: PsiElement? get() = null
}

// -----------------------------------------------------------------

fun ControlFlowGraph.createStubNode() : StubNode = StubNode(this).also(this::init)

fun ControlFlowGraph.createLoopExitNode(loop: FirLoop): LoopExitNode = LoopExitNode(this, loop).also(this::init)
fun ControlFlowGraph.createLoopEnterNode(loop: FirLoop): LoopEnterNode = LoopEnterNode(this, loop).also(this::init)

fun ControlFlowGraph.createTypeOperatorCallNode(fir: FirTypeOperatorCall): TypeOperatorCallNode =
    TypeOperatorCallNode(this, fir).also(this::init)

fun ControlFlowGraph.createWhenBranchConditionExitNode(fir: FirWhenBranch, condition: Condition): WhenBranchConditionExitNode =
    WhenBranchConditionExitNode(this, fir, condition).also(this::init)

fun ControlFlowGraph.createJumpNode(fir: FirJump<*>): JumpNode = JumpNode(this, fir).also(this::init)

fun ControlFlowGraph.createVariableAccessNode(fir: FirQualifiedAccessExpression): VariableAccessNode =
    VariableAccessNode(this, fir).also(this::init)

fun ControlFlowGraph.createEnterBlockNode(fir: FirBlock): BlockEnterNode = BlockEnterNode(this, fir).also(this::init)

fun ControlFlowGraph.createBlockExitNode(fir: FirBlock): BlockExitNode = BlockExitNode(this, fir).also(this::init)


fun ControlFlowGraph.createStartNode(fir: FirFunction): FunctionEnterNode = FunctionEnterNode(this, fir).also {
    init(it)
    enterNode = it
}

fun ControlFlowGraph.createExitNode(fir: FirFunction): FunctionExitNode = FunctionExitNode(this, fir).also {
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