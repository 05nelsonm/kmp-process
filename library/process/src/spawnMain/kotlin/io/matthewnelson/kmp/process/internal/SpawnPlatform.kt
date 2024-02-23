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

package io.matthewnelson.kmp.process.internal

import io.matthewnelson.kmp.file.*
import io.matthewnelson.kmp.process.Signal
import io.matthewnelson.kmp.process.internal.PosixSpawnAttrs.Companion.posixSpawnAttrInit
import io.matthewnelson.kmp.process.internal.PosixSpawnFileActions.Companion.posixSpawnFileActionsInit
import io.matthewnelson.kmp.process.internal.stdio.StdioHandle
import kotlinx.cinterop.*
import platform.posix.*

@OptIn(ExperimentalForeignApi::class)
@Throws(IOException::class, UnsupportedOperationException::class)
internal actual fun posixSpawn(
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
    } catch (e: NullPointerException) {
        // gnu_get_libc_version on Linux returned null
        throw UnsupportedOperationException("Failed to retrieve glibc version", e)
    }

    val pid = memScoped {
        val fileActions = posixSpawnFileActionsInit()

        // TODO: Issue #15
        //  try addchdir_np (iOS/Linux throws IOException)

        val attrs = posixSpawnAttrInit()

        handle.dup2(action = { fd, newFd ->
            // posix_spawn_file_actions_adddup2 returns a non-zero
            // value to indicate the error.
            @OptIn(DelicateFileApi::class, ExperimentalForeignApi::class)
            when (val result = fileActions.adddup2(fd, newFd)) {
                0 -> null
                else -> errnoToIOException(result)
            }
        })

        val pid = alloc<pid_tVar>()

        val argv = args.toArgv(
            // First argument for posix_spawn's argv should be the executable name.
            // If command is the absolute path to the executable, take the final
            // path argument after last separator.
            argv0 = command.substringAfterLast(SysPathSep),
            scope = this,
        )

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

@Suppress("NOTHING_TO_INLINE")
@OptIn(ExperimentalForeignApi::class)
internal expect inline fun MemScope.posixSpawn(
    command: String,
    pid: CValuesRef<pid_tVar>,
    fileActions: PosixSpawnFileActions,
    attrs: PosixSpawnAttrs,
    argv: CValuesRef<CPointerVar<ByteVar>>,
    envp: CValuesRef<CPointerVar<ByteVar>>,
): Int

@Suppress("NOTHING_TO_INLINE")
@OptIn(ExperimentalForeignApi::class)
internal expect inline fun MemScope.posixSpawnP(
    command: String,
    pid: CValuesRef<pid_tVar>,
    fileActions: PosixSpawnFileActions,
    attrs: PosixSpawnAttrs,
    argv: CValuesRef<CPointerVar<ByteVar>>,
    envp: CValuesRef<CPointerVar<ByteVar>>,
): Int
