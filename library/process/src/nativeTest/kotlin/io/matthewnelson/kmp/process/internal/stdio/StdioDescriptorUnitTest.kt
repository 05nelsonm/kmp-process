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

import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.SysTempDir
import io.matthewnelson.kmp.file.resolve
import io.matthewnelson.kmp.file.writeUtf8
import io.matthewnelson.kmp.process.Stdio
import io.matthewnelson.kmp.process.internal.stdio.StdioDescriptor.Companion.fdOpen
import platform.posix.*
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StdioDescriptorUnitTest {

    @Test
    fun givenIO_whenCheckFD_thenIsExpected() {
        assertEquals(StdioDescriptor.STDIN.withFd { it }, STDIN_FILENO)
        assertEquals(StdioDescriptor.STDOUT.withFd { it }, STDOUT_FILENO)
        assertEquals(StdioDescriptor.STDERR.withFd { it }, STDERR_FILENO)
    }

    @Test
    fun givenClosed_whenWithFd_thenIsError() {
        @OptIn(ExperimentalStdlibApi::class)
        val d = Random.Default.nextBytes(8).toHexString().let { name ->
            SysTempDir.resolve(name)
        }
        val f = d.resolve("test.txt")

        try {
            d.mkdirs()
            f.writeUtf8("Hello World!")

            val descriptor = Stdio.File.of(f).fdOpen(isStdin = true)
            descriptor.close()
            assertFailsWith<IOException> { descriptor.withFd { it } }
        } finally {
            f.delete()
            d.delete()
        }
    }
}
