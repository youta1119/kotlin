/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.resolve.dfa.cfg

import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.resolve.dfa.DataFlowInfoMap

class ControlFlowGraph {
    val nodes = mutableListOf<CFGNode>()
    lateinit var startNode: StartNode
    lateinit var exitNode: ExitNode
}

abstract class CFGNode(val owner: ControlFlowGraph) {
    val previousNodes = mutableListOf<CFGNode>()
    val followingNodes = mutableListOf<CFGNode>()
}

private fun ControlFlowGraph.init(node: CFGNode) {
    nodes += node
}

class StartNode(owner: ControlFlowGraph, val function: FirFunction) : CFGNode(owner)
class ExitNode(owner: ControlFlowGraph, val function: FirFunction) : CFGNode(owner)
class WhenNode(owner: ControlFlowGraph, val whenExpression: FirWhenExpression) : CFGNode(owner)
class WhenExitNode(owner: ControlFlowGraph, val whenExpression: FirWhenExpression) : CFGNode(owner)
class WhenConditionBranchNode(owner: ControlFlowGraph, val condition: FirWhenBranch) : CFGNode(owner)
class VariableAccessNode(owner: ControlFlowGraph, val qualifiedAccess: FirQualifiedAccessExpression) : CFGNode(owner)
class TypeOperatorCallNode(owner: ControlFlowGraph, val typeOperatorCall: FirTypeOperatorCall) : CFGNode(owner)
class ConditionExitNode(owner: ControlFlowGraph, val whenBranch: FirWhenBranch, val outDataFlowInfo: DataFlowInfoMap) : CFGNode(owner)
class JumpNode(owner: ControlFlowGraph, val firJump: FirJump<*>) : CFGNode(owner)
class BlockEnterNode(owner: ControlFlowGraph, val block: FirBlock) : CFGNode(owner)
class BlockExitNode(owner: ControlFlowGraph, val block: FirBlock) : CFGNode(owner)

// -----------------------------------------------------------------

fun ControlFlowGraph.createTypeOperatorCallNode(typeOperatorCall: FirTypeOperatorCall): TypeOperatorCallNode =
    TypeOperatorCallNode(this, typeOperatorCall).also(this::init)

fun ControlFlowGraph.createConditionExitNode(whenBranch: FirWhenBranch, outDataFlowInfo: DataFlowInfoMap): ConditionExitNode =
    ConditionExitNode(this, whenBranch, outDataFlowInfo).also(this::init)

fun ControlFlowGraph.createJumpNode(firJump: FirJump<*>): JumpNode = JumpNode(this, firJump).also(this::init)

fun ControlFlowGraph.createVariableAccessNode(qualifiedAccess: FirQualifiedAccessExpression): VariableAccessNode =
    VariableAccessNode(this, qualifiedAccess).also(this::init)

fun ControlFlowGraph.createEnterBlockNode(block: FirBlock): BlockEnterNode = BlockEnterNode(this, block).also(this::init)

fun ControlFlowGraph.createExitBlockNode(block: FirBlock): BlockExitNode = BlockExitNode(this, block).also(this::init)


fun ControlFlowGraph.createStartNode(function: FirFunction): StartNode = StartNode(this, function).also {
    init(it)
    startNode = it
}

fun ControlFlowGraph.createExitNode(function: FirFunction): ExitNode = ExitNode(this, function).also {
    init(it)
    exitNode = it
}

fun ControlFlowGraph.createConditionNode(condition: FirWhenBranch): WhenConditionBranchNode =
    WhenConditionBranchNode(this, condition).also(this::init)

fun ControlFlowGraph.createWhenNode(whenExpression: FirWhenExpression): WhenNode = WhenNode(this, whenExpression).also(this::init)

fun ControlFlowGraph.createWhenExitNode(whenExpression: FirWhenExpression): WhenExitNode =
    WhenExitNode(this, whenExpression).also(this::init)