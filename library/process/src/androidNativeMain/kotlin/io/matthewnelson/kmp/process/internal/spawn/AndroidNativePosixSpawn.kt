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
import platform.posix.RTLD_NEXT
import platform.posix.dlsym
import platform.posix.pid_tVar

// androidNative
@OptIn(ExperimentalForeignApi::class)
@Suppress("LocalVariableName", "PropertyName")
internal actual class PosixSpawnScope internal constructor(
    @property:DoNotReferenceDirectly(useInstead = "PosixSpawnScope extension functions")
    internal val attrs: CValuesRef<posix_spawnattr_tVar>,
    @property:DoNotReferenceDirectly(useInstead = "PosixSpawnScope extension functions")
    internal val fileActions: CValuesRef<posix_spawn_file_actions_tVar>,
    private val mem: MemScope,
    @property:DoNotReferenceDirectly(useInstead = "PosixSpawnScope.spawn")
    internal val _posix_spawn: CPointer<CFunction<(
        __pid: CValuesRef<pid_tVar>?,
        __path: CPointer<ByteVarOf<Byte>>?,
        __actions: CValuesRef<posix_spawn_file_actions_tVar>?,
        __attr: CValuesRef<posix_spawnattr_tVar>?,
        __argv: CValuesRef<CPointerVar<ByteVar>>?,
        __env: CValuesRef<CPointerVar<ByteVar>>?,
    ) -> Int>>,
    @property:DoNotReferenceDirectly(useInstead = "PosixSpawnScope.spawn_p")
    internal val _posix_spawn_p: CPointer<CFunction<(
        __pid: CValuesRef<pid_tVar>?,
        __file: CPointer<ByteVarOf<Byte>>?,
        __actions: CValuesRef<posix_spawn_file_actions_tVar>?,
        __attr: CValuesRef<posix_spawnattr_tVar>?,
        __argv: CValuesRef<CPointerVar<ByteVar>>?,
        __env: CValuesRef<CPointerVar<ByteVar>>?,
    ) -> Int>>,
    @property:DoNotReferenceDirectly(useInstead = "PosixSpawnScope.file_actions_adddup2")
    internal val _file_actions_adddup2: CPointer<CFunction<(
        __actions: CValuesRef<posix_spawn_file_actions_tVar>?,
        __fd: Int,
        __new_fd: Int,
    ) -> Int>>,
): AutofreeScope() {

    actual override fun alloc(size: Long, align: Int): NativePointed = mem.alloc(size, align)

    // Normally one would not want to hold onto a function pointer reference
    // statically, but it's from glibc which is not going to be hot reloaded
    // or anything w/o this process terminating, so.
    @Suppress("LocalVariableName", "UNCHECKED_CAST")
    internal companion object {

        @DoNotReferenceDirectly(useInstead = "PosixSpawnScope.spawn")
        internal val POSIX_SPAWN by lazy {
            val ptr = dlsym(RTLD_NEXT, "posix_spawn")
                ?: return@lazy null

            ptr as CPointer<CFunction<(
                __pid: CValuesRef<pid_tVar>?,
                __path: CPointer<ByteVarOf<Byte>>?,
                __actions: CValuesRef<posix_spawn_file_actions_tVar>?,
                __attr: CValuesRef<posix_spawnattr_tVar>?,
                __argv: CValuesRef<CPointerVar<ByteVar>>?,
                __env: CValuesRef<CPointerVar<ByteVar>>?,
            ) -> Int>>
        }

        @DoNotReferenceDirectly(useInstead = "PosixSpawnScope.spawn_p")
        internal val POSIX_SPAWN_P by lazy {
            val ptr = dlsym(RTLD_NEXT, "posix_spawnp")
                ?: return@lazy null

            ptr as CPointer<CFunction<(
                __pid: CValuesRef<pid_tVar>?,
                __file: CPointer<ByteVarOf<Byte>>?,
                __actions: CValuesRef<posix_spawn_file_actions_tVar>?,
                __attr: CValuesRef<posix_spawnattr_tVar>?,
                __argv: CValuesRef<CPointerVar<ByteVar>>?,
                __env: CValuesRef<CPointerVar<ByteVar>>?,
            ) -> Int>>
        }

        @DoNotReferenceDirectly(useInstead = "posixSpawnScopeOrNull")
        internal val SPAWNATTR_INIT by lazy {
            val ptr = dlsym(RTLD_NEXT, "posix_spawnattr_init")
                ?: return@lazy null

            ptr as CPointer<CFunction<(
                __attr: CValuesRef<posix_spawnattr_tVar>?,
            ) -> Int>>
        }

        @DoNotReferenceDirectly(useInstead = "posixSpawnScopeOrNull")
        internal val SPAWNATTR_DESTROY by lazy {
            val ptr = dlsym(RTLD_NEXT, "posix_spawnattr_destroy")
                ?: return@lazy null

            ptr as CPointer<CFunction<(
                __attr: CValuesRef<posix_spawnattr_tVar>?,
            ) -> Int>>
        }

        @DoNotReferenceDirectly(useInstead = "posixSpawnScopeOrNull")
        internal val SPAWNATTR_SETSIGMASK by lazy {
            val ptr = dlsym(RTLD_NEXT, "posix_spawnattr_setsigmask")
                ?: return@lazy null

            ptr as CPointer<CFunction<(
                __attr: CValuesRef<posix_spawnattr_tVar>?,
                __mask: CValuesRef<*>? /* CValuesRef<sigset_t>? */,
            ) -> Int>>
        }

        @DoNotReferenceDirectly(useInstead = "posixSpawnScopeOrNull")
        internal val FILE_ACTIONS_INIT by lazy {
            val ptr = dlsym(RTLD_NEXT, "posix_spawn_file_actions_init")
                ?: return@lazy null

            ptr as CPointer<CFunction<(
                CValuesRef<posix_spawn_file_actions_tVar>?,
            ) -> Int>>
        }

        @DoNotReferenceDirectly(useInstead = "posixSpawnScopeOrNull")
        internal val FILE_ACTIONS_DESTROY by lazy {
            val ptr = dlsym(RTLD_NEXT, "posix_spawn_file_actions_destroy")
                ?: return@lazy null

            ptr as CPointer<CFunction<(
                __actions: CValuesRef<posix_spawn_file_actions_tVar>?,
            ) -> Int>>
        }

        @DoNotReferenceDirectly(useInstead = "PosixSpawnScope.file_actions_addchdir_np")
        internal val FILE_ACTIONS_ADDCHDIR_NP by lazy {
            val ptr = dlsym(RTLD_NEXT, "posix_spawn_file_actions_addchdir_np")
                ?: return@lazy null

            ptr as CPointer<CFunction<(
                __actions: CValuesRef<posix_spawn_file_actions_tVar>?,
                __path: CPointer<ByteVarOf<Byte>>,
            ) -> Int>>
        }

        @DoNotReferenceDirectly(useInstead = "PosixSpawnScope.file_actions_adddup2")
        internal val FILE_ACTIONS_ADDDUP2 by lazy {
            val ptr = dlsym(RTLD_NEXT, "posix_spawn_file_actions_adddup2")
                ?: return@lazy null

            ptr as CPointer<CFunction<(
                __actions: CValuesRef<posix_spawn_file_actions_tVar>?,
                __fd: Int,
                __new_fd: Int,
            ) -> Int>>
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
@Throws(UnsupportedOperationException::class)
internal actual inline fun PosixSpawnScope.file_actions_addchdir_np(chdir: File): Int {
    val addchdir_np = PosixSpawnScope.FILE_ACTIONS_ADDCHDIR_NP
        ?: throw UnsupportedOperationException("posix_spawn_file_actions_addchdir is not available")

    return addchdir_np.invoke(fileActions, chdir.path.cstr.getPointer(scope = this))
}

@OptIn(ExperimentalForeignApi::class)
internal actual inline fun PosixSpawnScope.file_actions_adddup2(fd: Int, newFd: Int): Int {
    return _file_actions_adddup2.invoke(fileActions, fd, newFd)
}

@OptIn(ExperimentalForeignApi::class)
internal actual inline fun PosixSpawnScope.spawn(
    program: String,
    pid: CValuesRef<pid_tVar>,
    argv: CValuesRef<CPointerVar<ByteVar>>,
    envp: CValuesRef<CPointerVar<ByteVar>>,
): Int = _posix_spawn.invoke(pid, program.cstr.getPointer(this), fileActions, attrs, argv, envp)

@OptIn(ExperimentalForeignApi::class)
internal actual inline fun PosixSpawnScope.spawn_p(
    program: String,
    pid: CValuesRef<pid_tVar>,
    argv: CValuesRef<CPointerVar<ByteVar>>,
    envp: CValuesRef<CPointerVar<ByteVar>>,
): Int = _posix_spawn_p.invoke(pid, program.cstr.getPointer(this), fileActions, attrs, argv, envp)

@Suppress("LocalVariableName")
@OptIn(ExperimentalForeignApi::class)
@Throws(UnsupportedOperationException::class)
internal actual inline fun <T: Any> posixSpawnScopeOrNull(
    requireChangeDir: Boolean,
    block: PosixSpawnScope.() -> T,
): T? {
    if (requireChangeDir) {
        // Android API 34+ supports posix_spawn_file_actions_addchdir_np
        if (PosixSpawnScope.FILE_ACTIONS_ADDCHDIR_NP == null) return null
    }

    // Android API 28+ supports posix_spawn
    val _posix_spawn = PosixSpawnScope.POSIX_SPAWN ?: return null
    val _posix_spawn_p = PosixSpawnScope.POSIX_SPAWN_P ?: return null
    val _posix_spawnattr_init = PosixSpawnScope.SPAWNATTR_INIT ?: return null
    val _posix_spawnattr_destroy = PosixSpawnScope.SPAWNATTR_DESTROY ?: return null
    val _posix_spawnattr_setsigmask = PosixSpawnScope.SPAWNATTR_SETSIGMASK ?: return null
    val _posix_spawn_file_actions_init = PosixSpawnScope.FILE_ACTIONS_INIT ?: return null
    val _posix_spawn_file_actions_destroy = PosixSpawnScope.FILE_ACTIONS_DESTROY ?: return null
    val _posix_spawn_file_actions_adddup2 = PosixSpawnScope.FILE_ACTIONS_ADDDUP2 ?: return null

    return memScoped {
        val attrs = alloc<posix_spawnattr_tVar>()
        if (_posix_spawnattr_init.invoke(attrs.ptr) != 0) {
            return@memScoped null
        }
        defer { _posix_spawnattr_destroy.invoke(attrs.ptr) }

        val ret = sigsetInitEmpty { sigset ->
            if (_posix_spawnattr_setsigmask(attrs.ptr, sigset) != 0) -1 else 0
        }
        if (ret == -1) return@memScoped null

        val fileActions = alloc<posix_spawn_file_actions_tVar>()
        if (_posix_spawn_file_actions_init.invoke(fileActions.ptr) != 0) {
            return@memScoped null
        }
        defer { _posix_spawn_file_actions_destroy.invoke(fileActions.ptr) }

        val scope = PosixSpawnScope(
            attrs.ptr,
            fileActions.ptr,
            this,
            _posix_spawn,
            _posix_spawn_p,
            _posix_spawn_file_actions_adddup2,
        )
        block(scope)
    }
}

// Returns -1 on sigemptyset error, otherwise action result.
@OptIn(ExperimentalForeignApi::class)
internal expect inline fun MemScope.sigsetInitEmpty(action: (CValuesRef<*>) -> Int): Int
