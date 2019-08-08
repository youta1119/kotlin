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
import org.jetbrains.kotlin.fir.symbols.ConeSymbol
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.types.*

class FirDataFlowAnalyzer(transformer: FirBodyResolveTransformer) : BodyResolveComponents by transformer {
    private val context: ConeTypeContext get() = inferenceComponents.ctx as ConeTypeContext
    private val factSystem: FactSystem = FactSystem(context)

    private lateinit var graph: ControlFlowGraph

    private val lexicalScopes: Stack<Stack<CFGNode>> = Stack()
    private val lastNodes: Stack<CFGNode> get() = lexicalScopes.top()
    private val whenExitNodes: Stack<WhenExitNode> = Stack()
    private val blockExitNodes: Stack<BlockExitNode> = Stack()
    private val whenConditionExitNodes: Stack<ConditionExitNode> = Stack()

    private val modes: Stack<Mode> = Stack()
    private val mode: Mode get() = modes.top()

    private val variableStorage = DataFlowVariableStorage()
    private val edges = mutableMapOf<CFGNode, Facts>().withDefault { Facts.EMPTY }
    private val outputEdges = mutableMapOf<CFGNode, Facts>()

    private val conditionVariables: Stack<DataFlowVariable> = Stack()

    fun getTypeUsingSmartcastInfo(qualifiedAccessExpression: FirQualifiedAccessExpression): ConeKotlinType? {
        val lastNode = lastNodes.top()
        val fir = (((qualifiedAccessExpression.calleeReference as? FirResolvedCallableReference)?.coneSymbol) as? FirBasedSymbol<*>)?.fir ?: return null
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
        graph = ControlFlowGraph()
        lexicalScopes.push(Stack(graph.createStartNode(namedFunction)))
        graph.createExitNode(namedFunction)
        modes.push(Mode.NoMode)
        for (valueParameter in namedFunction.valueParameters) {
            variableStorage.createNewRealVariable(valueParameter)
        }
    }

    fun exitNamedFunction(namedFunction: FirNamedFunction) {
        lastNodes.pop()
        modes.pop()
    }

    // ----------------------------------- Block -----------------------------------

    fun enterBlock(block: FirBlock) {
        val enterBlockNode = graph.createEnterBlockNode(block)
        val lastNode = lastNodes.top()
        val edgeFromCondition = lastNode is ConditionExitNode
        addEdge(lastNode, enterBlockNode, addInfo = !edgeFromCondition)
        if (edgeFromCondition) {
            val trueCondition = (lastNode as ConditionExitNode).condition
            enterBlockNode.facts = factSystem.verifyFacts(lastNode.facts, trueCondition, lexicalScopes.size)
        }
        lexicalScopes.push(Stack(enterBlockNode))
        blockExitNodes.push(graph.createExitBlockNode(block))
    }

    fun exitBlock(block: FirBlock) {
        if (lastNodes.isEmpty) {
            lexicalScopes.pop()
            return
        }

        val blockExitNode = blockExitNodes.pop()
        val lastNode = lastNodes.pop()
        addEdge(lastNode, blockExitNode, addInfo = false)

        intersectFactsFromPreviousNodes(blockExitNode)

        lexicalScopes.pop()

        val nextNode = when (mode) {
            Mode.NoMode -> graph.exitNode
            Mode.When -> {
                lastNodes.push(blockExitNode)
                return
            }
            Mode.Condition -> TODO()
        }
        addEdge(blockExitNode, nextNode)
    }

    // ----------------------------------- Type operator call -----------------------------------

    fun exitTypeOperatorCall(typeOperatorCall: FirTypeOperatorCall) {
        val node = graph.createTypeOperatorCallNode(typeOperatorCall)
        addNewSimpleNode(node)
        if (typeOperatorCall.operation == FirOperation.IS) {
            val symbol = typeOperatorCall.argument.toResolvedCallableSymbol() as? FirBasedSymbol<*> ?: return
            val type = typeOperatorCall.conversionTypeRef.coneTypeSafe<ConeKotlinType>() ?: return

            val variable = variableStorage[symbol.fir] ?: return
            val conditionalVariable = conditionVariables.top()
            val info = UnverifiedInfo(
                BooleanCondition(conditionalVariable, true),
                FirDataFlowInfo(variable, setOf(type), emptySet())
            )
            outputEdges[node] = node.facts + info
        }
    }

