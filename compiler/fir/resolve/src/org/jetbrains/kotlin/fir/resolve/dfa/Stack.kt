/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.resolve.dfa

class Stack<T>(vararg values: T) {
    private val stack = mutableListOf(*values)

    fun top(): T = stack[stack.size - 1]
    fun pop(): T = stack.removeAt(stack.size - 1)
    fun push(value: T) = stack.add(value)

    val isEmpty: Boolean get() = stack.isEmpty()

    val size: Int get() = stack.size
}

fun <T> Stack<T>.topOrNull(): T? = if (size == 0) null else top()