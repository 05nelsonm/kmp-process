/*
 * Copyright (c) 2024 Matthew Nelson
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
package io.matthewnelson.kmp.process

import io.matthewnelson.kmp.process.internal.*
import kotlin.test.Test
import kotlin.test.assertEquals

class JsArrayUnitTest {

    @Test
    fun givenBytes_whenToJsArray_thenBytesAreCopied() {
        val b = ByteArray(50) { it.toByte() }
        val a = b.toJsArray { Int8Array(it) }
        for (i in b.indices) {
            assertEquals(b[i], a.asDynamic()[i])
        }
    }

    @Test
    fun givenBytes_whenToJsArrayWithOffset_thenBytesAreCopied() {
        val b = ByteArray(250) { it.toByte() }
        val offset = 50
        val a = b.toJsArray(offset = offset) { Int8Array(it) }

        assertEquals(b.size - offset, a.byteLength)

        for (i in 0 until a.byteLength) {
            assertEquals(b[i + offset], a.asDynamic()[i])
        }
    }

    @Test
    fun givenJsArray_whenFill_thenArrayIsZeroedOut() {
        val a = ByteArray(250) { it.toByte() }.toJsArray { Uint8Array(it) }
        a.fill()
        for (i in 0 until a.byteLength) {
            assertEquals(0, a.asDynamic()[i])
        }
    }
}
