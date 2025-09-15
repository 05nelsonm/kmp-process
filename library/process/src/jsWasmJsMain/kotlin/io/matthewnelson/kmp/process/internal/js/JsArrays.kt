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
@file:OptIn(ExperimentalWasmJsInterop::class)
@file:Suppress("NOTHING_TO_INLINE", "UNUSED")

package io.matthewnelson.kmp.process.internal.js

import io.matthewnelson.kmp.process.internal.checkBounds
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsName
import kotlin.js.js

@JsName("Array")
internal external class JsArray: JsAny {
    internal val length: Int
    internal companion object {
        internal fun of(size: Int): JsArray
    }
}

internal inline fun JsArray.getString(index: Int): String = jsArrayGetString(this, index)
internal fun jsArrayGetString(array: JsArray, index: Int): String = js("array[index]")

internal inline operator fun JsArray.set(index: Int, value: String) { jsArraySetString(this, index, value) }
internal fun jsArraySetString(array: JsArray, index: Int, value: String) { js("array[index] = value") }

internal inline operator fun JsArray.set(index: Int, value: Double) { jsArraySetDouble(this, index, value) }
internal fun jsArraySetDouble(array: JsArray, index: Int, value: Double) { js("array[index] = value") }

@JsName("ArrayBufferView")
internal external interface JsArrayBufferView: JsAny {
    val byteLength: Int
}

@JsName("Int8Array")
internal open external class JsInt8Array(length: Int): JsArrayBufferView {
    override val byteLength: Int
}

@JsName("Uint8Array")
internal open external class JsUint8Array(length: Int): JsArrayBufferView {
    override val byteLength: Int
}

internal inline operator fun <T: JsArrayBufferView> T.set(index: Int, value: Byte) { jsArraySet(this, index, value) }
internal fun <T: JsArrayBufferView> jsArraySet(array: T, index: Int, value: Byte) { js("array[index] = value") }

@OptIn(ExperimentalContracts::class)
// @Throws(IndexOutOfBoundsException::class)
internal inline fun <T: JsArrayBufferView> ByteArray.toJsArray(
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

    var i = 0
    for (j in offset until offset + len) {
        array[i++] = this[j]
    }

    return array
}

internal inline fun JsArrayBufferView.fill() {
    val len = byteLength
    if (len == 0) return
    for (i in 0 until len) {
        this[i] = 0
    }
}
