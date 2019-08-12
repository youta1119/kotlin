/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.resolve.dfa

import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirResolvedCallableReference
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.render
import org.jetbrains.kotlin.fir.resolve.BodyResolveComponents
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.*
import org.jetbrains.kotlin.fir.resolve.transformers.FirBodyResolveTransformer
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.types.*


class FirDataFlowAnalyzer(transformer: FirBodyResolveTransformer) : BodyResolveComponents by transformer {
    private val context: ConeTypeContext get() = inferenceComponents.ctx as ConeTypeContext
    private val factSystem: FactSystem = FactSystem(context)

    private val graphs: Stack<ControlFlowGraph> = stackOf()
    private val graph: ControlFlowGraph get() = graphs.top()

    private val lexicalScopes: Stack<Stack<CFGNode<*>>> = stackOf()
    private val lastNodes: Stack<CFGNode<*>> get() = lexicalScopes.top()

    private val whenExitNodes: NodeStorage<FirWhenExpression, WhenExitNode> = NodeStorage()
    private val functionExitNodes: NodeStorage<FirFunction, FunctionExitNode> = NodeStorage()
    private val loopEnterNodes: NodeStorage<FirLoop, LoopEnterNode> = NodeStorage()
    private val loopExitNodes: NodeStorage<FirLoop, LoopExitNode> = NodeStorage()

    private val variableStorage = DataFlowVariableStorage()
    private val edges = mutableMapOf<CFGNode<*>, Facts>().withDefault { Facts.EMPTY }
    private val outputEdges = mutableMapOf<CFGNode<*>, Facts>()

    private val conditionVariables: Stack<DataFlowVariable> = stackOf()

