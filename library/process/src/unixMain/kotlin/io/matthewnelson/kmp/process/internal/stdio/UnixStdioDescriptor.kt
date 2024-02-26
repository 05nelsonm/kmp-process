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
@file:Suppress("KotlinRedundantDiagnosticSuppress")

package io.matthewnelson.kmp.process.internal.stdio

import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.errnoToIOException
import io.matthewnelson.kmp.process.Stdio
import io.matthewnelson.kmp.process.internal.check
import kotlinx.cinterop.*
import platform.posix.*

@Suppress("NOTHING_TO_INLINE")
internal actual inline fun Int.orOCloExec(): Int = this or O_CLOEXEC

@Throws(IOException::class)
@OptIn(ExperimentalForeignApi::class)
internal actual fun ((fdRead: Int, fdWrite: Int) -> StdioDescriptor.Pair).fdOpen(
    stdio: Stdio.Pipe,
): StdioDescriptor.Pair {
    val pipeFD = IntArray(2) { -1 }

    val isPipe2 = pipeFD.usePinned { pinned ->
        val p0: CPointer<IntVar> = pinned.addressOf(0)

        if (p0.pipe2(O_CLOEXEC) == 0) return@usePinned true

        pipe(p0).check()
        false
    }

    if (!isPipe2) {
        pipeFD.forEach { fd ->
//            val prev = fcntl(fd, F_GETFD)
//            if (prev < 0) {\
//                val e = errnoToIOException(errno)
//                close(pipeFD[0])
//                close(pipeFD[1])
//                throw e
//            }

            // no need to check F_GETFD b/c it was just
            // created via pipe1 with no flags set.
            val result = fcntl(fd, F_SETFD, FD_CLOEXEC)
            if (result != 0) {
                val e = errnoToIOException(errno)
                close(pipeFD[0])
                close(pipeFD[1])
                throw e
            }
        }
    }

    return this(/*fdRead =*/ pipeFD[0],/* fdWrite =*/ pipeFD[1])
}

// returns 0 for success, -1 for failure
@Suppress("NOTHING_TO_INLINE")
@OptIn(ExperimentalForeignApi::class)
internal expect inline fun CPointer<IntVar>.pipe2(
    flags: Int,
): Int
