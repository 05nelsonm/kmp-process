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

import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

class OutputOptionsUnitTest {

    @Test
    fun givenBuilder_whenOptionsLessThanMin_thenOptionsUseMinValues() {
        val o = Output.Options.Builder.get().apply {
            timeoutMillis = 2
            maxBuffer = 5
        }.build()

        assertEquals(250.milliseconds, o.timeout)
        assertEquals(1024 * 16, o.maxBuffer)
        assertFalse(o.hasInput)
        assertNull(o.consumeInputBytes())
        assertNull(o.consumeInputUtf8())
    }

    @Test
    fun givenOptions_whenInputBytesConsumed_thenDereferencesCallback() {
        val o = Output.Options.Builder.get().apply {
            inputUtf8 { "Hello" }
            input { "Hello".encodeToByteArray() }
        }.build()

        assertTrue(o.hasInput)
        assertNull(o.consumeInputUtf8())
        assertNotNull(o.consumeInputBytes())
        assertNull(o.consumeInputBytes())
    }

    @Test
    fun givenOptions_whenInputUtf8Consumed_thenDereferencesCallback() {
        val o = Output.Options.Builder.get().apply {
            input { "Hello".encodeToByteArray() }
            inputUtf8 { "Hello" }
        }.build()

        assertTrue(o.hasInput)
        assertNull(o.consumeInputBytes())
        assertNotNull(o.consumeInputUtf8())
        assertNull(o.consumeInputUtf8())
    }
}
