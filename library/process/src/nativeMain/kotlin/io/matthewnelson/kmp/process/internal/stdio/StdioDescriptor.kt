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
import io.matthewnelson.kmp.file.path
import io.matthewnelson.kmp.process.Stdio
import io.matthewnelson.kmp.process.internal.STDIO_NULL
import io.matthewnelson.kmp.process.internal.check
import platform.posix.*
import kotlin.concurrent.Volatile

internal sealed class StdioDescriptor private constructor() {

    @Volatile
    internal var isClosed: Boolean = false
        private set

    internal fun close() {
        if (isClosed) return

        when (this) {
            is Single -> when (fd) {
                Single.Stdin.fd,
                Single.Stdout.fd,
                Single.Stderr.fd -> { /* do not close */ }
                else -> close(fd)
            }
            is Pair -> {
                close(fdRead)
                close(fdWrite)
            }
        }

        isClosed = true
    }

    internal class Single private constructor(
        internal val fd: Int,
    ): StdioDescriptor() {

        internal companion object {

            internal val Stdin: Single = Single(STDIN_FILENO)
            internal val Stdout: Single = Single(STDOUT_FILENO)
            internal val Stderr: Single = Single(STDERR_FILENO)

            @Throws(IOException::class)
            internal fun Stdio.File.fdOpen(isStdin: Boolean): Single {
                var mode = 0
                var flags = if (isStdin) O_RDONLY else O_WRONLY

                if (!isStdin && append) {
                    flags = flags or O_APPEND
                }

                flags = flags.orOCloExec()

                if (!isStdin && file != STDIO_NULL) {
                    // File may need to be created if it does not currently exist
                    mode = S_IRUSR or S_IWUSR or S_IRGRP or S_IWGRP or S_IROTH
                    flags = flags or O_CREAT
                }

                val fd = open(file.path, flags, mode).check()
                return Single(fd)
            }
        }
    }

    internal class Pair private constructor(
        internal val fdRead: Int,
        internal val fdWrite: Int,
    ): StdioDescriptor() {

        internal companion object {

            @Throws(IOException::class)
            internal fun Stdio.Pipe.fdOpen(): Pair = ::Pair.fdOpen(this)
        }
    }
}

@Suppress("NOTHING_TO_INLINE")
internal expect inline fun Int.orOCloExec(): Int

@Throws(IOException::class)
internal expect fun ((fdRead: Int, fdWrite: Int) -> StdioDescriptor.Pair).fdOpen(
    stdio: Stdio.Pipe,
): StdioDescriptor.Pair
