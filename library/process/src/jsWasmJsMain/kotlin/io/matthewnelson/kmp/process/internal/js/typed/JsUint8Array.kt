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
@file:OptIn(DoNotReferenceDirectly::class)
@file:Suppress("NOTHING_TO_INLINE")

package io.matthewnelson.kmp.process.internal.js.typed

import io.matthewnelson.kmp.process.internal.DoNotReferenceDirectly
import io.matthewnelson.kmp.process.internal.js.buffer.JsArrayBufferLike
import kotlin.js.JsName

@JsName("Uint8Array")
internal open external class JsUint8Array: JsTypedArray {
    override val buffer: JsArrayBufferLike
    override val byteLength: Int
    override val byteOffset: Int
    override val length: Int
    override fun slice(start: Int, end: Int): JsUint8Array
    internal companion object
}

internal inline fun JsUint8Array.Companion.new(length: Int): JsUint8Array = jsUint8Array(length)
internal inline fun JsUint8Array.Companion.new(buffer: JsArrayBufferLike): JsUint8Array = jsUint8Array(buffer)

internal inline operator fun JsUint8Array.get(index: Int): UByte = jsGet(this, index).toUByte()
internal inline operator fun JsUint8Array.set(index: Int, value: UByte) { jsSet(this, index, value.toByte()) }

internal const val CODE_JS_NEW_UINT8_LENGTH = "new Uint8Array(length)"
internal const val CODE_JS_NEW_UINT8_BUFFER = "new Uint8Array(buffer)"

@DoNotReferenceDirectly("JsUint8Array.Companion.new(length)")
internal expect fun jsUint8Array(length: Int): JsUint8Array
@DoNotReferenceDirectly("JsUint8Array.Companion.new(buffer)")
internal expect fun jsUint8Array(buffer: JsArrayBufferLike): JsUint8Array

@DoNotReferenceDirectly("array[index]")
internal expect fun jsGet(array: JsUint8Array, index: Int): Short
@DoNotReferenceDirectly("array[index] = value")
internal expect fun jsSet(array: JsUint8Array, index: Int, value: Byte)
