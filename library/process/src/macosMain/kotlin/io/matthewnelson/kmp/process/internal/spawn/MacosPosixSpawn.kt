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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "NOTHING_TO_INLINE", "FunctionName")
@file:OptIn(DoNotReferenceDirectly::class)

package io.matthewnelson.kmp.process.internal.spawn

import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.path
import io.matthewnelson.kmp.process.internal.DoNotReferenceDirectly
import kotlinx.cinterop.AutofreeScope
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.NativePointed
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.posix.STDERR_FILENO
import platform.posix.STDIN_FILENO
import platform.posix.STDOUT_FILENO
import platform.posix.pid_tVar
import platform.posix.sigemptyset
import platform.posix.sigset_tVar

// macOS
@OptIn(ExperimentalForeignApi::class)
internal actual class PosixSpawnScope internal constructor(
    @property:DoNotReferenceDirectly(useInstead = "PosixSpawnScope extension functions")
    internal val attrs: CValuesRef<posix_spawnattr_tVar>,
    @property:DoNotReferenceDirectly(useInstead = "PosixSpawnScope extension functions")
    internal val fileActions: CValuesRef<posix_spawn_file_actions_tVar>,
    private val mem: MemScope,
): AutofreeScope() {

    internal actual val hasCLOEXEC: Boolean = true
    actual override fun alloc(size: Long, align: Int): NativePointed = mem.alloc(size, align)
}

@OptIn(ExperimentalForeignApi::class)
@Throws(UnsupportedOperationException::class)
internal actual inline fun PosixSpawnScope.file_actions_addchdir(chdir: File): Int {
    return posix_spawn_file_actions_addchdir_np(fileActions, chdir.path)
}

@OptIn(ExperimentalForeignApi::class)
internal actual inline fun PosixSpawnScope.file_actions_addclose(fd: Int): Int {
    return posix_spawn_file_actions_addclose(fileActions, fd)
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
): T? = memScoped {
    val attrs = posix_spawnattr_init() ?: return@memScoped null

    val sigset = alloc<sigset_tVar>()
    if (sigemptyset(sigset.ptr) == -1) return@memScoped null
    if (posix_spawnattr_setsigmask(attrs, sigset.ptr) != 0) return@memScoped null

    val flags = POSIX_SPAWN_SETSIGMASK or POSIX_SPAWN_CLOEXEC_DEFAULT
    if (posix_spawnattr_setflags(attrs, flags.convert()) != 0) return@memScoped null

    val fileActions = posix_spawn_file_actions_init() ?: return@memScoped null

    arrayOf(STDIN_FILENO, STDOUT_FILENO, STDERR_FILENO).forEach { fd ->
        if (posix_spawn_file_actions_addinherit_np(fileActions, fd) != 0) return@memScoped null
    }

    val scope = PosixSpawnScope(attrs, fileActions, this)
    block(scope)
}
