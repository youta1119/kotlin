/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.resolve.dfa.cfg

import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.render
import org.jetbrains.kotlin.fir.resolve.dfa.NodeStorage
import org.jetbrains.kotlin.fir.resolve.dfa.Stack
import org.jetbrains.kotlin.fir.resolve.dfa.stackOf
import org.jetbrains.kotlin.fir.resolve.transformers.resultType
import org.jetbrains.kotlin.fir.types.isNothing

open class ControlFlowGraphBuilder {
    private val graphs: Stack<ControlFlowGraph> = stackOf()
    private val graph: ControlFlowGraph get() = graphs.top()

    private val lexicalScopes: Stack<Stack<CFGNode<*>>> = stackOf()
    private val lastNodes: Stack<CFGNode<*>> get() = lexicalScopes.top()

    private val whenExitNodes: NodeStorage<FirWhenExpression, WhenExitNode> = NodeStorage()
    private val functionExitNodes: NodeStorage<FirFunction, FunctionExitNode> = NodeStorage()
    private val loopEnterNodes: NodeStorage<FirElement, CFGNode<FirElement>> = NodeStorage()
    private val loopExitNodes: NodeStorage<FirLoop, LoopExitNode> = NodeStorage()

    private val tryExitNodes: NodeStorage<FirTryExpression, TryExpressionExitNode> = NodeStorage()
    private val catchNodeStorages: Stack<NodeStorage<FirCatch, CatchClauseEnterNode>> = stackOf()
    private val catchNodeStorage: NodeStorage<FirCatch, CatchClauseEnterNode> get() = catchNodeStorages.top()

    val lastNode: CFGNode<*> get() = lastNodes.top()

    // ----------------------------------- Callbacks -----------------------------------

    open fun passFlow(from: CFGNode<*>, to: CFGNode<*>) {}

    // ----------------------------------- Named function -----------------------------------

    fun enterNamedFunction(namedFunction: FirNamedFunction): FunctionEnterNode {
        graphs.push(ControlFlowGraph())
        lexicalScopes.push(stackOf())
        functionExitNodes.push(graph.createFunctionExitNode(namedFunction))
        return graph.createFunctionEnterNode(namedFunction).also { lastNodes.push(it) }
    }

    fun exitNamedFunction(namedFunction: FirNamedFunction): ControlFlowGraph {
        val exitNode = functionExitNodes.pop()
        addEdge(lastNodes.pop(), exitNode)
        assert(exitNode.fir == namedFunction)
        lexicalScopes.pop()
        return graphs.pop()
    }

    // ----------------------------------- Block -----------------------------------

    fun enterBlock(block: FirBlock): BlockEnterNode {
        return graph.createBlockEnterNode(block).also { addNewSimpleNode(it) }
    }

    fun exitBlock(block: FirBlock): BlockExitNode {
        return graph.createBlockExitNode(block).also { addNewSimpleNode(it) }
    }

    // ----------------------------------- Type operator call -----------------------------------

    fun exitTypeOperatorCall(typeOperatorCall: FirTypeOperatorCall): TypeOperatorCallNode {
        return graph.createTypeOperatorCallNode(typeOperatorCall).also { addNewSimpleNode(it) }
    }

    // ----------------------------------- Jump -----------------------------------

    fun exitJump(jump: FirJump<*>): JumpNode {
        val node = graph.createJumpNode(jump)
        val nextNode = when (jump) {
            is FirReturnExpression -> functionExitNodes[jump.target.labeledElement]
            is FirContinueExpression -> loopEnterNodes[jump.target.labeledElement]
            is FirBreakExpression -> loopExitNodes[jump.target.labeledElement]
            else -> throw IllegalArgumentException("Unknown jump type: ${jump.render()}")
        }
        addNodeWithJump(node, nextNode)
        return node
    }

    // ----------------------------------- When -----------------------------------

    fun enterWhenExpression(whenExpression: FirWhenExpression): WhenEnterNode {
        val node = graph.createWhenEnterNode(whenExpression)
        addNewSimpleNode(node)
        whenExitNodes.push(graph.createWhenExitNode(whenExpression))
        return node
    }

    fun enterWhenBranchCondition(whenBranch: FirWhenBranch): WhenBranchConditionEnterNode {
        return graph.createWhenBranchConditionEnterNode(whenBranch).also { addNewSimpleNode(it) }
    }

