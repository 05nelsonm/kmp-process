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

import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.SysDirSep
import io.matthewnelson.kmp.file.SysTempDir
import io.matthewnelson.kmp.file.canonicalPath
import io.matthewnelson.kmp.file.name
import io.matthewnelson.kmp.file.parentFile
import io.matthewnelson.kmp.file.parentPath
import io.matthewnelson.kmp.file.path
import io.matthewnelson.kmp.file.resolve
import io.matthewnelson.kmp.file.toFile
import io.matthewnelson.kmp.process.Process
import io.matthewnelson.kmp.process.ProcessException
import io.matthewnelson.kmp.process.Signal
import io.matthewnelson.kmp.process.Stdio
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ForkUnitTest {

    private companion object {
        val CHDIR = run {
            @OptIn(ExperimentalStdlibApi::class)
            val rand = Random.Default.nextBytes(8).toHexString()
            SysTempDir.resolve("fork_$rand")
        }
    }

    @AfterTest
    fun teardownTest() { CHDIR.delete() }

    @BeforeTest
    fun setupTest() { CHDIR.mkdirs() }

    @Test
    fun givenSh_whenEcho_thenIsSuccessful() {
        val expected = "Hello World!"
        val b = PlatformBuilder.get()

        val p = b.forkExec(
            command = "sh",
            args = listOf("-c", "echo \"$expected\"; sleep 1; exit 42"),
            chdir = null,
            env = b.env,
            stdio = Stdio.Config.Builder.get().build(null),
            destroy = Signal.SIGTERM,
            handler = ProcessException.Handler.IGNORE,
        )

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
    fun givenExecutable_whenRelativePathWithChDir_thenExecutes() {
        if (!IS_COMMAND_WHICH_AVAILABLE) {
            println("Skipping...")
            return
        }

        val out = Process.Builder(command = "which").args("sh").output()
        val expected = "Hello World!"
        val sh = out.stdout.toFile()
        assertTrue(sh.exists())
        assertTrue(sh.isAbsolute())

        val parentDirName = sh.parentPath?.substringAfterLast(SysDirSep)
        assertNotNull(parentDirName)
        val command = "..".toFile()
            .resolve(parentDirName)
            .resolve(sh.name)

        val b = PlatformBuilder.get()

        val p = b.forkExec(
            command = command.path,
            args = listOf("-c", "echo \"$expected\"; sleep 1; exit 42"),
            chdir = sh.parentFile,
            env = b.env,
            stdio = Stdio.Config.Builder.get().build(null),
            destroy = Signal.SIGTERM,
            handler = ProcessException.Handler.IGNORE
        )

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
    fun givenChdir_whenDirExists_thenIsSuccessful() {
        val b = PlatformBuilder.get()

        val p = b.forkExec(
            command = "sh",
            args = listOf("-c", "echo \"$(pwd)\"; sleep 1; exit 42"),
            chdir = CHDIR,
            env = b.env,
            stdio = Stdio.Config.Builder.get().build(null),
            destroy = Signal.SIGTERM,
            handler = ProcessException.Handler.IGNORE,
        )

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
    fun givenChdir_whenDirDoesNotExist_thenThrowsException() {
        assertFailsWith<IOException> {
            val b = PlatformBuilder.get()

            b.forkExec(
                command = "sh",
                args = listOf("-c", "echo \"$(pwd)\"; sleep 1; exit 42"),
                chdir = CHDIR.resolve("does_not_exist"),
                env = b.env,
                stdio = Stdio.Config.Builder.get().build(null),
                destroy = Signal.SIGTERM,
                handler = ProcessException.Handler.IGNORE,
            ).destroy() // for posterity...
        }
    }
}