    fun getTypeUsingSmartcastInfo(qualifiedAccessExpression: FirQualifiedAccessExpression): ConeKotlinType? {
        val lastNode = lastNodes.top()
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

    fun enterNamedFunction(namedFunction: FirNamedFunction) {
        variableStorage.reset()
        graphs.push(ControlFlowGraph())
        lexicalScopes.push(stackOf(graph.createFunctionEnterNode(namedFunction)))
        functionExitNodes.push(graph.createFunctionExitNode(namedFunction))

        for (valueParameter in namedFunction.valueParameters) {
            variableStorage.createNewRealVariable(valueParameter)
        }
    }

    fun exitNamedFunction(namedFunction: FirNamedFunction): ControlFlowGraph {
        val exitNode = functionExitNodes.pop()
        addEdge(lastNodes.pop(), exitNode)
        assert(exitNode.fir == namedFunction)
        return graphs.pop()
    }

    // ----------------------------------- Block -----------------------------------

    fun enterBlock(block: FirBlock) {
        addNewSimpleNode(graph.createBlockEnterNode(block))
//        val enterBlockNode = graph.createEnterBlockNode(block)
//        val lastNode = lastNodes.pop()
//        val edgeFromCondition = lastNode is WhenBranchConditionExitNode
//        addEdge(lastNode, enterBlockNode, addInfo = !edgeFromCondition)
//        if (edgeFromCondition) {
//            val trueCondition = (lastNode as WhenBranchConditionExitNode).condition
//            enterBlockNode.facts = factSystem.verifyFacts(lastNode.facts, trueCondition, lexicalScopes.size)
//        }
//        lastNodes.push(enterBlockNode)
//        lexicalScopes.push(stackOf(enterBlockNode))
    }

    fun exitBlock(block: FirBlock) {
        addNewSimpleNode(graph.createBlockExitNode(block))
//        val blockExitNode = graph.createBlockExitNode(block)
//        val lastNode = lastNodes.pop()
//        addEdge(lastNode, blockExitNode, addInfo = false)
//        lexicalScopes.pop()
//        addEdge(blockExitNode, lastNodes.pop())
//        lastNodes.push(blockExitNode)
    }

    // ----------------------------------- Type operator call -----------------------------------

    fun exitTypeOperatorCall(typeOperatorCall: FirTypeOperatorCall) {
        val node = graph.createTypeOperatorCallNode(typeOperatorCall)
        addNewSimpleNode(node)
//        if (typeOperatorCall.operation == FirOperation.IS) {
//            val symbol = typeOperatorCall.argument.toResolvedCallableSymbol() as? FirBasedSymbol<*> ?: return
//            val type = typeOperatorCall.conversionTypeRef.coneTypeSafe<ConeKotlinType>() ?: return
//
//            val variable = variableStorage[symbol.fir] ?: return
//            val conditionalVariable = conditionVariables.top()
//            val info = UnverifiedInfo(
//                BooleanCondition(conditionalVariable, true),
//                FirDataFlowInfo(variable, setOf(type), emptySet())
//            )
//            outputEdges[node] = node.facts + info
//        }
    }

    // ----------------------------------- Jump -----------------------------------

    fun exitJump(jump: FirJump<*>) {
        val node = graph.createJumpNode(jump)
        addEdge(lastNodes.pop(), node)
        val nextNode = when (jump) {
            is FirReturnExpression -> functionExitNodes[jump.target.labeledElement]
            is FirContinueExpression -> loopEnterNodes[jump.target.labeledElement]
            is FirBreakExpression -> loopExitNodes[jump.target.labeledElement]
            else -> throw IllegalArgumentException("Unknown jump type: ${jump.render()}")
        }
        addEdge(node, nextNode)
        lastNodes.push(graph.createStubNode())
    }

    // ----------------------------------- When -----------------------------------

    fun enterWhenExpression(whenExpression: FirWhenExpression) {
        addNewSimpleNode(graph.createWhenEnterNode(whenExpression))
        whenExitNodes.push(graph.createWhenExitNode(whenExpression))
    }

    fun enterWhenBranchCondition(whenBranch: FirWhenBranch) {
        addNewSimpleNode(graph.createWhenBranchConditionEnterNode(whenBranch))

        conditionVariables.push(variableStorage.createNewSyntheticVariable(whenBranch.condition))
    }

    fun exitWhenBranchCondition(whenBranch: FirWhenBranch) {
        val conditionVariable = conditionVariables.pop()
        val trueCondition = BooleanCondition(conditionVariable, true)

        val node = graph.createWhenBranchConditionExitNode(whenBranch, trueCondition)
        addNewSimpleNode(node)
        // put exit branch condition node twice so we can refer it after exit from when expression
        lastNodes.push(node)
    }

    fun exitWhenBranchResult(whenBranch: FirWhenBranch) {
        val node = graph.createWhenBranchResultExitNode(whenBranch)
        addEdge(lastNodes.pop(), node)
        val whenExitNode = whenExitNodes.top()
        addEdge(node, whenExitNode)

//        val factsBeforeResult = conditionExitNode.facts
//        val factsFromResult = lastResultNode.facts
//        val newVerifiedInfos = factsFromResult.verifiedInfos - factsBeforeResult.verifiedInfos
//        val newUnverifiedInfos = newVerifiedInfos.map { UnverifiedInfo(conditionExitNode.condition, it.dataFlowInfo) }
//        addEdge(lastResultNode, resultExitNode, addInfo = false)
//        addEdge(resultExitNode, whenExitNode, addInfo = false)
//        val facts = factsBeforeResult + newUnverifiedInfos + factsFromResult.unverifiedInfos
//        resultExitNode.facts = facts
    }

    fun exitWhenExpression(whenExpression: FirWhenExpression) {
        // exit from last condition node still on stack
        // we should remove it
        require(lastNodes.pop() is WhenBranchConditionExitNode)
        val whenExitNode = whenExitNodes.pop()
        intersectFactsFromPreviousNodes(whenExitNode)
        lastNodes.push(whenExitNode)
    }

    // ----------------------------------- While Loop -----------------------------------

    fun enterWhileLoop(loop: FirLoop) {
        addNewSimpleNode(graph.createLoopEnterNode(loop))
        val node = graph.createLoopConditionEnterNode(loop)
        addNewSimpleNode(node)
        // put conditional node twice so we can refer it after exit from loop block
        lastNodes.push(node)
        loopExitNodes.push(graph.createLoopExitNode(loop))
    }

    fun exitWhileLoopCondition(loop: FirLoop) {
        val conditionExitNode = graph.createLoopConditionExitNode(loop)
        addNewSimpleNode(conditionExitNode)
        addEdge(conditionExitNode, loopExitNodes.top())
        addNewSimpleNode(graph.createLoopBlockEnterNode(loop))
    }

    fun exitWhileLoop(loop: FirLoop) {
        val loopBlockExitNode = graph.createLoopBlockExitNode(loop)
        addEdge(lastNodes.pop(), loopBlockExitNode)
        val conditionEnterNode = lastNodes.pop()
        require(conditionEnterNode is LoopConditionEnterNode)
        addEdge(loopBlockExitNode, conditionEnterNode)
        lastNodes.push(loopExitNodes.pop())
    }

    // ----------------------------------- Do while Loop -----------------------------------

    fun enterDoWhileLoop(loop: FirLoop) {
        addNewSimpleNode(graph.createLoopEnterNode(loop))
        val blockEnterNode = graph.createLoopBlockEnterNode(loop)
        addNewSimpleNode(blockEnterNode)
        // put block enter node twice so we can refer it after exit from loop condition
        lastNodes.push(blockEnterNode)
        loopExitNodes.push(graph.createLoopExitNode(loop))
    }

    fun enterDoWhileLoopCondition(loop: FirLoop) {
        addNewSimpleNode(graph.createLoopBlockExitNode(loop))
        addNewSimpleNode(graph.createLoopConditionEnterNode(loop))
    }

    fun exitDoWhileLoop(loop: FirLoop) {
        val conditionExitNode = graph.createLoopConditionExitNode(loop)
        addEdge(lastNodes.pop(), conditionExitNode)
        val blockEnterNode = lastNodes.pop()
        require(blockEnterNode is LoopBlockEnterNode)
        addEdge(conditionExitNode, blockEnterNode)
        val loopExit = loopExitNodes.pop()
        addEdge(conditionExitNode, loopExit)
        lastNodes.push(loopExit)
    }

    // ----------------------------------- Resolvable call -----------------------------------

    // -------------------------------------------------------------------------------------------------------------------------

    private fun addNewSimpleNodeWithoutExtractingFromStack(node: CFGNode<*>): CFGNode<*> {
        return connectNodes(lastNodes.top(), node)
    }

    private fun connectNodes(oldNode: CFGNode<*>, newNode: CFGNode<*>): CFGNode<*> {
        addEdge(oldNode, newNode)
        lastNodes.push(newNode)
        return oldNode
    }

    private fun addNewSimpleNode(node: CFGNode<*>): CFGNode<*> {
        return connectNodes(lastNodes.pop(), node)
    }

    private fun addEdge(from: CFGNode<*>, to: CFGNode<*>, addInfo: Boolean = true) {
        from.followingNodes += to
        to.previousNodes += from
        if (addInfo) to.facts = outputEdges[from] ?: from.facts
    }

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