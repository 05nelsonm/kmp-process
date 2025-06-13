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
import io.matthewnelson.kmp.process.internal.check
import kotlinx.cinterop.*
import platform.posix.*

@Suppress("NOTHING_TO_INLINE")
internal actual inline fun Int.orOCloExec(): Int = this or O_CLOEXEC

@Throws(IOException::class)
@OptIn(ExperimentalForeignApi::class)
internal actual fun ((fdRead: Int, fdWrite: Int) -> StdioDescriptor.Pipe).fdOpen(
    nonBlock: Boolean,
): StdioDescriptor.Pipe {
    val pipeFD = IntArray(2) { -1 }

    val isPipe2 = pipeFD.usePinned { pinned ->
        val p0: CPointer<IntVar> = pinned.addressOf(0)
        var flags = O_CLOEXEC
        if (nonBlock) flags = flags or O_NONBLOCK
        if (p0.pipe2(flags) == 0) return@usePinned true

        pipe(p0).check()
        false
    }

    if (!isPipe2) {
        arrayOf(
            // Want to ensure FD_CLOEXEC is configured first and foremost, before
            // configuring anything else. Unfortunately this cannot be done atomically
            // like with pipe2.
            F_SETFD to FD_CLOEXEC,
            if (nonBlock) F_SETFL to O_NONBLOCK else null,
        ).forEach {
            if (it == null) return@forEach

            val (cmd, flags) = it

            pipeFD.forEach pipeFD@ { fd ->
                if (fcntl(fd, cmd, flags) == 0) return@pipeFD

                val e = errnoToIOException(errno)
                if (close(pipeFD[0]) == -1) {
                    val ee = errnoToIOException(errno)
                    e.addSuppressed(ee)
                }
                if (close(pipeFD[1]) == -1) {
                    val ee = errnoToIOException(errno)
                    e.addSuppressed(ee)
                }
                throw e
            }
        }
        pipeFD.forEach { fd ->
            var result = fcntl(fd, F_SETFD, FD_CLOEXEC)
            if (result == 0 && nonBlock) {
                result = fcntl(fd, F_SETFL, O_NONBLOCK)
            }
            if (result != 0) {
                val e = errnoToIOException(errno)
                if (close(pipeFD[0]) == -1) {
                    val ee = errnoToIOException(errno)
                    e.addSuppressed(ee)
                }
                if (close(pipeFD[1]) == -1) {
                    val ee = errnoToIOException(errno)
                    e.addSuppressed(ee)
                }
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
