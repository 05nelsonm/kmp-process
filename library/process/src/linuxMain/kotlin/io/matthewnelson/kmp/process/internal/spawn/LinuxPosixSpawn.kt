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
import kotlinx.cinterop.ByteVarOf
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.NativePointed
import kotlinx.cinterop.alloc
import kotlinx.cinterop.cstr
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.linux.posix_spawn
import platform.linux.posix_spawn_file_actions_adddup2
import platform.linux.posix_spawn_file_actions_destroy
import platform.linux.posix_spawn_file_actions_init
import platform.linux.posix_spawn_file_actions_t
import platform.linux.posix_spawnattr_destroy
import platform.linux.posix_spawnattr_init
import platform.linux.posix_spawnattr_setsigmask
import platform.linux.posix_spawnattr_t
import platform.linux.posix_spawnp
import platform.posix.dlsym
import platform.posix.pid_tVar
import platform.posix.sigemptyset
import platform.posix.sigset_t

// linux
@OptIn(ExperimentalForeignApi::class)
internal actual class PosixSpawnScope internal constructor(
    @property:DoNotReferenceDirectly(useInstead = "PosixSpawnScope extension functions")
    internal val attrs: CValuesRef<posix_spawnattr_t>,
    @property:DoNotReferenceDirectly(useInstead = "PosixSpawnScope extension functions")
    internal val fileActions: CValuesRef<posix_spawn_file_actions_t>,
    private val mem: MemScope,
): AutofreeScope() {

    actual override fun alloc(size: Long, align: Int): NativePointed = mem.alloc(size, align)

    // Normally one would not want to hold onto a function pointer reference
    // statically, but it's from glibc which is not going to be hot reloaded
    // or anything w/o this process terminating, so.
    @Suppress("LocalVariableName", "UNCHECKED_CAST")
    internal companion object {

        @DoNotReferenceDirectly(useInstead = "PosixSpawnScope.file_actions_addchdir_np")
        internal val FILE_ACTIONS_ADDCHDIR_NP by lazy {
            val ptr = dlsym(null, "posix_spawn_file_actions_addchdir_np")
                ?: return@lazy null

            ptr as CPointer<CFunction<(
                __actions: CValuesRef<posix_spawn_file_actions_t>,
                __path: CPointer<ByteVarOf<Byte>>,
            ) -> Int>>
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
@Throws(UnsupportedOperationException::class)
internal actual inline fun PosixSpawnScope.file_actions_addchdir_np(chdir: File): Int {
    val addchdir_np = PosixSpawnScope.FILE_ACTIONS_ADDCHDIR_NP
        ?: throw UnsupportedOperationException("posix_spawn_file_actions_addchdir_np is not available")

    return addchdir_np.invoke(fileActions, chdir.path.cstr.getPointer(scope = this))
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

@OptIn(ExperimentalForeignApi::class)
@Throws(UnsupportedOperationException::class)
internal actual inline fun <T: Any> posixSpawnScopeOrNull(
    requireChangeDir: Boolean,
    block: PosixSpawnScope.() -> T,
): T? {
    if (requireChangeDir) {
        // glibc 2.29+ supports posix_spawn_file_actions_addchdir_np
        if (PosixSpawnScope.FILE_ACTIONS_ADDCHDIR_NP == null) return null
    }

    return memScoped {
        val attrs = alloc<posix_spawnattr_t>()
        if (posix_spawnattr_init(attrs.ptr) != 0) {
            return@memScoped null
        }
        defer { posix_spawnattr_destroy(attrs.ptr) }

        val sigset = alloc<sigset_t>()
        if (sigemptyset(sigset.ptr) == -1) return null
        if (posix_spawnattr_setsigmask(attrs.ptr, sigset.ptr) != 0) return null

        val fileActions = alloc<posix_spawn_file_actions_t>()
        if (posix_spawn_file_actions_init(fileActions.ptr) != 0) {
            return@memScoped null
        }
        defer { posix_spawn_file_actions_destroy(fileActions.ptr) }

        val scope = PosixSpawnScope(attrs.ptr, fileActions.ptr, this)
        block(scope)
    }
}
