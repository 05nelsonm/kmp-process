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

import io.matthewnelson.kmp.file.resolve
import io.matthewnelson.kmp.file.toFile
import io.matthewnelson.kmp.process.PROJECT_DIR_PATH
import io.matthewnelson.kmp.process.Stdio
import io.matthewnelson.kmp.process.internal.stdio.StdioDescriptor.Companion.fdOpen
import platform.posix.*
import kotlin.test.Test
import kotlin.test.assertEquals

class StdioDescriptorUnitTest {

    @Test
    fun givenIO_whenCheckFD_thenIsExpected() {
        assertEquals(StdioDescriptor.STDIN.withFd { it }, STDIN_FILENO)
        assertEquals(StdioDescriptor.STDOUT.withFd { it }, STDOUT_FILENO)
        assertEquals(StdioDescriptor.STDERR.withFd { it }, STDERR_FILENO)
    }

    @Test
    fun givenClosed_whenWithFd_thenIsError() {
        val f = PROJECT_DIR_PATH
            .toFile()
            .resolve("build.gradle.kts")
        
        val descriptor = Stdio.File.of(f).fdOpen(isStdin = true)
        descriptor.close()
        val actual = descriptor.withFd { it }
        assertEquals(-1, actual)
        assertEquals(EBADF, errno)
    }
}
