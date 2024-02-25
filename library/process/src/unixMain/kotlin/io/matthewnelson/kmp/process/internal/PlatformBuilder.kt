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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "KotlinRedundantDiagnosticSuppress")

package io.matthewnelson.kmp.process.internal

import io.matthewnelson.kmp.file.*
import io.matthewnelson.kmp.process.Output
import io.matthewnelson.kmp.process.Process
import io.matthewnelson.kmp.process.Signal
import io.matthewnelson.kmp.process.Stdio
import io.matthewnelson.kmp.process.internal.fork.dup2
import io.matthewnelson.kmp.process.internal.fork.execve
import io.matthewnelson.kmp.process.internal.fork.fork
import io.matthewnelson.kmp.process.internal.spawn.GnuLibcVersion
import io.matthewnelson.kmp.process.internal.spawn.PosixSpawnAttrs.Companion.posixSpawnAttrInit
import io.matthewnelson.kmp.process.internal.spawn.PosixSpawnFileActions.Companion.posixSpawnFileActionsInit
import io.matthewnelson.kmp.process.internal.spawn.posixSpawn
import io.matthewnelson.kmp.process.internal.spawn.posixSpawnP
import io.matthewnelson.kmp.process.internal.stdio.StdioHandle
import io.matthewnelson.kmp.process.internal.stdio.StdioHandle.Companion.openHandle
import kotlinx.cinterop.*
import platform.posix.errno
import platform.posix.exit
import platform.posix.getpid
import platform.posix.pid_tVar

// unixMain
internal actual class PlatformBuilder private actual constructor() {

    internal actual val env: MutableMap<String, String> by lazy {
        parentEnvironment()
    }

    @Throws(IOException::class)
    internal actual fun output(
        command: String,
        args: List<String>,
        env: Map<String, String>,
        stdio: Stdio.Config,
        options: Output.Options,
        destroy: Signal,
    ): Output = blockingOutput(command, args, env, stdio, options, destroy)

    @Throws(IOException::class)
    internal actual fun spawn(
        command: String,
        args: List<String>,
        env: Map<String, String>,
        stdio: Stdio.Config,
        destroy: Signal,
    ): Process {

        val handle = stdio.openHandle()

        try {
            return posixSpawn(command, args, env, handle, destroy)
        } catch (_: UnsupportedOperationException) {
            /* ignore and try fork/exec */
        } catch (e: IOException) {
            handle.close()
            throw e
        }

        try {
            return forkExec(command, args, env, handle, destroy)
        } catch (e: Exception) {
            handle.close()
            throw e.wrapIOException()
        }
    }

    @OptIn(DelicateFileApi::class, ExperimentalForeignApi::class)
    @Throws(IOException::class, UnsupportedOperationException::class)
    internal fun posixSpawn(
        command: String,
        args: List<String>,
        env: Map<String, String>,
        handle: StdioHandle,
        destroy: Signal,
    ): NativeProcess {
        try {
            GnuLibcVersion.check {
                if (!isAtLeast(major = 2u, minor = 24u)) {
                    // Only Linux glibc 2.24+ posix_spawn supports returning ENOENT
                    // fall back to fork & exec
                    throw UnsupportedOperationException("Unsupported Linux $this")
                }
                // TODO: Issue #15
                //  if addchdir_np needed, glibc 2.29+ required
            }
        } catch (_: NullPointerException) {
            // gnu_get_libc_version on Linux returned null
            throw UnsupportedOperationException("gnu_get_libc_version returned null")
        }

        val pid = memScoped {
            val fileActions = posixSpawnFileActionsInit()

            // TODO: Issue #15
            //  try addchdir_np (iOS/Linux throws IOException)

            val attrs = posixSpawnAttrInit()

            handle.dup2(action = { fd, newFd ->
                // posix_spawn_file_actions_adddup2 returns a non-zero
                // value to indicate the error.
                when (val result = fileActions.adddup2(fd, newFd)) {
                    0 -> null
                    else -> errnoToIOException(result)
                }
            })

            val pid = alloc<pid_tVar>()

            val argv = args.toArgv(command = command, scope = this)
            val envp = env.toEnvp(scope = this)

            // TODO: Check relative paths like ../program and how
            //  posix_spawnp functions with that. It "should" work,
            //  but darwin seems like there is a bug if utilized with
            //  addchdir_np. May be necessary to always convert to
            //  absolute file path if command.contains(SysPathSep) is
            //  true?
            if (command.startsWith(SysPathSep)) {
                // Absolute path, utilize posix_spawn
                posixSpawn(command, pid.ptr, fileActions, attrs, argv, envp)
            } else {
                // relative path or program name, utilize posix_spawnp
                posixSpawnP(command, pid.ptr, fileActions, attrs, argv, envp)
            }.check()

            pid.value
        }

        return NativeProcess(
            pid,
            handle,
            command,
            args,
            env,
            destroy,
        )
    }

    @OptIn(DelicateFileApi::class, ExperimentalForeignApi::class)
    @Throws(IOException::class, UnsupportedOperationException::class)
    internal fun forkExec(
        command: String,
        args: List<String>,
        env: Map<String, String>,
        handle: StdioHandle,
        destroy: Signal,
    ): NativeProcess {
        val pid = fork().check()

        if (pid == 0) {
            try {
                handle.dup2 { fd, newFd ->
                    when (dup2(fd, newFd)) {
                        0 -> null
                        else -> errnoToIOException(errno)
                    }
                }
            } catch (e: IOException) {
                // TODO: write errno to output?
                exit(errno)
            }

            val result = memScoped {
                val argv = args.toArgv(command = command, scope = this)
                val envp = env.toEnvp(scope = this)

                execve(command, argv, envp)
            }

            val status = if (result == -1) errno else 0
            // TODO: write errno to output?
            exit(status)
        }

        return NativeProcess(
            pid,
            handle,
            command,
            args,
            env,
            destroy,
        )
    }

    internal actual companion object {

        internal actual fun get(): PlatformBuilder = PlatformBuilder()

        internal actual fun myPid(): Int = getpid()
    }
}
