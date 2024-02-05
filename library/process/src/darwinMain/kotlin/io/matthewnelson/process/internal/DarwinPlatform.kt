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

import io.matthewnelson.process.*
import kotlinx.cinterop.*
import platform.Foundation.NSProcessInfo
import platform.posix.pid_tVar

@Suppress("NOTHING_TO_INLINE")
internal actual inline fun Process.Builder.parentEnvironment(): MutableMap<String, String> {
    // NSDictionary<NSString *, NSString *>
    val env = NSProcessInfo.processInfo.environment
    val map = LinkedHashMap<String, String>(env.size, 1.0F)
    for (entry in env.entries) {
        val key = entry.key ?: continue
        val value = entry.value ?: continue
        map[key.toString()] = value.toString()
    }
    return map
}

@Suppress("NOTHING_TO_INLINE")
@OptIn(ExperimentalForeignApi::class)
internal actual inline fun MemScope.posixSpawn(
    command: String,
    pid: CValuesRef<pid_tVar>,
    fileActions: PosixSpawnFileActions,
    attrs: PosixSpawnAttrs,
    argv: CValuesRef<CPointerVar<ByteVar>>,
    envp: CValuesRef<CPointerVar<ByteVar>>,
): Int = posix_spawn(pid, command, fileActions.ref, attrs.ref, argv, envp)

@Suppress("NOTHING_TO_INLINE")
@OptIn(ExperimentalForeignApi::class)
internal actual inline fun MemScope.posixSpawnP(
    command: String,
    pid: CValuesRef<pid_tVar>,
    fileActions: PosixSpawnFileActions,
    attrs: PosixSpawnAttrs,
    argv: CValuesRef<CPointerVar<ByteVar>>,
    envp: CValuesRef<CPointerVar<ByteVar>>,
): Int = posix_spawnp(pid, command, fileActions.ref, attrs.ref, argv, envp)
