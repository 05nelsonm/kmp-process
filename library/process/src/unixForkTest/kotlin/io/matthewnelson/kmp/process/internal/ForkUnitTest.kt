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

import io.matthewnelson.kmp.file.FileNotFoundException
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.OpenExcl
import io.matthewnelson.kmp.file.SysDirSep
import io.matthewnelson.kmp.file.SysTempDir
import io.matthewnelson.kmp.file.canonicalPath2
import io.matthewnelson.kmp.file.delete2
import io.matthewnelson.kmp.file.errnoToIOException
import io.matthewnelson.kmp.file.exists2
import io.matthewnelson.kmp.file.mkdirs2
import io.matthewnelson.kmp.file.name
import io.matthewnelson.kmp.file.parentFile
import io.matthewnelson.kmp.file.parentPath
import io.matthewnelson.kmp.file.path
import io.matthewnelson.kmp.file.resolve
import io.matthewnelson.kmp.file.toFile
import io.matthewnelson.kmp.file.writeUtf8
import io.matthewnelson.kmp.process.Blocking
import io.matthewnelson.kmp.process.Process
import io.matthewnelson.kmp.process.changeDir
import io.matthewnelson.kmp.process.usePosixSpawn
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UnsafeNumber
import platform.posix.O_RDONLY
import platform.posix.close
import platform.posix.errno
import platform.posix.open
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)
class ForkUnitTest {

    private companion object {
        val CHDIR = run {
            @OptIn(ExperimentalStdlibApi::class)
            val rand = Random.nextBytes(8).toHexString()
            SysTempDir.resolve("fork_$rand")
        }
    }

    @AfterTest
    fun teardownTest() { CHDIR.delete2(ignoreReadOnly = true) }

    @BeforeTest
    fun setupTest() { CHDIR.mkdirs2(mode = null) }

    @Test
    fun givenSh_whenEcho_thenIsSuccessful() {
        val expected = "Hello World!"
        val p = Process.Builder(command = "sh")
            .usePosixSpawn(use = false)
            .args("-c", "echo \"$expected\"; sleep 1; exit 42")
            .spawn()

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
    fun givenParentProcessDescriptorsWithoutCLOEXEC_whenSpawn_thenChildProcessClosesDescriptors() {
        val fdsBuffer = IntArray(10) { -1 }
        val fdsTest = fdsBuffer.copyOf()
        @OptIn(ExperimentalUuidApi::class)
        val file = SysTempDir.resolve(Uuid.random().toString())
        var p: Process? = null

        val fdsChild = try {
            // Ensure file exists
            file.writeUtf8(excl = OpenExcl.MustCreate.of("400"), "")

            // fdsBuffer are purely for increasing the fdsTest descriptors to a higher
            // number. Once fdsBuffer descriptors are all closed in this process, we
            // should have some distance between any descriptors the Child Process would
            // open, and ours in the parent.
            arrayOf(fdsBuffer, fdsTest).forEach { array ->
                for (i in array.indices) {
                    // No O_CLOEXEC, so they will be leaked to child process (normally).
                    array[i] = open(file.path, O_RDONLY, 0)
                    if (array[i] == -1) throw errnoToIOException(errno, file)
                }
            }

            for (i in fdsBuffer.indices) {
                close(fdsBuffer[i])
                fdsBuffer[i] = -1
            }

            p = Process.Builder(command = "ls")
                .usePosixSpawn(false)
                .args(FD_DIR)
                .spawn()

            var eosStdout = false
            var eosStderr = false
            val fdsLS = mutableListOf<String>()

            p.stdoutFeed { line ->
                if (line == null) {
                    eosStdout = true
                    return@stdoutFeed
                }
                fdsLS.add(line)
            }.stderrFeed { line ->
                if (line == null) {
                    eosStderr = true
                    return@stderrFeed
                }
                println(line)
            }.waitFor()

            while (true) {
                if (eosStdout && eosStderr) break
                Blocking.threadSleep(25.milliseconds)
            }

            fdsLS.map { it.trim().toInt() }
        } finally {
            (fdsBuffer + fdsTest).forEach { fd ->
                if (fd == -1) return@forEach
                close(fd)
            }
            file.delete2()
            p?.destroy()
        }

        fdsTest.forEach { fd ->
            if (!fdsChild.contains(fd)) return@forEach
            fail("Parent Process fds${fdsTest.toList()} were leaked >> Child Process fds$fdsChild")
        }
    }

    @Test
    fun givenExecutable_whenRelativePathWithChDir_thenExecutes() {
        if (!IS_COMMAND_WHICH_AVAILABLE) {
            println("Skipping...")
            return
        }

        val sh = Process.Builder(command = "which").args("sh").output().stdout.toFile()
        val expected = "Hello World!"
        assertTrue(sh.exists2())
        assertTrue(sh.isAbsolute())

        val parentDirName = sh.parentPath?.substringAfterLast(SysDirSep)
        assertNotNull(parentDirName)
        val command = "..".toFile()
            .resolve(parentDirName)
            .resolve(sh.name)

        val p = Process.Builder(command = command.path)
            .usePosixSpawn(use = false)
            .args("-c", "echo \"$expected\"; sleep 1; exit 42")
            .changeDir(sh.parentFile)
            .spawn()

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
        val p = Process.Builder(command = "sh")
            .usePosixSpawn(use = false)
            .args("-c", "echo \"$(pwd)\"; sleep 1; exit 42")
            .changeDir(CHDIR)
            .spawn()

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
    fun givenChdir_whenDirDoesNotExist_thenThrowsException() {
        assertFailsWith<IOException> {
            Process.Builder(command = "sh")
                .usePosixSpawn(use = false)
                .args("-c", "echo \"$(pwd)\"; sleep 1; exit 42")
                .changeDir(CHDIR.resolve("does_not_exist"))
                .spawn()
                .destroy() // for posterity
        }
    }

    @Test
    fun givenUsePosixSpawn_whenFalse_thenUsesForkExecve() {
        if (!IS_POSIX_SPAWN_AVAILABLE) {
            // Need posixSpawn available for the platform, otherwise would
            // always fall back to the forkExec implementation and show a
            // false positive for our usePosixSpawn = false setting.
            println("Skipping...")
            return
        }

        val b = Process.Builder(command = "does_not_exist_123")

        try {
            b.spawn().destroy()
            fail("spawn should have failed due to program not existing...")
        } catch (e: IOException) {
            // Check that the error message format is that of the posixSpawn implementation's.
            assertEquals(true, e.message?.startsWith("posix_spawnp failed"))
            assertTrue(b.platform().usePosixSpawn)
        }

        try {
            b.usePosixSpawn(use = false).spawn().destroy()
            fail("spawn should have failed due to program not existing...")
        } catch (e: FileNotFoundException) {
            // Check that the error message format is that of the forkExec implementation's.
            assertEquals(true, e.message?.startsWith("Child process exec failure."))
            assertFalse(b.platform().usePosixSpawn)
        }
    }
}
