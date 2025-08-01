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
@file:Suppress("NOTHING_TO_INLINE")

package io.matthewnelson.kmp.process.internal.stdio

import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.errnoToIOException
import io.matthewnelson.kmp.process.internal.check
import kotlinx.cinterop.*
import platform.posix.*

internal actual inline fun Int.orOCloExec(): Int = this or O_CLOEXEC

@Throws(IOException::class)
@OptIn(ExperimentalForeignApi::class)
internal actual fun ((isPipe1: Boolean, fd0: Int, fd1: Int) -> StdioDescriptor.Pipe).fdOpen(
    readEndNonBlock: Boolean,
): StdioDescriptor.Pipe {
    val fds = IntArray(2) { -1 }

    val isPipe1 = fds.usePinned { pinned ->
        val p0: CPointer<IntVar> = pinned.addressOf(0)
        if (p0.pipe2(O_CLOEXEC) == 0) return@usePinned false

        pipe(p0).check()
        true
    }

    if (isPipe1) {
        fds.forEach { fd ->
            if (fcntl(fd, F_SETFD, FD_CLOEXEC) == 0) return@forEach

            val e = errnoToIOException(errno)
            if (close(fds[0]) == -1) {
                e.addSuppressed(errnoToIOException(errno))
            }
            if (close(fds[1]) == -1) {
                e.addSuppressed(errnoToIOException(errno))
            }
            throw e
        }
    }

    // This is for when the pipe is being utilized as a monitor for a newly
    // spawned process, such that we can await execve/execvpe success, or
    // child process exit due to failure.
    if (readEndNonBlock) {
        if (fcntl(fds[0], F_SETFL, O_NONBLOCK) != 0) {
            val e = errnoToIOException(errno)
            if (close(fds[0]) == -1) {
                e.addSuppressed(errnoToIOException(errno))
            }
            if (close(fds[1]) == -1) {
                e.addSuppressed(errnoToIOException(errno))
            }
            throw e
        }
    }

    return this(/* isPipe1 = */ isPipe1, /* fd0 = */ fds[0], /* fd1 = */ fds[1])
}

// returns 0 for success, -1 for failure
@OptIn(ExperimentalForeignApi::class)
internal expect inline fun CPointer<IntVar>.pipe2(
    flags: Int,
): Int
