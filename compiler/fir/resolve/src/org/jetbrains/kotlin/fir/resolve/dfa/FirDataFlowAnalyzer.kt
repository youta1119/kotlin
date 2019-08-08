/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.resolve.dfa

import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirResolvedCallableReference
import org.jetbrains.kotlin.fir.FirTargetElement
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.resolve.BodyResolveComponents
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.*
import org.jetbrains.kotlin.fir.resolve.transformers.FirBodyResolveTransformer
import org.jetbrains.kotlin.fir.symbols.ConeSymbol
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid
import org.jetbrains.kotlin.resolve.calls.NewCommonSuperTypeCalculator

typealias DataFlowInfoMap = MutableMap<ConeSymbol, FirDataFlowInfo>

data class FirDataFlowInfo(
    val exactTypes: MutableList<ConeKotlinType> = mutableListOf(),
    val exactNotTypes: MutableList<ConeKotlinType> = mutableListOf()
)

class FirDataFlowAnalyzer(transformer: FirBodyResolveTransformer) : BodyResolveComponents by transformer {
    private val context: ConeTypeContext get() = inferenceComponents.ctx as ConeTypeContext

    private var graph = ControlFlowGraph()

    private val lexicalScopes: Stack<Stack<CFGNode>> = Stack()
    private val lastNodes: Stack<CFGNode> get() = lexicalScopes.top()
    private val whenExitNodes: Stack<WhenExitNode> = Stack()
    private val blockExitNodes: Stack<BlockExitNode> = Stack()
    private val modes: Stack<Mode> = Stack()
    private val mode: Mode get() = modes.top()

    private val enterVisitor = EnterVisitor()
    private val exitVisitor = ExitVisitor()

    private val edgesMap = mutableMapOf<CFGNode, MutableMap<ConeSymbol, FirDataFlowInfo>>().withDefault { mutableMapOf() }

    fun enterFirNode(element: FirElement) = element.accept(enterVisitor)
    fun exitFirNode(element: FirElement) = element.accept(exitVisitor)

    fun getTypeUsingSmartcastInfo(qualifiedAccessExpression: FirQualifiedAccessExpression): ConeKotlinType? {
        val lastNode = lastNodes.top()
        val symbol = (qualifiedAccessExpression.calleeReference as? FirResolvedCallableReference)?.coneSymbol ?: return null
        val dfi = edgesMap[lastNode]?.get(symbol) ?: return null
        val originalType = qualifiedAccessExpression.typeRef.coneTypeSafe<ConeKotlinType>() ?: return null
        val types = dfi.exactTypes.takeIf { it.isNotEmpty() } ?: return null
        return ConeTypeIntersector.intersectTypesFromSmartcasts(context, originalType, types)
    }

    private inner class EnterVisitor : FirVisitorVoid() {
        override fun visitElement(element: FirElement) {
            throw IllegalStateException()
        }

        override fun visitNamedFunction(namedFunction: FirNamedFunction) {
            lexicalScopes.push(Stack(graph.createStartNode(namedFunction)))
            graph.createExitNode(namedFunction)
            modes.push(Mode.NoMode)
        }

        override fun visitWhenExpression(whenExpression: FirWhenExpression) {
            addNewSimpleNode(graph.createWhenNode(whenExpression))
            whenExitNodes.push(graph.createWhenExitNode(whenExpression))
            modes.push(Mode.When)
        }

        override fun visitWhenBranch(whenBranch: FirWhenBranch) {
            addNewSimpleNodeWithoutExtractingFromStack(graph.createConditionNode(whenBranch))
            modes.push(Mode.Condition)
        }

        override fun visitBlock(block: FirBlock) {
            val enterBlockNode = graph.createEnterBlockNode(block)
            val lastNode = lastNodes.top()
            addEdge(lastNode, enterBlockNode)
            if (lastNode is ConditionExitNode) {
                enterBlockNode.incomingInfo = lastNode.outDataFlowInfo
            }
            lexicalScopes.push(Stack(enterBlockNode))
            blockExitNodes.push(graph.createExitBlockNode(block))
        }
    }