    fun exitWhenBranchCondition(whenBranch: FirWhenBranch): WhenBranchConditionExitNode {
        val node = graph.createWhenBranchConditionExitNode(whenBranch)
        addNewSimpleNode(node)
        // put exit branch condition node twice so we can refer it after exit from when expression
        lastNodes.push(node)
        return node
    }

    fun exitWhenBranchResult(whenBranch: FirWhenBranch): WhenBranchResultExitNode {
        val node = graph.createWhenBranchResultExitNode(whenBranch)
        addEdge(lastNodes.pop(), node)
        val whenExitNode = whenExitNodes.top()
        addEdge(node, whenExitNode, propagateDeadness = false)
        return node
    }

    fun exitWhenExpression(whenExpression: FirWhenExpression): WhenExitNode {
        // exit from last condition node still on stack
        // we should remove it
        require(lastNodes.pop() is WhenBranchConditionExitNode)
        val whenExitNode = whenExitNodes.pop()
        lastNodes.push(whenExitNode)
        return whenExitNode
    }

    // ----------------------------------- While Loop -----------------------------------

    fun enterWhileLoop(loop: FirLoop): LoopConditionEnterNode {
        addNewSimpleNode(graph.createLoopEnterNode(loop))
        val node = graph.createLoopConditionEnterNode(loop)
        addNewSimpleNode(node)
        // put conditional node twice so we can refer it after exit from loop block
        lastNodes.push(node)
        loopEnterNodes.push(node)
        loopExitNodes.push(graph.createLoopExitNode(loop))
        return node
    }

    fun exitWhileLoopCondition(loop: FirLoop): LoopConditionExitNode {
        val conditionExitNode = graph.createLoopConditionExitNode(loop)
        addNewSimpleNode(conditionExitNode)
        // TODO: here we can check that condition is always true
        addEdge(conditionExitNode, loopExitNodes.top())
        addNewSimpleNode(graph.createLoopBlockEnterNode(loop))
        return conditionExitNode
    }

    fun exitWhileLoop(loop: FirLoop): CFGNode<*> {
        loopEnterNodes.pop()
        val loopBlockExitNode = graph.createLoopBlockExitNode(loop)
        addEdge(lastNodes.pop(), loopBlockExitNode)
        val conditionEnterNode = lastNodes.pop()
        require(conditionEnterNode is LoopConditionEnterNode)
        addEdge(loopBlockExitNode, conditionEnterNode, propagateDeadness = false)
        lastNodes.push(loopExitNodes.pop())
        return conditionEnterNode
    }

    // ----------------------------------- Do while Loop -----------------------------------

    fun enterDoWhileLoop(loop: FirLoop) {
        addNewSimpleNode(graph.createLoopEnterNode(loop))
        val blockEnterNode = graph.createLoopBlockEnterNode(loop)
        addNewSimpleNode(blockEnterNode)
        // put block enter node twice so we can refer it after exit from loop condition
        lastNodes.push(blockEnterNode)
        loopEnterNodes.push(blockEnterNode)
        loopExitNodes.push(graph.createLoopExitNode(loop))
    }

    fun enterDoWhileLoopCondition(loop: FirLoop) {
        addNewSimpleNode(graph.createLoopBlockExitNode(loop))
        addNewSimpleNode(graph.createLoopConditionEnterNode(loop))
    }

    fun exitDoWhileLoop(loop: FirLoop) {
        loopEnterNodes.pop()
        val conditionExitNode = graph.createLoopConditionExitNode(loop)
        // TODO: here we can check that condition is always false
        addEdge(lastNodes.pop(), conditionExitNode)
        val blockEnterNode = lastNodes.pop()
        require(blockEnterNode is LoopBlockEnterNode)
        addEdge(conditionExitNode, blockEnterNode, propagateDeadness = false)
        val loopExit = loopExitNodes.pop()
        addEdge(conditionExitNode, loopExit)
        lastNodes.push(loopExit)
    }

    // ----------------------------------- Try-catch-finally -----------------------------------

    fun enterTryExpression(tryExpression: FirTryExpression): TryMainBlockEnterNode {
        catchNodeStorages.push(NodeStorage())
        addNewSimpleNode(graph.createTryExpressionEnterNode(tryExpression))
        val tryNode = graph.createTryMainBlockEnterNode(tryExpression)
        addNewSimpleNode(tryNode)
        addEdge(tryNode, functionExitNodes.top())
        tryExitNodes.push(graph.createTryExpressionExitNode(tryExpression))

        for (catch in tryExpression.catches) {
            val catchNode = graph.createCatchClauseEnterNode(catch)
            catchNodeStorage.push(catchNode)
            addEdge(tryNode, catchNode)
            addEdge(catchNode, functionExitNodes.top())
        }

        // TODO: add finally
        return tryNode
    }

