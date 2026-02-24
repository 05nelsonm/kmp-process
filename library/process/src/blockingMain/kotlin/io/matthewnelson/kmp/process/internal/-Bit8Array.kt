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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "NOTHING_TO_INLINE", "ReplaceGetOrSet")

package io.matthewnelson.kmp.process.internal

import kotlin.jvm.JvmInline

@Suppress("ACTUAL_WITHOUT_EXPECT")
internal actual typealias Bit8ArrayType = ByteArray

@JvmInline
internal actual value class Bit8Array internal actual constructor(internal actual val storage: Bit8ArrayType) {

    internal actual constructor(size: Int): this(Bit8ArrayType(size))

    internal actual inline operator fun get(index: Int): Byte = storage[index]
    internal actual inline operator fun set(index: Int, value: Byte) { storage[index] = value }

    internal actual inline fun size(): Int = storage.size
    internal actual inline fun indices(): IntRange = storage.indices
    internal actual inline operator fun iterator(): ByteIterator = storage.iterator()

    internal actual inline fun copyInto(
        dest: ByteArray,
        destOffset: Int,
        indexStart: Int,
        indexEnd: Int,
        // Only used for Js/WasmJs, as Jvm/Native ByteArray does this already.
        checkBounds: Boolean,
    ): ByteArray = storage.copyInto(dest, destOffset, indexStart, indexEnd)

    internal actual inline fun copyOf(newSize: Int): Bit8Array = Bit8Array(storage.copyOf(newSize))
}

internal actual inline fun Bit8Array(size: Int, init: (i: Int) -> Byte): Bit8Array {
    return Bit8Array(ByteArray(size) { i -> init(i) })
}
