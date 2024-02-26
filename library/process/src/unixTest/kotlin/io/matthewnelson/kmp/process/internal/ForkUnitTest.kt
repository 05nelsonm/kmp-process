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

import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.process.IsDarwinMobile
import io.matthewnelson.kmp.process.Signal
import io.matthewnelson.kmp.process.Stdio
import io.matthewnelson.kmp.process.internal.stdio.StdioHandle
import io.matthewnelson.kmp.process.internal.stdio.StdioHandle.Companion.openHandle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class ForkUnitTest {

    @Test
    fun givenProcess_whenFork_thenIsSuccessful() {
        if (IsDarwinMobile) {
            println("Skipping...")
            return
        }

        val p = forkProcess("sh", listOf("-c", "sleep 1; exit 42"))
        val code = try {
            p.waitFor()
        } finally {
            p.destroy()
        }

        println(p)
        assertEquals(42, code)
    }

    @Test
    fun givenInvalidProgramPath_whenFork_thenExecThrowsException() {
        if (IsDarwinMobile) {
            println("Skipping...")
            return
        }

        try {
            // Should result in an ENOENT when exec is called
            val p = forkProcess("/invalid/path/sh", listOf("-c", "sleep 1; exit 5"))
            p.destroy()
            fail("forkExec returned Process")
        } catch (e: IOException) {
            assertTrue(e.message!!.startsWith("Child process exec failure."))
        }
    }

    @Test
    fun givenBadDup2_whenFork_thenDup2ThrowsException() {
        if (IsDarwinMobile) {
            println("Skipping...")
            return
        }

        try {
            val h = Stdio.Config.Builder.get().build(null).openHandle()

            // This should result in an IOException when dup2 is called in child process
            // which will then be piped back to the parent process and child _exit called
            h.close()

            val p = forkProcess("sh", listOf("-c", "sleep 1; exit 5"), h)
            p.destroy()
            fail("forkExec returned Process")
        } catch (e: IOException) {
            assertTrue(e.message!!.startsWith("Child process dup2 failure."))
        }
    }

    private fun forkProcess(
        command: String,
        args: List<String>,
        handle: StdioHandle? = null,
    ): NativeProcess {
        val h = handle ?: Stdio.Config.Builder.get()
            .build(null)
            .openHandle()

        val p = try {
            val b = PlatformBuilder.get()
            b.forkExec(command, args, b.env, h, Signal.SIGTERM)
        } catch (e: IOException) {
            h.close()
            throw e
        }

        return p
    }
}