    private inner class ExitVisitor : FirVisitorVoid() {
        override fun visitElement(element: FirElement) {
            throw IllegalStateException()
        }

        override fun visitNamedFunction(namedFunction: FirNamedFunction) {
            lastNodes.pop()
            modes.pop()
        }

        override fun visitWhenExpression(whenExpression: FirWhenExpression) {
            val whenExitNode = whenExitNodes.pop()
            intersectIncomingData(whenExitNode)
            lastNodes.push(whenExitNode)
            modes.pop()
        }

        override fun visitWhenBranch(whenBranch: FirWhenBranch) {
            modes.pop()
            val lastConditionNode = lastNodes.pop()
            val dfi = lastConditionNode.incomingInfo

            addNewSimpleNode(graph.createConditionExitNode(whenBranch, dfi))
        }

        override fun visitBlock(block: FirBlock) {
            if (lastNodes.isEmpty) {
                lexicalScopes.pop()
                return
            }

            val blockExitNode = blockExitNodes.pop()
            addEdge(lastNodes.pop(), blockExitNode)
            lexicalScopes.pop()

            val nextNode = when (mode) {
                Mode.NoMode -> graph.exitNode
                Mode.When -> whenExitNodes.top()
                Mode.Condition -> TODO()
            }
            addEdge(blockExitNode, nextNode)
        }

        override fun visitTypeOperatorCall(typeOperatorCall: FirTypeOperatorCall) {
            val node = graph.createTypeOperatorCallNode(typeOperatorCall)
            addNewSimpleNode(node)
            if (mode != Mode.Condition) return
            if (typeOperatorCall.operation == FirOperation.IS) {
                val symbol = typeOperatorCall.argument.toResolvedCallableSymbol() ?: return
                val type = typeOperatorCall.conversionTypeRef.coneTypeSafe<ConeKotlinType>() ?: return
                val dataFlowInfoMap = node.incomingInfo
                val dataFlowInfo = dataFlowInfoMap[symbol] ?: FirDataFlowInfo().also { dataFlowInfoMap[symbol] = it }
                dataFlowInfo.exactTypes += type
            }
        }

        override fun <E : FirTargetElement> visitJump(jump: FirJump<E>) {
            addNewSimpleNode(graph.createJumpNode(jump))
            addEdge(lastNodes.pop(), graph.exitNode)
        }
    }

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

    private fun addEdge(from: CFGNode, to: CFGNode) {
        from.followingNodes += to
        to.previousNodes += from
        to.incomingInfo = from.incomingInfo
    }

    private fun intersectIncomingData(node: CFGNode) {
        edgesMap[node] = node.previousNodes.singleOrNull()?.incomingInfo
            ?: node.previousNodes.map { it.incomingInfo }
                .reduce { a, b -> a.or(b) }
    }

    private infix fun FirDataFlowInfo.or(other: FirDataFlowInfo): FirDataFlowInfo {
        fun intersectTypes(types: List<ConeKotlinType>) = ConeTypeIntersector.intersectTypes(context, types)

        fun calculateTypes(a: List<ConeKotlinType>, b: List<ConeKotlinType>): MutableList<ConeKotlinType> =
            if (a.isEmpty() || b.isEmpty()) {
                mutableListOf()
            } else {
                val commonSuperType = with(NewCommonSuperTypeCalculator) {
                    with(inferenceComponents.ctx) {
                        commonSuperType(listOf(intersectTypes(exactTypes), intersectTypes(b)))
                    }
                } as ConeKotlinType

                if (commonSuperType is ConeIntersectionType) commonSuperType.intersectedTypes.toMutableList()
                else mutableListOf(commonSuperType)
            }

        val exactTypes = calculateTypes(this.exactTypes, other.exactTypes)
        val exactNotTypes = calculateTypes(this.exactNotTypes, other.exactNotTypes)
        return FirDataFlowInfo(exactTypes, exactNotTypes)
    }

    fun DataFlowInfoMap.or(other: DataFlowInfoMap): DataFlowInfoMap {
        if (this.isEmpty() || other.isEmpty()) return mutableMapOf()
        val keys = this.keys.intersect(other.keys)
        return keys.associateByTo(mutableMapOf(), { it }) {
            val a = this[it]!!
            val b = other[it]!!
            a.or(b)
        }
    }

    private var CFGNode.incomingInfo: DataFlowInfoMap
        get() = edgesMap.getValue(this)
        set(value) {
            edgesMap[this] = value.toMutableMap()
        }
}

enum class Mode {
    NoMode, When, Condition
}

class Stack<T>(vararg values: T) {
    private val stack = mutableListOf(*values)

    fun top(): T = stack[stack.size - 1]
    fun pop(): T = stack.removeAt(stack.size - 1)
    fun push(value: T) = stack.add(value)

    val isEmpty: Boolean get() = stack.isEmpty()

    val size: Int get() = stack.size
}