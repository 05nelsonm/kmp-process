/*
 * Copyright (c) 2025 Matthew Nelson
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
@file:Suppress("NOTHING_TO_INLINE", "UNUSED")

package io.matthewnelson.kmp.process.internal

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

internal external interface ArrayBufferView {
    val byteLength: Int
}

internal open external class Int8Array(length: Int): ArrayBufferView {
    override val byteLength: Int
}

internal open external class Uint8Array(length: Int): ArrayBufferView {
    override val byteLength: Int
}

@OptIn(ExperimentalContracts::class)
// @Throws(IndexOutOfBoundsException::class)
internal inline fun <T: ArrayBufferView> ByteArray.toJsArray(
    offset: Int = 0,
    len: Int = size - offset,
    checkBounds: Boolean = false,
    factory: (size: Int) -> T,
): T {
    contract {
        callsInPlace(factory, InvocationKind.AT_MOST_ONCE)
    }

    if (checkBounds) checkBounds(offset, len)
    val array = factory(len)
    val dArray = array.asDynamic()

    var aI = 0
    for (i in offset until offset + len) {
        dArray[aI++] = this[i]
    }

    return array
}

internal inline fun ArrayBufferView.fill() {
    val len = byteLength
    if (len == 0) return
    val a = asDynamic()
    for (i in 0 until len) {
        a[i] = 0
    }
}
