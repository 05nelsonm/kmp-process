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
package io.matthewnelson.kmp.process.internal

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class InstanceUnitTest {

    @Test
    fun givenNullableType_whenGetOrCreate_thenReturnsAsExpected() {
        val i = Instance<Any?> { Any() }

        assertNull(i.getOrNull())
        assertNotNull(i.getOrCreate())
        assertNotNull(i.getOrNull())
    }
}
