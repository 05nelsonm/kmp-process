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
import io.matthewnelson.kmp.process.StdinStream
import io.matthewnelson.kmp.process.Stdio
import io.matthewnelson.kmp.process.internal.Instance
import io.matthewnelson.kmp.process.internal.Lock
import io.matthewnelson.kmp.process.internal.stdio.StdioDescriptor.Pair.Companion.fdOpen
import io.matthewnelson.kmp.process.internal.stdio.StdioDescriptor.Single.Companion.fdOpen
import platform.posix.STDERR_FILENO
import platform.posix.STDIN_FILENO
import platform.posix.STDOUT_FILENO
import kotlin.concurrent.Volatile

internal class StdioHandle private constructor(
    internal val stdio: Stdio.Config,
    private val stdinFD: StdioDescriptor,
    private val stdoutFD: StdioDescriptor,
    private val stderrFD: StdioDescriptor,
) {

    @Volatile
    internal var isClosed = false
        private set
    private val lock = Lock()

    private val stdin: Instance<StdinStream?> = Instance(create = {
        if (isClosed) return@Instance null
        if (stdinFD !is StdioDescriptor.Pair) return@Instance null
        RealStdinStream(stdinFD)
    })

    private val stdout: Instance<StdioReader?> = Instance(create = {
        if (isClosed) return@Instance null
        if (stdoutFD !is StdioDescriptor.Pair) return@Instance null
        StdioReader(stdoutFD)
    })

    private val stderr: Instance<StdioReader?> = Instance(create = {
        if (isClosed) return@Instance null
        if (stderrFD !is StdioDescriptor.Pair) return@Instance null
        StdioReader(stderrFD)
    })

    @Throws(IOException::class)
    internal fun dup2(action: (fd: Int, newFd: Int) -> IOException?) {
        lock.withLock {
            if (isClosed) throw IOException("StdioHandle is closed")

            try {
                action(stdinFD.dup2FD(isStdin = true), STDIN_FILENO)?.let { throw it }
                action(stdoutFD.dup2FD(isStdin = false), STDOUT_FILENO)?.let { throw it }
                action(stderrFD.dup2FD(isStdin = false), STDERR_FILENO)?.let { throw it }
            } catch (e: IOException) {
                closeNoLock()
                throw e
            }
        }
    }

    internal fun stdinStream(): StdinStream? = lock.withLock { stdin.getOrCreate() }
    internal fun stdoutReader(): StdioReader? = lock.withLock { stdout.getOrCreate() }
    internal fun stderrReader(): StdioReader? = lock.withLock { stderr.getOrCreate() }

    internal fun close() {
        // subsequent calls to close will do nothing, as
        // we do not want to call close on potentially
        // recycled descriptors.
        if (isClosed) return

        lock.withLock {
            closeNoLock()
        }
    }

    private fun closeNoLock() {
        if (isClosed) return
        isClosed = true

        stdinFD.close()
        stdoutFD.close()
        stderrFD.close()
    }

    internal companion object {

        @Throws(IOException::class)
        internal fun Stdio.Config.openHandle(): StdioHandle {
            val stdinFD = when (val s = stdin) {
                is Stdio.Inherit -> StdioDescriptor.Single.Stdin
                is Stdio.File -> s.fdOpen(isStdin = true)
                is Stdio.Pipe -> s.fdOpen()
            }

            val stdoutFD = try {
                when (val s = stdout) {
                    is Stdio.Inherit -> StdioDescriptor.Single.Stdout
                    is Stdio.File -> s.fdOpen(isStdin = false)
                    is Stdio.Pipe -> s.fdOpen()
                }
            } catch (e: IOException) {
                stdinFD.close()
                throw e
            }

            val stderrFD = try {
                when (val s = stderr) {
                    is Stdio.Inherit -> StdioDescriptor.Single.Stderr
                    is Stdio.File -> if (isStderrSameFileAsStdout) {
                        stdoutFD
                    } else {
                        s.fdOpen(isStdin = false)
                    }
                    is Stdio.Pipe -> s.fdOpen()
                }
            } catch (e: IOException) {
                stdinFD.close()
                stdoutFD.close()
                throw e
            }

            return StdioHandle(this, stdinFD, stdoutFD, stderrFD)
        }
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun StdioDescriptor.dup2FD(isStdin: Boolean): Int = when (this) {
        is StdioDescriptor.Single -> fd
        is StdioDescriptor.Pair -> if (isStdin) fdRead else fdWrite
    }
}
