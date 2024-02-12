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

import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.toFile
import io.matthewnelson.kmp.process.Signal
import io.matthewnelson.kmp.process.Stdio
import io.matthewnelson.kmp.process.internal.PosixSpawnAttrs.Companion.posixSpawnAttrInit
import io.matthewnelson.kmp.process.internal.PosixSpawnFileActions.Companion.posixSpawnFileActionsInit
import kotlinx.cinterop.*
import platform.posix.*

internal actual val STDIO_NULL: File = "/dev/null".toFile()

@OptIn(ExperimentalForeignApi::class)
@Throws(IOException::class, UnsupportedOperationException::class)
internal actual fun MemScope.posixSpawn(
    command: String,
    args: List<String>,
    env: Map<String, String>,
    stdio: Stdio.Config,
    destroy: Signal,
): NativeProcess {
    val fileActions = posixSpawnFileActionsInit()
    val attrs = posixSpawnAttrInit()

    // TODO: try chg dir first
    //  - Linux glibc < 2.24 throw UnsupportedOperationException
    //  .
    //  - iOS throw IOException (it's not supported, but we want
    //    to stop early w/o trying fork & exec b/c that is not
    //    supported on iOS either.

    // TODO: streams Issue #2

    val pid = alloc<pid_tVar>()

    // null terminated c-string array
    val argv = allocArray<CPointerVar<ByteVar>>(args.size + 2).apply {
        // First argument for posix_spawn's argv should be the executable name.
        // If command is the absolute path to the executable, take the final
        // path argument after last separator.
        this[0] = command.substringAfterLast('/').cstr.ptr

        var i = 1
        val iterator = args.iterator()
        while (iterator.hasNext()) {
            this[i++] = iterator.next().cstr.ptr
        }

        this[i] = null
    }

    // null terminated c-string array
    val envp = allocArray<CPointerVar<ByteVar>>(env.size + 1).apply {
        var i = 0
        val iterator = env.entries.iterator()
        while (iterator.hasNext()) {
            this[i++] = iterator.next().toString().cstr.ptr
        }

        this[i] = null
    }

    val result = if (command.startsWith('/')) {
        // Absolute path, utilize posix_spawn
        posixSpawn(command, pid.ptr, fileActions, attrs, argv, envp)
    } else {
        // relative path or program name, utilize posix_spawnp
        posixSpawnP(command, pid.ptr, fileActions, attrs, argv, envp)
    }

    // TODO: close things
    result.check()

    return NativeProcess(
        pid.value,
        command,
        args,
        env,
        stdio,
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
