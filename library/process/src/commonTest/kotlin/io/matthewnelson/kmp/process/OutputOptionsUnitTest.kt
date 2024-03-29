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
        val o = Output.Options.Builder.build {
            timeoutMillis = 2
            maxBuffer = 5
        }

        assertEquals(250.milliseconds, o.timeout)
        assertEquals(1024 * 16, o.maxBuffer)
    }

    @Test
    fun givenOptions_whenInputConsumed_thenDereferencesCallback() {
        val o = Output.Options.Builder.build {
            input { "Hello".encodeToByteArray() }
        }

        assertTrue(o.hasInput)
        assertNotNull(o.consumeInput())
        assertNull(o.consumeInput())
    }
}
