/*
 * Copyright (c) 2024 Matthew Nelson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
@file:Suppress("KotlinRedundantDiagnosticSuppress")

package io.matthewnelson.process.internal

import io.matthewnelson.process.Process
import io.matthewnelson.process.internal.PosixSpawnAttrs.Companion.posixSpawnAttrInit
import io.matthewnelson.process.internal.PosixSpawnFileActions.Companion.posixSpawnFileActionsInit
import kotlinx.cinterop.*
import platform.posix.*

@OptIn(ExperimentalForeignApi::class)
internal actual fun createProcess(
    command: String,
    args: List<String>,
    env: Map<String, String>,
): Process = memScoped {

    // TODO: pipes Issue #2

    val fileActions: PosixSpawnFileActions = posixSpawnFileActionsInit().let { (result, ref) ->
        if (result != 0) {
            // TODO: error to stdioerr
            val errno = errno
            val message = strerror(errno)?.toKString() ?: "errno: $errno"
            println("ERR_FILE_ACTIONS[$errno]: $message")
            return@memScoped NativeProcess(-1, command, args, env)
        }
        ref
    }

    val attrs: PosixSpawnAttrs = posixSpawnAttrInit().let { (result, ref) ->
        if (result != 0) {
            // TODO: error to stdioerr
            val errno = errno
            val message = strerror(errno)?.toKString() ?: "errno: $errno"
            println("ERR_ATTRS[$errno]: $message")
            return@memScoped NativeProcess(-1, command, args, env)
        }
        ref
    }

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
        // relative path, utilize posix_spawnp
        posixSpawnP(command, pid.ptr, fileActions, attrs, argv, envp)
    }
    if (result != 0) {
        // TODO: error to stdioerr
        val errno = errno
        val message = strerror(errno)?.toKString() ?: "errno: $errno"
        println("ERR_SPAWN[$errno]: $message")
    }

    NativeProcess(pid.value, command, args, env)
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
