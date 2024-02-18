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

internal sealed class StdioDescriptor private constructor() {

    // Does not track if descriptors have been closed
    // already. That is done in bulk by StdioHandle
    internal fun close() {
        when (this) {
            is Single -> listOf(fd)
            is Pair -> listOf(fdRead, fdWrite)
        }.forEach { fd ->
            when (fd) {
                STDIN_FILENO,
                STDOUT_FILENO,
                STDERR_FILENO -> return@forEach
                else -> close(fd)
            }
        }
    }

    internal class Single private constructor(
        internal val fd: Int,
    ): StdioDescriptor() {

        internal companion object {

            internal val Stdin: Single get() = Single(STDIN_FILENO)
            internal val Stdout: Single get() = Single(STDOUT_FILENO)
            internal val Stderr: Single get() = Single(STDOUT_FILENO)

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
