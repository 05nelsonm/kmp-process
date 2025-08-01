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
package io.matthewnelson.kmp.process.internal.spawn

import io.matthewnelson.kmp.file.FileNotFoundException
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.SysTempDir
import io.matthewnelson.kmp.file.canonicalPath2
import io.matthewnelson.kmp.file.delete2
import io.matthewnelson.kmp.file.mkdirs2
import io.matthewnelson.kmp.file.resolve
import io.matthewnelson.kmp.file.toFile
import io.matthewnelson.kmp.process.IsAppleSimulator
import io.matthewnelson.kmp.process.Process
import io.matthewnelson.kmp.process.ProcessException
import io.matthewnelson.kmp.process.Signal
import io.matthewnelson.kmp.process.Stdio
import io.matthewnelson.kmp.process.internal.IS_POSIX_SPAWN_AVAILABLE
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class PosixSpawnUnitTest {

    private companion object {
        // Will be null if not currently supported
        val CHDIR = try {
            posixSpawnScopeOrNull(requireChangeDir = true) {
                @OptIn(ExperimentalStdlibApi::class)
                val rand = Random.Default.nextBytes(8).toHexString()
                SysTempDir.resolve("pspawn_$rand")
            }
        } catch (_: UnsupportedOperationException) {
            // iOS
            null
        }
    }

    @AfterTest
    fun teardownTest() { CHDIR?.delete2() }

    @BeforeTest
    fun setupTest() { CHDIR?.mkdirs2(mode = null) }

    @Test
    fun givenSh_whenEcho_thenIsSuccessful() {
        if (!IS_POSIX_SPAWN_AVAILABLE) {
            println("Skipping...")
            return
        }

        val expected = "Hello World!"

        val p = posixSpawn(
            command = if (IsAppleSimulator) "/bin/sh" else "sh",
            args = listOf("-c", "echo \"$expected\"; sleep 1; exit 42"),
            chdir = null,
            env = Process.Current.environment(),
            stdio = Stdio.Config.Builder.get().build(null),
            destroy = Signal.SIGTERM,
            handler = ProcessException.Handler.IGNORE,
        )
        assertNotNull(p)

        val output = mutableListOf<String>()
        val exitCode = try {
            p.stdoutFeed { line ->
                line ?: return@stdoutFeed
                output.add(line)
            }.waitFor()
        } finally {
            p.destroy()
        }
        p.stdoutWaiter().awaitStop()

        println(p)
        assertEquals(42, exitCode)
        assertEquals(expected, output.firstOrNull())
    }

    @Test
    fun givenAddChdir_whenAvailableAndDirExists_thenIsSuccessful() {
        if (!IS_POSIX_SPAWN_AVAILABLE || CHDIR == null) {
            println("Skipping...")
            return
        }

        val p = posixSpawn(
            command = if (IsAppleSimulator) "/bin/sh" else "sh",
            args = listOf("-c", "echo \"$(pwd)\"; sleep 1; exit 42"),
            chdir = CHDIR,
            env = Process.Current.environment(),
            stdio = Stdio.Config.Builder.get().build(null),
            destroy = Signal.SIGTERM,
            handler = ProcessException.Handler.IGNORE,
        )
        assertNotNull(p)

        val output = mutableListOf<String>()
        val exitCode = try {
            p.stdoutFeed { line ->
                line ?: return@stdoutFeed
                output.add(line)
            }.waitFor()
        } finally {
            p.destroy()
        }
        p.stdoutWaiter().awaitStop()

        println(p)
        assertEquals(42, exitCode)
        assertEquals(CHDIR.canonicalPath2(), output.firstOrNull()?.toFile()?.canonicalPath2())
    }

    @Test
    fun givenAddChdir_whenAvailableAndDirDoesNotExist_thenThrowsException() {
        if (!IS_POSIX_SPAWN_AVAILABLE || CHDIR == null) {
            println("Skipping...")
            return
        }

        assertFailsWith<FileNotFoundException> {
            posixSpawn(
                command = if (IsAppleSimulator) "/bin/sh" else "sh",
                args = listOf("-c", "echo \"$(pwd)\"; sleep 1; exit 42"),
                chdir = CHDIR.resolve("does_not_exist"),
                env = Process.Current.environment(),
                stdio = Stdio.Config.Builder.get().build(null),
                destroy = Signal.SIGTERM,
                handler = ProcessException.Handler.IGNORE,
            )?.destroy() // for posterity...
        }
    }

    @Test
    fun givenInvalidProgramPath_whenSpawn_thenThrowsException() {
        if (!IS_POSIX_SPAWN_AVAILABLE) {
            println("Skipping...")
            return
        }

        assertFailsWith<FileNotFoundException> {
            posixSpawn(
                command = "/invalid/path/sh",
                args = listOf("-c", "echo \"$(pwd)\"; sleep 1; exit 42"),
                chdir = null,
                env = Process.Current.environment(),
                stdio = Stdio.Config.Builder.get().build(null),
                destroy = Signal.SIGTERM,
                handler = ProcessException.Handler.IGNORE,
            )?.destroy() // for posterity...
        }

        assertFailsWith<IOException> {
            posixSpawn(
                command = "not_a_program_123",
                args = listOf("-c", "echo \"$(pwd)\"; sleep 1; exit 42"),
                chdir = null,
                env = Process.Current.environment(),
                stdio = Stdio.Config.Builder.get().build(null),
                destroy = Signal.SIGTERM,
                handler = ProcessException.Handler.IGNORE,
            )?.destroy() // for posterity...
        }
    }
}
