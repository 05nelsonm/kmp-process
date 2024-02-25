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

import io.matthewnelson.kmp.process.internal.spawn.GnuLibcVersion
import kotlin.test.*

class GnuLibcVersionUnitTest {

    @Test
    fun givenCheck_whenCalled_thenLambdaIsActuallyInvoked() {
        // need to ensure that on Linux, the lambda is actually invoked
        // with the version class.
        var invocations = 0
        GnuLibcVersion.check { invocations++; println(this) }
        assertEquals(1, invocations)
    }

    @Test
    fun givenVersion_whenIsAtLeast_thenReturnsExpected() {
        with(GnuLibcVersion(major = 2u, minor = 24u)) {
            assertTrue(isAtLeast(1u, 19u))
            assertTrue(isAtLeast(1u, 45u))
            assertTrue(isAtLeast(2u, 23u))
            assertTrue(isAtLeast(2u, 24u))

            assertFalse(isAtLeast(2u, 25u))
            assertFalse(isAtLeast(3u, 0u))
            assertFalse(isAtLeast(3u, 45u))
        }
    }
}
