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
package io.matthewnelson.process

import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

abstract class ProcessNonJsBaseTest: ProcessBaseTest() {

    protected abstract val isUnixDesktop: Boolean

    @Test
    fun givenWaitFor_whenProcessExits_thenWaitForReturnsEarly() {
        if (!isUnixDesktop) {
            println("Skipping...")
            return
        }

        val runTime = measureTime {
            val p = Process.Builder("sleep")
                .arg("0.25")
                .start()

            assertNull(p.waitFor(100.milliseconds))
            assertTrue(p.isAlive)
            assertEquals(0, p.waitFor(2.seconds))
            assertFalse(p.isAlive)
        }

        // Should be less than the 2 seconds (dropped out early)
        assertTrue(runTime < 1.seconds)
    }

    @Test
    fun givenExitCode_whenCompleted_thenIsAsExpected() {
        if (!isUnixDesktop) {
            println("Skipping...")
            return
        }

        val expected = 42
        val p = Process.Builder("sh")
            .arg("-c")
            .arg("sleep 0.25; exit $expected")
            .start()

        assertEquals(expected, p.waitFor(1.seconds))
    }
}
