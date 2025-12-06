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
@file:Suppress("RedundantUnitReturnType")

package io.matthewnelson.kmp.process.test.api

import android.app.Application
import android.content.Context
import android.os.Build
import android.system.Os
import androidx.test.core.app.ApplicationProvider
import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.SysTempDir
import io.matthewnelson.kmp.process.Process
import io.matthewnelson.kmp.process.Signal
import io.matthewnelson.kmp.process.Stdio
import kotlinx.coroutines.test.TestResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ProcessAndroidTest: ProcessBaseTest() {

    private val ctx = ApplicationProvider.getApplicationContext<Application>().applicationContext

    override val IsAndroidInstrumentTest: Boolean = true
    override val homeDir: File get() = ctx.getDir("torservice", Context.MODE_PRIVATE)
    override val cacheDir: File get() = SysTempDir.resolve("torservice").resolve("cache")

    private val sdkInt: Int = Build.VERSION.SDK_INT

    override fun assertExitCode(code: Int) {
        val expected = if (sdkInt < 24) Signal.SIGKILL.code else 0
        assertEquals(expected, code)
    }

    @Test
    fun givenAndroidOsEnvironment_whenModified_thenMatchesCurrentProcessEnvironment() {
        if (sdkInt < Build.VERSION_CODES.LOLLIPOP) {
            println("Skipping...")
            return
        }

        val envKey = "process_env_test"
        Os.setenv(envKey, "anything", true)

        val environmentProcess = Process.Current.environment().map { (key, value) -> "$key=$value" }
        val environmentOs = Os.environ().toList()

        assertEquals(sdkInt !in 24..32, ProcessBuilder().environment().contains(envKey))

        val missingFromProcessEnv = mutableListOf<String>()
        environmentOs.forEach { line ->
            if (environmentProcess.contains(line)) return@forEach
            missingFromProcessEnv.add(line)
        }

        val missingFromOsEnv = mutableListOf<String>()
        environmentProcess.forEach { line ->
            if (environmentOs.contains(line)) return@forEach
            missingFromOsEnv.add(line)
        }

        Os.unsetenv(envKey)
        if (missingFromProcessEnv.isEmpty() && missingFromOsEnv.isEmpty()) return

        buildString {
            append("Missing From Os.environ: [")
            if (missingFromOsEnv.isEmpty()) {
                appendLine(']')
            } else {
                appendLine()
                missingFromOsEnv.forEach { line ->
                    append("    ").appendLine(line)
                }
                appendLine(']')
            }

            append("Missing From ProcessBuilder.environment: [")
            if (missingFromProcessEnv.isEmpty()) {
                appendLine(']')
            } else {
                appendLine()
                missingFromProcessEnv.forEach { line ->
                    append("    ").appendLine(line)
                }
                appendLine(']')
            }
        }.let { throw AssertionError(it) }
    }

    @Test
    override fun givenCurrentProcess_whenPid_thenSucceeds() {
        super.givenCurrentProcess_whenPid_thenSucceeds()

        val expected = android.os.Process.myPid()
        assertEquals(expected, Process.Current.pid())
    }

    @Test
    override fun givenExecutable_whenRelativePathWithChDir_thenExecutes() {
        if (sdkInt < 21) {
            println("Skipping...")
            return
        }
        super.givenExecutable_whenRelativePathWithChDir_thenExecutes()
    }

    @Test
    override fun givenExecutable_whenOutputToFile_thenIsAsExpected(): TestResult {
        if (sdkInt < 21) {
            println("Skipping...")
            return
        }
        return super.givenExecutable_whenOutputToFile_thenIsAsExpected()
    }

    @Test
    override fun givenExecutable_whenOutput_thenIsAsExpected(): TestResult {
        if (sdkInt < 21) {
            println("Skipping...")
            return
        }
        return super.givenExecutable_whenOutput_thenIsAsExpected()
    }

    @Test
    override fun givenExecutable_whenPipeOutputFeeds_thenIsAsExpected(): TestResult {
        if (sdkInt < 21) {
            println("Skipping...")
            return
        }
        return super.givenExecutable_whenPipeOutputFeeds_thenIsAsExpected()
    }

    @Test
    fun givenStdioConfig_whenInherit_thenIsModifiedToDevNullOnApi23OrBelow() {
        val p = Process.Builder(command = "sh")
            .args("-c", "echo \"abc\"; exit 42;")
            .stdin(Stdio.Inherit)
            .stdout(Stdio.Inherit)
            .stderr(Stdio.Inherit)
            .createProcess().use { p -> p.waitFor(); p }

        println(p.toString())

        val expected = if (sdkInt >= 24) Stdio.Inherit else Stdio.Null
        arrayOf(p.stdio.stdin, p.stdio.stdout, p.stdio.stderr).forEachIndexed { i, actual ->
            assertEquals(expected, actual, "fileno: $i")
        }
    }

    @Test
    fun givenStdioFiles_whenNonExistentOrImproperPermissions_thenSpawnThrowsException() {
        // This ensures functionality is the same, even for supplemental
        // implementation of redirects for API 23 and below

        val d = SysTempDir.resolve("some_directory")
        val f = SysTempDir.resolve("test_file.txt")

        d.delete()
        d.deleteOnExit()
        f.delete()
        f.deleteOnExit()

        val stdioDir = Stdio.File.of(d)
        val stdioFile = Stdio.File.of(f)

        val b = Process.Builder(command = "sh")
            .args("-c")
            .args("sleep 0.25")

        // Should fail for not existing
        b.stdin(stdioFile)
        assertFailsWith<IOException> { b.createProcess().destroy() }

        assertTrue(f.createNewFile())
        assertTrue(f.setReadable(false))
        assertTrue(f.setWritable(false))

        // Should fail for not being able to read
        assertFailsWith<IOException> { b.createProcess().destroy() }

        assertTrue(d.mkdirs())

        // Should fail for not being a file
        b.stdin(stdioDir)
        assertFailsWith<IOException> { b.createProcess().destroy() }
        b.stdin(Stdio.Pipe)

        // Should fail for not being a file
        b.stdout(stdioDir)
        assertFailsWith<IOException> { b.createProcess().destroy() }

        // Should fail for not being able to write
        b.stdout(stdioFile)
        assertFailsWith<IOException> { b.createProcess().destroy() }
        b.stdout(Stdio.Pipe)

        // Should fail for not being a file
        b.stderr(stdioDir)
        assertFailsWith<IOException> { b.createProcess().destroy() }

        // Should fail for not being able to write
        b.stderr(stdioFile)
        assertFailsWith<IOException> { b.createProcess().destroy() }
    }
}
