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

import io.matthewnelson.kmp.file.SysTempDir
import io.matthewnelson.kmp.file.resolve
import io.matthewnelson.kmp.file.writeUtf8
import io.matthewnelson.kmp.process.Stdio
import io.matthewnelson.kmp.process.internal.stdio.StdioDescriptor
import io.matthewnelson.kmp.process.internal.stdio.StdioDescriptor.Companion.fdOpen
import io.matthewnelson.kmp.process.internal.stdio.StdioDescriptor.Pipe.Companion.fdOpen
import io.matthewnelson.kmp.process.internal.stdio.withFd
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.posix.*
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalForeignApi::class)
class UnixStdioDescriptorUnitTest {

    @Test
    fun givenStdioFile_whenFdOpen_thenHasFDCLOEXEC() {
        @OptIn(ExperimentalStdlibApi::class)
        val d = Random.Default.nextBytes(8).toHexString().let { name ->
            SysTempDir.resolve(name)
        }
        val f = d.resolve("test.txt")

        var descriptor: StdioDescriptor? = null

        try {
            d.mkdirs()
            f.writeUtf8("Hello World!")

            descriptor = Stdio.File.of(f).fdOpen(isStdin = true)
            assertTrue(descriptor.withFd { it }.hasCLOEXEC())
        } finally {
            descriptor?.close()
            f.delete()
            d.delete()
        }
    }

    @Test
    fun givenStdioPipe_whenFdOpen_thenHasFDCLOEXEC() {
        val pipe = Stdio.Pipe.fdOpen()

        try {
            assertTrue(pipe.read.withFd { it }.hasCLOEXEC())
            assertTrue(pipe.write.withFd { it }.hasCLOEXEC())
            assertFalse(pipe.read.withFd { it }.hasNONBLOCK())
            assertFalse(pipe.write.withFd { it }.hasNONBLOCK())
        } finally {
            pipe.close()
        }
    }

    @Test
    fun givenStdioPipe_whenFdOpenNonBlockTrue_thenHasNONBLOCK() {
        val pipe = Stdio.Pipe.fdOpen(nonBlock = true)

        try {
            assertTrue(pipe.read.withFd { it }.hasCLOEXEC())
            assertTrue(pipe.write.withFd { it }.hasCLOEXEC())
            assertTrue(pipe.read.withFd { it }.hasNONBLOCK())
            assertTrue(pipe.write.withFd { it }.hasNONBLOCK())
        } finally {
            pipe.close()
        }
    }

    @Test
    fun givenPosixPipe1_whenTestFunctionHasCLOEXEC_thenIsFalse() {
        // Tests the hasCLOEXEC function to ensure that it works as expected.
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

    @Test
    fun givenPosixPipe1_whenTestFunctionHasNONBLOCK_thenIsFalse() {
        // Tests the hasNONBLOCK function to ensure that it works as expected.
        val fds = IntArray(2) { -1 }
        fds.usePinned { pinned ->
            pipe(pinned.addressOf(0)).check()
        }

        try {
            assertFalse(fds[0].hasNONBLOCK())
            assertFalse(fds[1].hasNONBLOCK())
        } finally {
            close(fds[0])
            close(fds[1])
        }
    }

    private fun Int.hasCLOEXEC(): Boolean {
        val stat = fcntl(this, F_GETFD).check()
        return (stat or FD_CLOEXEC) == stat
    }

    private fun Int.hasNONBLOCK(): Boolean {
        val stat = fcntl(this, F_GETFL).check()
        return (stat or O_NONBLOCK) == stat
    }
}
