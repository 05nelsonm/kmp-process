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
package io.matthewnelson.kmp.process

import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.resolve
import io.matthewnelson.kmp.file.toFile
import io.matthewnelson.kmp.process.internal.STDIO_NULL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StdioConfigUnitTest {

    @Test
    fun givenBuilder_whenStdinFileAppendTrue_thenBuildChangesToAppendFalse() {
        val b = Stdio.Config.Builder.get()

        val buildGradle = PROJECT_DIR_PATH.toFile().resolve("build.gradle.kts")
        b.stdin = Stdio.File.of(buildGradle, append = true)

        val config = b.build(null)
        assertEquals(false, (config.stdin as Stdio.File).append)
    }

    @Test
    fun givenBuilder_whenStdinFileIsSameAsOutputFile_thenThrowsException() {
        val b = Stdio.Config.Builder.get()

        b.stdin = Stdio.Null
        b.stdout = Stdio.Null
        b.stderr = Stdio.Null

        // Null file should not throw exception
        b.build(null)
        b.stderr = Stdio.Pipe

        val f = Stdio.File.of(PROJECT_DIR_PATH.toFile().resolve("build.gradle.kts"))
        b.stdin = f
        b.stdout = f

        assertFailsWith<IOException> { b.build(null) }
        b.stdout = Stdio.Pipe

        b.stderr = f
        assertFailsWith<IOException> { b.build(null) }
    }

    @Test
    fun givenBuilder_whenOutputOptions_thenStdoutStderrArePipe() {
        val b = Stdio.Config.Builder.get()

        b.stdout = Stdio.File.of("stdout")
        b.stderr = Stdio.File.of("stderr")

        val config = b.build(Output.Options.Builder.build {  })
        assertEquals(Stdio.Pipe, config.stdout)
        assertEquals(Stdio.Pipe, config.stderr)
    }

    @Test
    fun givenStdioNull_whenAppendTrue_thenReturnsAppendFalse() {
        assertEquals(false, Stdio.File.of(STDIO_NULL, append = true).append)
    }
}
