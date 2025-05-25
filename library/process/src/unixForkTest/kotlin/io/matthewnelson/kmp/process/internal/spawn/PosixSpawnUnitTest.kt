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
import io.matthewnelson.kmp.file.canonicalPath
import io.matthewnelson.kmp.file.resolve
import io.matthewnelson.kmp.file.toFile
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
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.fail

class PosixSpawnUnitTest {

    private companion object {
        // Will be null if not currently supported
        val CHDIR = posixSpawnScopeOrNull(requireChangeDir = true) {
            @OptIn(ExperimentalStdlibApi::class)
            val rand = Random.Default.nextBytes(8).toHexString()
            SysTempDir.resolve("pspawn_$rand")
        }
    }

    @AfterTest
    fun teardownTest() { CHDIR?.delete() }

    @BeforeTest
    fun setupTest() { CHDIR?.mkdirs() }

    @Test
    fun givenSh_whenEcho_thenIsSuccessful() {
        if (!IS_POSIX_SPAWN_AVAILABLE) {
            println("Skipping...")
            return
        }

        val expected = "Hello World!"

        val p = posixSpawn(
            command = "sh",
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
            command = "sh",
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
        assertEquals(CHDIR.canonicalPath(), output.firstOrNull()?.toFile()?.canonicalPath())
    }

    @Test
    fun givenAddChdir_whenAvailableAndDirDoesNotExist_thenThrowsException() {
        if (!IS_POSIX_SPAWN_AVAILABLE || CHDIR == null) {
            println("Skipping...")
            return
        }

        try {
            posixSpawn(
                command = "sh",
                args = listOf("-c", "echo \"$(pwd)\"; sleep 1; exit 42"),
                chdir = CHDIR.resolve("does_not_exist"),
                env = Process.Current.environment(),
                stdio = Stdio.Config.Builder.get().build(null),
                destroy = Signal.SIGTERM,
                handler = ProcessException.Handler.IGNORE,
            )?.destroy() // for posterity...
            fail("posix_spawn should have failed due to directory not existing")
        } catch (e: IOException) {
            assertIs<FileNotFoundException>(e)
            assertEquals(true, e.message?.contains("Directory specified does not exist"))
        }
    }

    @Test
    fun givenInvalidProgramPath_whenSpawn_thenThrowsException() {
        if (!IS_POSIX_SPAWN_AVAILABLE) {
            println("Skipping...")
            return
        }

        assertFailsWith<IOException> {
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
    }
}
