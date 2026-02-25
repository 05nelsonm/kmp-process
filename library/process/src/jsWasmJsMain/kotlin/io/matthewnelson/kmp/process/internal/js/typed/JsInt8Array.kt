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

@JsName("Int8Array")
internal open external class JsInt8Array: JsTypedArray {
    override val buffer: JsArrayBufferLike
    override val byteLength: Int
    override val byteOffset: Int
    override val length: Int
    override fun slice(start: Int, end: Int): JsInt8Array
    override fun subarray(start: Int, end: Int): JsInt8Array
    internal companion object
}

internal inline fun JsInt8Array.Companion.new(length: Int): JsInt8Array = jsInt8Array(length)
internal inline fun JsInt8Array.Companion.new(buffer: JsArrayBufferLike): JsInt8Array = jsInt8Array(buffer)

internal inline operator fun JsInt8Array.get(index: Int): Byte = jsGet(this, index)
internal inline operator fun JsInt8Array.set(index: Int, value: Byte) { jsSet(this, index, value) }

internal inline fun JsInt8Array.asJsUint8Array(): JsUint8Array = JsUint8Array.new(buffer)

internal const val CODE_JS_NEW_INT8_LENGTH = "new Int8Array(length)"
internal const val CODE_JS_NEW_INT8_BUFFER = "new Int8Array(buffer)"

@DoNotReferenceDirectly("JsInt8Array.Companion.new(length)")
internal expect fun jsInt8Array(length: Int): JsInt8Array
@DoNotReferenceDirectly("JsInt8Array.Companion.new(buffer)")
internal expect fun jsInt8Array(buffer: JsArrayBufferLike): JsInt8Array

@DoNotReferenceDirectly("array[index]")
internal expect fun jsGet(array: JsInt8Array, index: Int): Byte
@DoNotReferenceDirectly("array[index] = value")
internal expect fun jsSet(array: JsInt8Array, index: Int, value: Byte)
