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
import io.matthewnelson.kmp.process.Stdio
import io.matthewnelson.kmp.process.internal.*
import io.matthewnelson.kmp.process.internal.stdio.StdioDescriptor.Companion.fdOpen
import io.matthewnelson.kmp.process.internal.stdio.StdioDescriptor.Pipe.Companion.fdOpen
import platform.posix.STDERR_FILENO
import platform.posix.STDIN_FILENO
import platform.posix.STDOUT_FILENO

internal class StdioHandle private constructor(
    internal val stdio: Stdio.Config,
    private val stdinFD: Closeable,
    private val stdoutFD: Closeable,
    private val stderrFD: Closeable,
): Closeable {

    override val isClosed: Boolean get() = stdinFD.isClosed && stdoutFD.isClosed && stderrFD.isClosed
    private val lock = newLock()
    private var threw: IOException? = null

    private val stdin by lazy(LazyThreadSafetyMode.NONE) {
        // This is invoked once and only once upon NativeProcess
        // instantiation (after fork occurs). We can do some descriptor
        // clean up here and close unneeded descriptors which were duped
        // in the child process and remain open over there.
        try {
            if (stdinFD is StdioDescriptor.Pipe) {
                // Leave write end open here in parent process
                stdinFD.read
            } else {
                stdinFD
            }.close()
        } catch (e: IOException) {
            threw?.let { e.addSuppressed(it) }
            threw = e
        }

        try {
            if (stdoutFD is StdioDescriptor.Pipe) {
                // Leave read end open here in parent process
                stdoutFD.write
            } else {
                stdoutFD
            }.close()
        } catch (e: IOException) {
            threw?.let { e.addSuppressed(it) }
            threw = e
        }

        try {
            if (stderrFD is StdioDescriptor.Pipe) {
                // Leave read end open here in parent process
                stderrFD.write
            } else {
                stdoutFD
            }.close()
        } catch (e: IOException) {
            threw?.let { e.addSuppressed(it) }
            threw = e
        }

        if (stdinFD !is StdioDescriptor.Pipe) return@lazy null
        if (stdinFD.write.isClosed) return@lazy null
        WriteStream.of(stdinFD)
    }

    private val stdout by lazy(LazyThreadSafetyMode.NONE) {
        if (stdoutFD !is StdioDescriptor.Pipe) return@lazy null
        if (stdoutFD.read.isClosed) return@lazy null
        ReadStream.of(stdoutFD)
    }

    private val stderr by lazy(LazyThreadSafetyMode.NONE) {
        if (stderrFD !is StdioDescriptor.Pipe) return@lazy null
        if (stderrFD.read.isClosed) return@lazy null
        ReadStream.of(stderrFD)
    }

    @Throws(IOException::class)
    internal fun dup2(action: (fd: Int, newFd: Int) -> IOException?) {
        lock.withLock {
            if (isClosed) throw IOException("StdioHandle is closed")

            try {
                arrayOf(
                    intArrayOf(stdinFD.dup2FD(isStdin = true), STDIN_FILENO),
                    intArrayOf(stdoutFD.dup2FD(isStdin = false), STDOUT_FILENO),
                    intArrayOf(stderrFD.dup2FD(isStdin = false), STDERR_FILENO),
                ).forEach { fds ->
                    // dup2 can return EINVAL if descriptors are the same.
                    if (fds[0] == fds[1]) return@forEach
                    action(fds[0], fds[1])?.let { throw it }
                }
            } catch (e: IOException) {
                try {
                    closeNoLock()
                } catch (ee: IOException) {
                    e.addSuppressed(ee)
                }
                throw e
            }
        }
    }

    internal fun stdinStream(): WriteStream? = lock.withLock { stdin }
    internal fun stdoutStream(): ReadStream? = lock.withLock { stdout }
    internal fun stderrStream(): ReadStream? = lock.withLock { stderr }

    @Throws(IOException::class)
    override fun close() {
        lock.withLock {
            closeNoLock()
        }
    }

    @Throws(IOException::class)
    private fun closeNoLock() {
        if (isClosed) return

        var threw: IOException? = threw

        try {
            stdinFD.close()
        } catch (e: IOException) {
            if (threw != null) e.addSuppressed(threw)
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

        // initialize them if they haven't been already.
        stdin
        stdout
        stderr

        if (threw != null) throw threw
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
                throw stdinFD.tryCloseSuppressed(e)
            }

            val stderrFD = try {
                when (val s = stderr) {
                    is Stdio.Inherit -> StdioDescriptor.STDERR
                    is Stdio.File -> if (isStderrSameFileAsStdout()) {
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

    @Throws(IOException::class)
    private inline fun Closeable.dup2FD(isStdin: Boolean): Int = when (this) {
        is StdioDescriptor -> withFd { it }
        is StdioDescriptor.Pipe -> (if (isStdin) read else write).withFd { it }
        // Will never occur
        else -> throw IOException("Closable was not a descriptor")
    }
}
