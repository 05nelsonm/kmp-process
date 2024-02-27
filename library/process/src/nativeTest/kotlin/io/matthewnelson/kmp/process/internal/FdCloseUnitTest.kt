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
import io.matthewnelson.kmp.process.internal.stdio.StdioDescriptor
import io.matthewnelson.kmp.process.internal.stdio.StdioDescriptor.Single.Companion.fdOpen
import platform.posix.EBADF
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FdCloseUnitTest {

    @Test
    fun givenDescriptor_whenFdClose_thenReturnsNull() {
        val descriptor = testFD()
        assertNull(fdClose(descriptor.fd))
    }

    @Test
    fun givenDescriptor_whenAlreadyClosed_thenReturnsErrno() {
        val descriptor = testFD()
        assertNull(fdClose(descriptor.fd))

        assertEquals(EBADF, fdClose(descriptor.fd))
    }

    private fun testFD(): StdioDescriptor.Single {
        val f = PROJECT_DIR_PATH
            .toFile()
            .resolve("build.gradle.kts")

        return Stdio.File.of(f).fdOpen(isStdin = true)
    }
}
