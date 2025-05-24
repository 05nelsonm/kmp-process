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
package io.matthewnelson.kmp.process.internal.stdio

import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.process.Stdio
import io.matthewnelson.kmp.process.internal.stdio.StdioHandle.Companion.openHandle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StdioHandleUnitTest {

    @Test
    fun givenPipes_whenNotClosed_thenReadersWritersAreNotNull() {
        val handle = Stdio.Config.Builder.get().build(null).openHandle()

        try {
            assertNotNull(handle.stdinStream())
            assertNotNull(handle.stdoutReader())
            assertNotNull(handle.stderrReader())
        } finally {
            handle.close()
        }
    }

    @Test
    fun givenPipes_whenClosed_thenReadersWritersAreNull() {
        val handle = Stdio.Config.Builder.get().build(null).openHandle()

        handle.close()

        assertNull(handle.stdinStream())
        assertNull(handle.stdoutReader())
        assertNull(handle.stderrReader())
    }

    @Test
    fun givenHandle_whenDup2Failure_thenCloses() {
        repeat(3) { n ->
            val handle = Stdio.Config.Builder.get().build(null).openHandle()

            var i = 0
            try {
                assertFailsWith<IOException> {
                    // Is invoked 3 times (stdin, stdout, stderr).
                    // Check things are thrown for each...
                    handle.dup2 { _, _ ->
                        if (i == n) return@dup2 IOException()
                        i++
                        null
                    }
                }

                assertTrue(handle.isClosed)
            } finally {
                handle.close()
            }

            assertEquals(n, i)
            println(i)
        }
    }
}
