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

package io.matthewnelson.kmp.process.internal.spawn

import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.absolutePath
import kotlinx.cinterop.*
import platform.linux.posix_spawn
import platform.posix.pid_tVar

@Suppress("NOTHING_TO_INLINE")
@OptIn(ExperimentalForeignApi::class)
internal actual inline fun MemScope.posixSpawn(
    program: File,
    pid: CValuesRef<pid_tVar>,
    fileActions: PosixSpawnFileActions,
    attrs: PosixSpawnAttrs,
    argv: CValuesRef<CPointerVar<ByteVar>>,
    envp: CValuesRef<CPointerVar<ByteVar>>,
): Int = posix_spawn(pid, program.absolutePath, fileActions.ref, attrs.ref, argv, envp)
