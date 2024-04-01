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

import io.matthewnelson.kmp.file.*
import io.matthewnelson.kmp.process.IsDarwinMobile
import io.matthewnelson.kmp.process.PROJECT_DIR_PATH
import io.matthewnelson.kmp.process.Signal
import io.matthewnelson.kmp.process.Stdio
import io.matthewnelson.kmp.process.internal.spawn.GnuLibcVersion
import io.matthewnelson.kmp.process.internal.stdio.StdioHandle
import io.matthewnelson.kmp.process.internal.stdio.StdioHandle.Companion.openHandle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class SpawnUnitTest {

    private val chdirIsAvailable: Boolean by lazy {
        var available: Boolean? = null
        GnuLibcVersion.check {
            // does nothing on non-Linux
            available = isAtLeast(major = 2u, minor = 29u)
        }

        // available on Linux glibc 2.29+
        available ?: false
    }

    @Test
    fun givenProcess_whenForkExec_thenIsSuccessful() {
        if (IsDarwinMobile) {
            println("Skipping...")
            return
        }

        val d = PROJECT_DIR_PATH.toFile().resolve("src").resolve("commonMain")

        val p = spawnProcess(
            "sh",
            listOf("-c", "echo \"$(pwd)\"; sleep 1; exit 42"),
            chdir = d,
            useFork = true,
        )
        val output = mutableListOf<String>()
        val code = try {
            p.stdoutFeed { line ->
                if (line == null) return@stdoutFeed
                output.add(line)
            }
            p.waitFor()
        } finally {
            p.destroy()
        }

        println(p)
        assertEquals(42, code)
        assertEquals(d.path, output.firstOrNull())
    }

    @Test
    fun givenProcess_whenPosixSpawn_thenIsSuccessful() {
        if (IsDarwinMobile) {
            println("Skipping...")
            return
        }

        val d = if (chdirIsAvailable) {
            PROJECT_DIR_PATH.toFile()
                .resolve("src")
                .resolve("commonMain")
        } else {
            null
        }

        val p = spawnProcess(
            "sh",
            listOf("-c", "echo \"$(pwd)\"; sleep 1; exit 42"),
            chdir = d,
            useFork = false,
        )
        val output = mutableListOf<String>()
        val code = try {
            p.stdoutFeed { line ->
                if (line == null) return@stdoutFeed
                output.add(line)
            }
            p.waitFor()
        } finally {
            p.destroy()
        }

        println(p)
        assertEquals(42, code)
        if (d != null) {
            assertEquals(d.path, output.firstOrNull())
        }
    }

    @Test
    fun givenBadCommand_whenForkExec_thenExecDispatchesError() {
        if (IsDarwinMobile) {
            println("Skipping...")
            return
        }

        try {
            // Should result in an ENOENT when exec is called
            val p = spawnProcess(
                "/invalid/path/sh",
                listOf("-c", "sleep 1; exit 5"),
                useFork = true,
            )
            p.destroy()
            fail("forkExec returned Process")
        } catch (e: IOException) {
            assertTrue(e.message!!.startsWith("Child process exec failure."))
        }
    }

    @Test
    fun givenBadDup2_whenForkExec_thenDup2DispatchesError() {
        if (IsDarwinMobile) {
            println("Skipping...")
            return
        }

        try {
            val h = Stdio.Config.Builder.get().build(null).openHandle()

            // This should result in an IOException when dup2 is called in child process
            // which will then be piped back to the parent process and child _exit called
            h.close()

            val p = spawnProcess(
                "sh",
                listOf("-c", "sleep 1; exit 5"),
                handle = h,
                useFork = true,
            )
            p.destroy()
            fail("forkExec returned Process")
        } catch (e: IOException) {
            assertTrue(e.message!!.startsWith("Child process dup2 failure."))
        }
    }

    @Test
    fun givenBadChdir_whenForkExec_thenChdirDispatchesError() {
        if (IsDarwinMobile) {
            println("Skipping...")
            return
        }

        val d = PROJECT_DIR_PATH.toFile().resolve("non_existent_directory")

        try {
            val p = spawnProcess(
                "sh",
                listOf("-c", "sleep 1; exit 5"),
                chdir = d,
                useFork = true,
            )
            p.destroy()
            fail("forkExec returned Process")
        } catch (e: IOException) {
            assertTrue(e.message!!.startsWith("Child process chdir failure."))
        }
    }

    @Test
    fun givenBadChdir_whenPosixSpawn_thenThrowsException() {
        if (!chdirIsAvailable) {
            println("Skipping...")
            return
        }

        val d = PROJECT_DIR_PATH.toFile().resolve("non_existent_directory")

        try {
            val p = spawnProcess(
                "sh",
                listOf("-c", "sleep 1; exit 5"),
                chdir = d,
                useFork = false,
            )
            p.destroy()
            fail("posixSpawn returned Process")
        } catch (_: IOException) {
            // pass
        }
    }

    private fun spawnProcess(
        command: String,
        args: List<String>,
        chdir: File? = null,
        handle: StdioHandle? = null,
        useFork: Boolean,
    ): NativeProcess {
        val h = handle ?: Stdio.Config.Builder.get()
            .build(null)
            .openHandle()

        val p = try {
            val b = PlatformBuilder.get()
            if (useFork) {
                b.forkExec(command, args, chdir, b.env, h, Signal.SIGTERM)
            } else {
                b.posixSpawn(command, args, chdir, b.env, h, Signal.SIGTERM)
            }
        } catch (e: IOException) {
            h.close()
            throw e
        }

        return p
    }
}