    fun exitTryMainBlock(tryExpression: FirTryExpression): TryMainBlockExitNode {
        val node = graph.createTryMainBlockExitNode(tryExpression)
        addEdge(lastNodes.pop(), node)
        addEdge(node, tryExitNodes.top())
        return node
    }

    fun enterCatchClause(catch: FirCatch): CatchClauseEnterNode {
        return catchNodeStorage[catch].also { lastNodes.push(it) }
    }

    fun exitCatchClause(catch: FirCatch): CatchClauseExitNode {
        return graph.createCatchClauseExitNode(catch).also {
            addEdge(lastNodes.pop(), it)
            addEdge(it, tryExitNodes.top(), propagateDeadness = false)
        }
    }

    fun enterFinallyBlock(tryExpression: FirTryExpression) { /*TODO*/ }

    fun exitFinallyBlock(tryExpression: FirTryExpression) { /*TODO*/ }

    fun exitTryExpression(tryExpression: FirTryExpression): TryExpressionExitNode {
        catchNodeStorages.pop()
        val node = tryExitNodes.pop()
        node.markAsDeadIfNecessary()
        lastNodes.push(node)
        return node
    }

    // ----------------------------------- Resolvable call -----------------------------------

    fun exitQualifiedAccessExpression(qualifiedAccessExpression: FirQualifiedAccessExpression): QualifiedAccessNode {
        val returnsNothing = qualifiedAccessExpression.resultType.isNothing
        val node = graph.createQualifiedAccessNode(qualifiedAccessExpression, returnsNothing)
        if (returnsNothing) {
            addNodeThatReturnsNothing(node)
        } else {
            addNewSimpleNode(node)
        }
        return node
    }

    fun exitFunctionCall(functionCall: FirFunctionCall): FunctionCallNode {
        val returnsNothing = functionCall.resultType.isNothing
        val node = graph.createFunctionCallNode(functionCall, returnsNothing)
        if (returnsNothing) {
            addNodeThatReturnsNothing(node)
        } else {
            addNewSimpleNode(node)
        }
        return node
    }

    fun exitConstExpresion(constExpression: FirConstExpression<*>): ConstExpressionNode {
        return graph.createConstExpressionNode(constExpression).also { addNewSimpleNode(it) }
    }

    fun exitVariableDeclaration(variable: FirVariable<*>): VariableDeclarationNode {
        return graph.createVariableDeclarationNode(variable).also { addNewSimpleNode(it) }
    }

    fun exitVariableAssignment(assignment: FirVariableAssignment): VariableAssignmentNode {
        return graph.createVariableAssignmentNode(assignment).also { addNewSimpleNode(it) }
    }

    fun exitThrowExceptionNode(throwExpression: FirThrowExpression): ThrowExceptionNode {
        return graph.createThrowExceptionNode(throwExpression).also { addNodeThatReturnsNothing(it) }
    }

    // -------------------------------------------------------------------------------------------------------------------------

    private fun CFGNode<*>.markAsDeadIfNecessary() {
        isDead = previousNodes.all { it.isDead }
    }

    private fun addNodeThatReturnsNothing(node: CFGNode<*>) {
        addNodeWithJump(node, functionExitNodes.top())
    }

    private fun addNodeWithJump(node: CFGNode<*>, targetNode: CFGNode<*>) {
        addEdge(lastNodes.pop(), node)
        addEdge(node, targetNode)
        val stub = graph.createStubNode()
        addEdge(node, stub)
        lastNodes.push(stub)
    }

    private fun addNewSimpleNode(newNode: CFGNode<*>): CFGNode<*> {
        val oldNode = lastNodes.pop()
        addEdge(oldNode, newNode)
        lastNodes.push(newNode)
        return oldNode
    }

    private fun addEdge(from: CFGNode<*>, to: CFGNode<*>, shouldPassFlow: Boolean = true, propagateDeadness: Boolean = true) {
        if (propagateDeadness && from.isDead) {
            to.isDead = true
        }
        from.followingNodes += to
        to.previousNodes += from
        if (shouldPassFlow) {
            passFlow(from, to)
        }
    }
}