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
import io.matthewnelson.kmp.process.Stdio
import io.matthewnelson.kmp.process.internal.*
import io.matthewnelson.kmp.process.internal.Closable.Companion.tryCloseSuppressed
import io.matthewnelson.kmp.process.internal.Instance
import io.matthewnelson.kmp.process.internal.Lock
import io.matthewnelson.kmp.process.internal.ReadStream
import io.matthewnelson.kmp.process.internal.WriteStream
import io.matthewnelson.kmp.process.internal.stdio.StdioDescriptor.Companion.fdOpen
import io.matthewnelson.kmp.process.internal.stdio.StdioDescriptor.Pipe.Companion.fdOpen
import platform.posix.STDERR_FILENO
import platform.posix.STDIN_FILENO
import platform.posix.STDOUT_FILENO

internal class StdioHandle private constructor(
    internal val stdio: Stdio.Config,
    private val stdinFD: Closable,
    private val stdoutFD: Closable,
    private val stderrFD: Closable,
): Closable {

    override val isClosed: Boolean get() = stdinFD.isClosed && stdoutFD.isClosed && stderrFD.isClosed
    private val lock = Lock()

    private val stdin: Instance<WriteStream?> = Instance(create = {
        if (stdinFD.isClosed) return@Instance null
        if (stdinFD !is StdioDescriptor.Pipe) return@Instance null
        WriteStream.of(stdinFD)
    })

    private val stdout: Instance<ReadStream?> = Instance(create = {
        if (stdoutFD.isClosed) return@Instance null
        if (stdoutFD !is StdioDescriptor.Pipe) return@Instance null
        ReadStream.of(stdoutFD)
    })

    private val stderr: Instance<ReadStream?> = Instance(create = {
        if (stderrFD.isClosed) return@Instance null
        if (stderrFD !is StdioDescriptor.Pipe) return@Instance null
        ReadStream.of(stderrFD)
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
                try {
                    closeNoLock()
                } catch (e: IOException) {
                    e.addSuppressed(e)
                }

                throw e
            }
        }
    }

    internal fun stdinStream(): WriteStream? = lock.withLock { stdin.getOrCreate() }
    internal fun stdoutReader(): ReadStream? = lock.withLock { stdout.getOrCreate() }
    internal fun stderrReader(): ReadStream? = lock.withLock { stderr.getOrCreate() }

    @Throws(IOException::class)
    override fun close() {
        lock.withLock {
            closeNoLock()
        }
    }

    @Throws(IOException::class)
    private fun closeNoLock() {
        if (isClosed) return

        var threw: IOException? = null

        try {
            stdinFD.close()
        } catch (e: IOException) {
            threw = e
        }
        try {
            stdoutFD.close()
        } catch (e: IOException) {
            if (threw != null) e.addSuppressed(threw)
            threw = e
        }
        try {
            stderrFD.close()
        } catch (e: IOException) {
            if (threw != null) e.addSuppressed(threw)
            threw = e
        }

        if (threw == null) return
        throw threw
    }

    internal companion object {

        @Throws(IOException::class)
        internal fun Stdio.Config.openHandle(): StdioHandle {
            val stdinFD = when (val s = stdin) {
                is Stdio.Inherit -> StdioDescriptor.STDIN
                is Stdio.File -> s.fdOpen(isStdin = true)
                is Stdio.Pipe -> s.fdOpen()
            }

            val stdoutFD = try {
                when (val s = stdout) {
                    is Stdio.Inherit -> StdioDescriptor.STDOUT
                    is Stdio.File -> s.fdOpen(isStdin = false)
                    is Stdio.Pipe -> s.fdOpen()
                }
            } catch (e: IOException) {
                stdinFD.tryCloseSuppressed(e)
                throw e
            }

            val stderrFD = try {
                when (val s = stderr) {
                    is Stdio.Inherit -> StdioDescriptor.STDERR
                    is Stdio.File -> if (isStderrSameFileAsStdout) {
                        stdoutFD
                    } else {
                        s.fdOpen(isStdin = false)
                    }
                    is Stdio.Pipe -> s.fdOpen()
                }
            } catch (e: IOException) {
                stdinFD.tryCloseSuppressed(e)
                stdoutFD.tryCloseSuppressed(e)
                throw e
            }

            return StdioHandle(this, stdinFD, stdoutFD, stderrFD)
        }
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun Closable.dup2FD(isStdin: Boolean): Int = when (this) {
        is StdioDescriptor -> withFd { it }
        is StdioDescriptor.Pipe -> (if (isStdin) read else write).withFd { it }
        // Will never occur
        else -> throw IOException("Closable was not a descriptor")
    }
}
