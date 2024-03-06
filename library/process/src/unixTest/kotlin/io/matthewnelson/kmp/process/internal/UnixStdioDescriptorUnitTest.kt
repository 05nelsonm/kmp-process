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

import io.matthewnelson.kmp.file.resolve
import io.matthewnelson.kmp.file.toFile
import io.matthewnelson.kmp.process.PROJECT_DIR_PATH
import io.matthewnelson.kmp.process.Stdio
import io.matthewnelson.kmp.process.internal.stdio.StdioDescriptor.Companion.fdOpen
import io.matthewnelson.kmp.process.internal.stdio.StdioDescriptor.Pipe.Companion.fdOpen
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.posix.*
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalForeignApi::class)
class UnixStdioDescriptorUnitTest {

    @Test
    fun givenStdioFile_whenFdOpen_thenHasFDCLOEXEC() {
        val f = PROJECT_DIR_PATH.toFile().resolve("build.gradle.kts")
        val descriptor = Stdio.File.of(f).fdOpen(isStdin = true)

        try {
            assertTrue(descriptor.withFd { it }.hasCLOEXEC())
        } finally {
            descriptor.close()
        }
    }

    @Test
    fun givenStdioPipe_whenFdOpen_thenHasFDCLOEXEC() {
        val pipe = Stdio.Pipe.fdOpen()

        try {
            assertTrue(pipe.read.withFd { it }.hasCLOEXEC())
            assertTrue(pipe.write.withFd { it }.hasCLOEXEC())
        } finally {
            pipe.close()
        }
    }

    @Test
    fun givenPosixPipe1_whenTestFunctionHasCLOEXEC_thenIsFalse() {
        // Tests the hasCLOEXEC function to ensure
        // that it works as expected.
        val fds = IntArray(2) { -1 }
        fds.usePinned { pinned ->
            pipe(pinned.addressOf(0)).check()
        }

        try {
            assertFalse(fds[0].hasCLOEXEC())
            assertFalse(fds[1].hasCLOEXEC())
        } finally {
            close(fds[0])
            close(fds[1])
        }
    }

    private fun Int.hasCLOEXEC(): Boolean {
        val stat = fcntl(this, F_GETFD).check()
        return (stat or FD_CLOEXEC) == stat
    }
}