    // ----------------------------------- Jump -----------------------------------

    fun exitJump(jump: FirJump<*>) {
        addNewSimpleNode(graph.createJumpNode(jump))
        addEdge(lastNodes.pop(), graph.exitNode)
    }

    // ----------------------------------- When -----------------------------------

    fun enterWhenExpression(whenExpression: FirWhenExpression) {
        addNewSimpleNode(graph.createWhenNode(whenExpression))
        whenExitNodes.push(graph.createWhenExitNode(whenExpression))
        modes.push(Mode.When)
    }

    fun enterWhenBranch(whenBranch: FirWhenBranch) {
        addNewSimpleNodeWithoutExtractingFromStack(graph.createConditionNode(whenBranch))
        conditionVariables.push(variableStorage.createNewSyntheticVariable(whenBranch.condition))
        modes.push(Mode.Condition)
    }

    fun exitWhenBranchCondition(whenBranch: FirWhenBranch) {
        modes.pop()

        val conditionVariable = conditionVariables.pop()
        val trueCondition = BooleanCondition(conditionVariable, true)

        val node = graph.createConditionExitNode(whenBranch, trueCondition)
        addNewSimpleNode(node)
        whenConditionExitNodes.push(node)
    }

    fun exitWhenBranchResult(whenBranch: FirWhenBranch) {
        val conditionExitNode = whenConditionExitNodes.pop()
        if (lastNodes.top() !is BlockExitNode) return
        val whenExitNode = whenExitNodes.top()
        val lastResultNode = lastNodes.pop()
        val resultExitNode = graph.createWhenBranchResultExitNode(whenBranch)

        val factsBeforeResult = conditionExitNode.facts
        val factsFromResult = lastResultNode.facts
        val newVerifiedInfos = factsFromResult.verifiedInfos - factsBeforeResult.verifiedInfos
        val newUnverifiedInfos = newVerifiedInfos.map { UnverifiedInfo(conditionExitNode.condition, it.dataFlowInfo) }
        addEdge(lastResultNode, resultExitNode, addInfo = false)
        addEdge(resultExitNode, whenExitNode, addInfo = false)
        val facts = factsBeforeResult + newUnverifiedInfos + factsFromResult.unverifiedInfos
        resultExitNode.facts = facts
    }

    fun exitWhenExpression(whenExpression: FirWhenExpression) {
        val whenExitNode = whenExitNodes.pop()
        intersectFactsFromPreviousNodes(whenExitNode)
        lastNodes.push(whenExitNode)
        modes.pop()
    }

    // -------------------------------------------------------------------------------------------------------------------------

    private fun addNewSimpleNodeWithoutExtractingFromStack(node: CFGNode): CFGNode {
        return connectNodes(lastNodes.top(), node)
    }

    private fun connectNodes(oldNode: CFGNode, newNode: CFGNode): CFGNode {
        addEdge(oldNode, newNode)
        lastNodes.push(newNode)
        return oldNode
    }

    private fun addNewSimpleNode(node: CFGNode): CFGNode {
        return connectNodes(lastNodes.pop(), node)
    }

    private fun addEdge(from: CFGNode, to: CFGNode, addInfo: Boolean = true) {
        from.followingNodes += to
        to.previousNodes += from
        if (addInfo) to.facts = outputEdges[from] ?: from.facts
    }

    private fun intersectFactsFromPreviousNodes(node: CFGNode) {
        node.facts = factSystem.foldFacts(node.previousNodes.map { outputEdges[it] ?: it.facts })
    }

    private var CFGNode.facts: Facts
        get() = edges.getValue(this)
        set(value) {
            edges[this] = value
        }

    private val CFGNode.verifiedInfos: Collection<VerifiedInfo>
        get() = facts.verifiedInfos

    private val CFGNode.previousFacts: List<Facts> get() = previousNodes.map { it.facts }
}

enum class Mode {
    NoMode, When, Condition
}