/*
 * Copyright (c) 2025 Matthew Nelson
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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package io.matthewnelson.kmp.process.internal

import io.matthewnelson.kmp.file.AccessDeniedException
import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.FileNotFoundException
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.errnoToIOException
import io.matthewnelson.kmp.file.toFile
import io.matthewnelson.kmp.process.Output
import io.matthewnelson.kmp.process.Process
import io.matthewnelson.kmp.process.ProcessException
import io.matthewnelson.kmp.process.Signal
import io.matthewnelson.kmp.process.Stdio
import io.matthewnelson.kmp.process.internal.spawn.posixSpawn
import io.matthewnelson.kmp.process.internal.stdio.StdioDescriptor.Pipe.Companion.fdOpen
import io.matthewnelson.kmp.process.internal.stdio.StdioHandle.Companion.openHandle
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UnsafeNumber
import org.kotlincrypto.bitops.endian.Endian.Big.beIntAt
import platform.posix.EACCES
import platform.posix.ENOENT
import platform.posix.EPERM
import platform.posix.fork

// unixForkMain
internal actual class PlatformBuilder private actual constructor() {

    internal var usePosixSpawn: Boolean = true

    internal actual val env: MutableMap<String, String> by lazy { parentEnvironment() }

    @Throws(IOException::class)
    internal actual fun output(
        command: String,
        args: List<String>,
        chdir: File?,
        env: Map<String, String>,
        stdio: Stdio.Config,
        options: Output.Options,
        destroy: Signal,
    ): Output = blockingOutput(command, args, chdir, env, stdio, options, destroy)

    @Throws(IOException::class)
    internal actual fun spawn(
        command: String,
        args: List<String>,
        chdir: File?,
        env: Map<String, String>,
        stdio: Stdio.Config,
        destroy: Signal,
        handler: ProcessException.Handler,
    ): Process {
        val process = if (usePosixSpawn) {
            posixSpawn(
                command,
                args,
                chdir,
                env,
                stdio,
                destroy,
                handler,
            )
        } else {
            null
        }

        if (process != null) return process

        // posix_spawn was:
        //  - Not supported
        //  - There was some sort of initialization failure
        //  - It was disabled by usePosixSpawn variable
        //
        // Try fork/exec.
        return forkExec(command, args, chdir, env, stdio, destroy, handler)
    }

    @Throws(IOException::class)
    @OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)
    private fun forkExec(
        command: String,
        args: List<String>,
        chdir: File?,
        env: Map<String, String>,
        stdio: Stdio.Config,
        destroy: Signal,
        handler: ProcessException.Handler,
    ): NativeProcess {
        val handle = stdio.openHandle()

        val pipe = try {
            Stdio.Pipe.fdOpen(readEndNonBlock = true)
        } catch (e: IOException) {
            throw handle.tryCloseSuppressed(e)
        }

        val pid = try {
            fork().check()
        } catch (e: IOException) {
            handle.tryCloseSuppressed(e)
            pipe.tryCloseSuppressed(e)
            throw e
        }

        if (pid == 0) {
            ChildProcess(pid, pipe, handle, command, args, chdir, env)
        }

        // Parent process
        val p = NativeProcess(
            pid,
            handle,
            command,
            args,
            chdir,
            env,
            destroy,
            handler,
        )

        val buf = ByteArray(5)
        val read = p.awaitExecOrFailure(buf, pipe, onNonZeroExitCode = { null })

        val error = when (read) {
            // execve successful and CLOEXEC pipe's write end
            // was closed in the child process, resulting in the
            // read end here in the parent stopping.
            0 -> null

            // Something happened in the child process
            buf.size -> {
                val type = when (buf[4]) {
                    ChildProcess.ERR_DUP2 -> "dup2"
                    ChildProcess.ERR_FD_CLOEXEC -> "fd_cloexec"
                    ChildProcess.ERR_CHDIR -> "chdir"
                    ChildProcess.ERR_SIG_MASK -> "sigmask"
                    ChildProcess.ERR_EXEC -> "exec"
                    else -> null
                }

                if (type == null) {
                    IOException("CLOEXEC pipe validation check failure")
                } else {
                    val errno = buf.beIntAt(0)
                    var msg = "Child process $type failure."
                    errnoToIOException(errno).message?.let { msg += " $it" }
                    when (errno) {
                        ENOENT -> FileNotFoundException(msg)
                        EACCES, EPERM -> AccessDeniedException(command.toFile(), null, msg)
                        else -> IOException(msg)
                    }
                }
            }

            // This should only ever be the scenario when pipe1 is being used (iOS/macOS)
            else -> when (p.exitCodeOrNull()) {
                // pipe1 and we timed-out waiting for the read
                ChildProcess.EXIT_CODE -> IOException("Invalid read[$read] on CLOEXEC pipe")
                // Either it's still running (null) or the program
                // exited by some manner other than 127.
                else -> null
            }
        }

        if (error != null) throw p.destroySuppressed(error)

        return p
    }

    internal actual companion object {
        internal actual fun get(): PlatformBuilder = PlatformBuilder()
    }
}
