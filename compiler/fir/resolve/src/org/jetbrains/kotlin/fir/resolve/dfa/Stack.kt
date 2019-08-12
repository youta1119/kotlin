/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.resolve.dfa

import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.CFGNode

interface Stack<T> {
    val size: Int
    fun top(): T
    fun pop(): T
    fun push(value: T)
}

fun <T> stackOf(vararg values: T): Stack<T> = StackImpl(*values)

val Stack<*>.isEmpty: Boolean get() = size == 0
fun <T> Stack<T>.topOrNull(): T? = if (size == 0) null else top()

private class StackImpl<T>(vararg values: T) : Stack<T> {
    private val stack = mutableListOf(*values)

    override fun top(): T = stack[stack.size - 1]
    override fun pop(): T = stack.removeAt(stack.size - 1)
    override fun push(value: T) {
        stack.add(value)
    }

    override val size: Int get() = stack.size
}

class NodeStorage<T : FirElement, N : CFGNode<T>> : Stack<N> {
    private val stack: Stack<N> = stackOf()
    private val map: MutableMap<T, N> = mutableMapOf()

    override val size: Int get() = stack.size

    override fun top(): N = stack.top()

    override fun pop(): N = stack.pop().also {
        map.remove(it.fir)
    }

    override fun push(value: N) {
        stack.push(value)
        map[value.fir] = value
    }

    operator fun get(key: T): N {
        return map[key]!!
    }
}

fun <T : FirElement, N : CFGNode<T>> nodeStorageOf(vararg values: N): NodeStorage<T, N> = NodeStorage<T, N>().apply {
    values.forEach(this::push)
}