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

package io.matthewnelson.kmp.process.internal.js

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.js
import kotlin.test.Test
import kotlin.test.assertEquals

private fun <T: JsArrayBufferView> jsArrayGet(a: T, index: Int): Byte = js("a[index]")

class JsArrayUnitTest {

    @Test
    fun givenBytes_whenToJsArray_thenBytesAreCopied() {
        val b = ByteArray(50) { i -> i.toByte() }
        val a = b.toJsArray(factory = ::JsInt8Array)
        for (i in b.indices) {
            assertEquals(b[i], jsArrayGet(a, i))
        }
    }

    @Test
    fun givenBytes_whenToJsArrayWithOffset_thenBytesAreCopied() {
        val b = ByteArray(250) { i -> i.toByte() }
        val offset = 50
        val a = b.toJsArray(offset = offset, factory = ::JsInt8Array)

        assertEquals(b.size - offset, a.byteLength)

        for (i in 0 until a.byteLength) {
            assertEquals(b[i + offset], jsArrayGet(a, i))
        }
    }

    @Test
    fun givenJsArray_whenFill_thenArrayIsZeroedOut() {
        val a = ByteArray(250) { i -> i.toByte() }.toJsArray(factory = ::JsUint8Array)
        a.fill()
        for (i in 0 until a.byteLength) {
            assertEquals(0, jsArrayGet(a, i))
        }
    }
}
