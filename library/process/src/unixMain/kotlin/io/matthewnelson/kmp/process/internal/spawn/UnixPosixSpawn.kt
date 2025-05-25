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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "KotlinRedundantDiagnosticSuppress", "NOTHING_TO_INLINE", "FunctionName")

package io.matthewnelson.kmp.process.internal.spawn

import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.FileNotFoundException
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.errnoToIOException
import io.matthewnelson.kmp.file.toFile
import io.matthewnelson.kmp.process.ProcessException
import io.matthewnelson.kmp.process.Signal
import io.matthewnelson.kmp.process.Stdio
import io.matthewnelson.kmp.process.internal.NativeProcess
import io.matthewnelson.kmp.process.internal.check
import io.matthewnelson.kmp.process.internal.stdio.StdioHandle.Companion.openHandle
import io.matthewnelson.kmp.process.internal.toArgv
import io.matthewnelson.kmp.process.internal.toEnvp
import io.matthewnelson.kmp.process.internal.tryCloseSuppressed
import kotlinx.cinterop.AutofreeScope
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.NativePointed
import kotlinx.cinterop.alloc
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.posix.pid_tVar

@OptIn(ExperimentalForeignApi::class)
internal expect class PosixSpawnScope: AutofreeScope {
    override fun alloc(size: Long, align: Int): NativePointed
}

@OptIn(ExperimentalForeignApi::class)
@Throws(UnsupportedOperationException::class)
internal expect inline fun PosixSpawnScope.file_actions_addchdir_np(chdir: File): Int

@OptIn(ExperimentalForeignApi::class)
internal expect inline fun PosixSpawnScope.file_actions_adddup2(fd: Int, newFd: Int): Int

@OptIn(ExperimentalForeignApi::class)
internal expect inline fun PosixSpawnScope.spawn(
    program: String,
    pid: CValuesRef<pid_tVar>,
    argv: CValuesRef<CPointerVar<ByteVar>>,
    envp: CValuesRef<CPointerVar<ByteVar>>,
): Int

@OptIn(ExperimentalForeignApi::class)
internal expect inline fun PosixSpawnScope.spawn_p(
    program: String,
    pid: CValuesRef<pid_tVar>,
    argv: CValuesRef<CPointerVar<ByteVar>>,
    envp: CValuesRef<CPointerVar<ByteVar>>,
): Int

/**
 * Returns `null` if platform support is not available, or if initialization calls of
 * `posix_spawnattr_init` or `posix_spawn_file_actions_init` failed.
 *
 * @param [requireChangeDir] If posix_spawn_file_actions_addchdir_np is required
 *
 * @throws [UnsupportedOperationException] if and only if on `iOS` and [requireChangeDir] is true.
 * */
@Throws(UnsupportedOperationException::class)
@OptIn(ExperimentalForeignApi::class)
internal expect inline fun <T: Any> posixSpawnScopeOrNull(
    requireChangeDir: Boolean,
    block: PosixSpawnScope.() -> T,
): T?

/**
 * Returns `null` if platform support is not available, or if initialization calls of
 * `posix_spawnattr_init` or `posix_spawn_file_actions_init` failed.
 *
 * @throws [IOException] if there was a failure to spawn the process
 * @throws [UnsupportedOperationException] if and only if on `iOS` and [chdir] is non-null.
 * */
@OptIn(ExperimentalForeignApi::class)
@Throws(IOException::class, UnsupportedOperationException::class)
internal inline fun posixSpawn(
    command: String,
    args: List<String>,
    chdir: File?,
    env: Map<String, String>,
    stdio: Stdio.Config,
    destroy: Signal,
    handler: ProcessException.Handler,
): NativeProcess? = posixSpawnScopeOrNull(requireChangeDir = chdir != null) {
    if (chdir != null) {
        file_actions_addchdir_np(chdir).check()
    }

    val handle = stdio.openHandle()

    // handle closes automatically on any failures
    handle.dup2(action = { fd, newFd ->
        // posix_spawn_file_actions_adddup2 returns a non-zero
        // value to indicate the error.
        when (val result = file_actions_adddup2(fd, newFd)) {
            0 -> null
            else -> errnoToIOException(result)
        }
    })

    val pid = try {
        // pre-setting to -1 will allow detection of
        // a post-fork step failure (the best we can do atm).
        val pidRef = alloc<pid_tVar>().apply { value = -1 }

        val argv = args.toArgv(command = command, scope = this)
        val envp = env.toEnvp(scope = this)

        val isCommandPathAbsolute = command.toFile().isAbsolute()
        if (isCommandPathAbsolute) {
            spawn(command, pidRef.ptr, argv, envp)
        } else {
            spawn_p(command, pidRef.ptr, argv, envp)
        }.check()

        // If there was a failure in the pre-exec or exec steps, the
        // pid reference will not be modified.
        //
        // Something like using an invalid directory location (non-existent)
        // for chdir would result in this scenario.
        val pid = pidRef.value
        if (pid == -1) {
            var msg = if (isCommandPathAbsolute) "posix_spawn" else "posix_spawnp"
            msg += " failed in pre-exec/exec steps."
            throw if (chdir != null && !chdir.exists()) {
                msg += " Directory specified does not exist"
                FileNotFoundException(msg)
            } else {
                msg += " Bad arguments for '$command'?"
                IOException(msg)
            }
        }
        pid
    } catch (e: IOException) {
        throw handle.tryCloseSuppressed(e)
    }

    NativeProcess(
        pid,
        handle,
        command,
        args,
        chdir,
        env,
        destroy,
        handler,
    )
}
