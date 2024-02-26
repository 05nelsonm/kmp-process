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
package io.matthewnelson.kmp.process.testing

import android.app.Application
import android.content.Context
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

    override val homeDir: File get() = ctx.getDir("torservice", Context.MODE_PRIVATE)
    override val cacheDir: File get() = SysTempDir.resolve("torservice").resolve("cache")
    override val tempDir: File get() = ctx.cacheDir

    private val sdkInt: Int = android.os.Build.VERSION.SDK_INT

    override fun assertExitCode(code: Int) {
        val expected = if (sdkInt < 24) Signal.SIGKILL.code else 0
        assertEquals(expected, code)
    }

    @Test
    override fun givenCurrentProcess_whenPid_thenSucceeds() {
        super.givenCurrentProcess_whenPid_thenSucceeds()

        val expected = android.os.Process.myPid()
        assertEquals(expected, Process.Current.pid())
    }

    @Test
    override fun givenExecutable_whenOutputToFile_thenIsAsExpected(): TestResult {
        if (sdkInt < 21) return
        return super.givenExecutable_whenOutputToFile_thenIsAsExpected()
    }

    @Test
    override fun givenExecutable_whenOutput_thenIsAsExpected(): TestResult {
        if (sdkInt < 21) return
        return super.givenExecutable_whenOutput_thenIsAsExpected()
    }

    @Test
    override fun givenExecutable_whenPipeOutputFeeds_thenIsAsExpected(): TestResult {
        if (sdkInt < 21) return
        return super.givenExecutable_whenPipeOutputFeeds_thenIsAsExpected()
    }

    @Test
    fun givenStdioFiles_whenNonExistentOrImproperPermissions_thenSpawnThrowsException() {
        // This ensures functionality is the same, even for supplemental
        // implementation of redirects for API 23 and below

        val d = tempDir.resolve("some_directory")
        val f = tempDir.resolve("test_file.txt")

        d.delete()
        d.deleteOnExit()
        f.delete()
        f.deleteOnExit()

        val fStdio = Stdio.File.of(f)

        val b = Process.Builder(command = "sh")
            .args("-c")
            .args("sleep 0.25")
            .stdin(fStdio)

        // Should fail for not existing
        assertFailsWith<IOException> { b.spawn {} }

        b.stdin(Stdio.File.of(d))
        assertTrue(d.mkdirs())

        // Should fail for not being a file
        assertFailsWith<IOException> { b.spawn {} }

        assertTrue(f.createNewFile())
        assertTrue(f.setReadable(false))
        assertTrue(f.setWritable(false))

        // Should fail for not being able to read
        assertFailsWith<IOException> { b.spawn {} }
        b.stdin(Stdio.Pipe)

        // Should fail for not being able to write
        b.stdout(fStdio)
        assertFailsWith<IOException> { b.spawn {} }
        b.stdout(Stdio.Pipe)

        // Should fail for not being able to write
        b.stderr(fStdio)
        assertFailsWith<IOException> { b.spawn {} }
    }
}
