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
import kotlinx.cinterop.AutofreeScope
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.NativePointed
import kotlinx.cinterop.memScoped
import platform.posix.pid_tVar

// macOS
@OptIn(ExperimentalForeignApi::class)
internal actual class PosixSpawnScope internal constructor(
    internal val attrs: CValuesRef<posix_spawnattr_tVar>,
    internal val fileActions: CValuesRef<posix_spawn_file_actions_tVar>,
    private val mem: MemScope,
): AutofreeScope() {

    actual override fun alloc(size: Long, align: Int): NativePointed = mem.alloc(size, align)
}

@OptIn(ExperimentalForeignApi::class)
@Throws(UnsupportedOperationException::class)
internal actual inline fun PosixSpawnScope.file_actions_addchdir_np(chdir: File): Int {
    throw UnsupportedOperationException("TODO: Check support macOS 10.15")
}

@OptIn(ExperimentalForeignApi::class)
internal actual inline fun PosixSpawnScope.file_actions_adddup2(fd: Int, newFd: Int): Int {
    return posix_spawn_file_actions_adddup2(fileActions, fd, newFd)
}

@OptIn(ExperimentalForeignApi::class)
internal actual inline fun PosixSpawnScope.spawn(
    program: String,
    pid: CValuesRef<pid_tVar>,
    argv: CValuesRef<CPointerVar<ByteVar>>,
    envp: CValuesRef<CPointerVar<ByteVar>>,
): Int = posix_spawn(pid, program, fileActions, attrs, argv, envp)

@OptIn(ExperimentalForeignApi::class)
internal actual inline fun PosixSpawnScope.spawn_p(
    program: String,
    pid: CValuesRef<pid_tVar>,
    argv: CValuesRef<CPointerVar<ByteVar>>,
    envp: CValuesRef<CPointerVar<ByteVar>>,
): Int = posix_spawnp(pid, program, fileActions, attrs, argv, envp)

@Throws(UnsupportedOperationException::class)
@OptIn(ExperimentalForeignApi::class)
internal actual inline fun <T: Any> posixSpawnScopeOrNull(
    requireChangeDir: Boolean,
    block: PosixSpawnScope.() -> T,
): T? {
    // TODO: Check support macOS 10.15
    if (requireChangeDir) return null

    return memScoped {
        val attrs = posix_spawnattr_init() ?: return@memScoped null
        val fileActions = posix_spawn_file_actions_init() ?: return@memScoped null
        val scope = PosixSpawnScope(attrs, fileActions, this)
        block(scope)
    }
}
