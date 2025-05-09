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
package io.matthewnelson.kmp.process.internal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// Project requires Java11, so all tests should run properly
class PIDUnitTest {

    @Test
    fun givenPID_whenAndroid_thenReturnsNull() {
        assertNull(PID.androidOrNull())
    }

    @Test
    fun givenPID_whenJava9_thenReturnsPIDJava8() {
        assertEquals(PID.java8(), PID.java9OrNull())
    }

    @Test
    fun givenPID_whenJava10_thenReturnsPIDJava8() {
        assertEquals(PID.java8(), PID.java10OrNull())
    }

    @Test
    fun givenPID_whenGet_thenReturnsPIDJava8() {
        assertEquals(PID.java8(), PID.get())
    }
}
