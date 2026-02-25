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

internal expect class Bit8ArrayType

internal expect value class Bit8Array internal constructor(internal val storage: Bit8ArrayType) {

    internal constructor(size: Int)

    internal inline operator fun get(index: Int): Byte
    internal inline operator fun set(index: Int, value: Byte)

    internal inline fun size(): Int
    internal inline fun indices(): IntRange
    internal inline operator fun iterator(): ByteIterator

    internal inline fun copyInto(
        dest: ByteArray,
        destOffset: Int,
        indexStart: Int,
        indexEnd: Int,
        // Only used for Js/WasmJs, as Jvm/Native ByteArray.copyInto does this already.
        checkBounds: Boolean,
    ): ByteArray

    internal inline fun copyOf(newSize: Int): Bit8Array
}

internal expect inline fun Bit8Array(size: Int, init: (i: Int) -> Byte): Bit8Array
