/*
 * Copyright (c) 2026 Matthew Nelson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "NOTHING_TO_INLINE")

package io.matthewnelson.kmp.process.internal

import io.matthewnelson.kmp.process.internal.js.typed.JsInt8Array
import io.matthewnelson.kmp.process.internal.js.typed.new
import io.matthewnelson.kmp.process.internal.js.typed.get
import io.matthewnelson.kmp.process.internal.js.typed.set

internal actual typealias Bit8ArrayType = JsInt8Array

internal actual value class Bit8Array internal actual constructor(internal actual val storage: Bit8ArrayType) {

    internal actual constructor(size: Int): this(Bit8ArrayType.new(size))

    internal actual inline operator fun get(index: Int): Byte = storage[index]
    internal actual inline operator fun set(index: Int, value: Byte) { storage[index] = value }

    internal actual inline fun size(): Int = storage.length
    internal actual inline fun indices(): IntRange = IntRange(0, size() - 1)
    internal actual inline operator fun iterator(): ByteIterator = object : ByteIterator() {
        private var i = 0
        override fun hasNext(): Boolean = i < size()
        override fun nextByte(): Byte = if (i < size()) storage[i++]
        else throw NoSuchElementException("Index $i out of bounds for size ${size()}")
    }

    internal actual inline fun copyInto(
        dest: ByteArray,
        destOffset: Int,
        indexStart: Int,
        indexEnd: Int,
        checkBounds: Boolean
    ): ByteArray {
        if (checkBounds) size().checkCopyBounds(dest.size, destOffset, indexStart, indexEnd)

        var j = destOffset
        for (i in indexStart until indexEnd) {
            dest[j++] = storage[i]
        }
        return dest
    }

    internal actual inline fun copyOf(newSize: Int): Bit8Array = if (newSize <= size()) {
        Bit8Array(storage.slice(start = 0, end = newSize))
    } else {
        val a = Bit8Array(newSize)
        repeat(size()) { i -> a[i] = this[i] }
        a
    }
}

internal actual inline fun Bit8Array(size: Int, init: (i: Int) -> Byte): Bit8Array {
    return Bit8Array(size).apply { repeat(size) { i -> this[i] = init(i) } }
}
