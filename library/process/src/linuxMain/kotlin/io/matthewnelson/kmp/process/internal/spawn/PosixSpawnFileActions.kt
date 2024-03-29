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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package io.matthewnelson.kmp.process.internal.spawn

import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.path
import io.matthewnelson.kmp.process.internal.check
import kotlinx.cinterop.*
import platform.linux.posix_spawn_file_actions_adddup2
import platform.linux.posix_spawn_file_actions_destroy
import platform.linux.posix_spawn_file_actions_init
import platform.linux.posix_spawn_file_actions_t
import platform.posix.dlsym

@OptIn(ExperimentalForeignApi::class)
internal actual value class PosixSpawnFileActions private actual constructor(
    private val _ref: CValuesRef<*>,
) {

    internal val ref: CValuesRef<posix_spawn_file_actions_t> get() {
        @Suppress("UNCHECKED_CAST")
        return _ref as CValuesRef<posix_spawn_file_actions_t>
    }

    internal actual fun adddup2(fd: Int, newFd: Int): Int {
        return posix_spawn_file_actions_adddup2(ref, fd, newFd)
    }

    @Throws(UnsupportedOperationException::class)
    @Suppress("ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT")
    internal actual fun addchdir_np(chdir: File, scope: MemScope): Int {
        return with(scope) {
            ADDCHDIR_NP?.invoke(ref, chdir.path.cstr.ptr)
                ?: throw UnsupportedOperationException()
        }
    }

    internal actual companion object {

        @Throws(IOException::class)
        internal actual fun MemScope.posixSpawnFileActionsInit(): PosixSpawnFileActions {
            val fileActions = alloc<posix_spawn_file_actions_t>()
            posix_spawn_file_actions_init(fileActions.ptr).check()
            defer { posix_spawn_file_actions_destroy(fileActions.ptr) }
            return PosixSpawnFileActions(fileActions.ptr)
        }

        private val ADDCHDIR_NP by lazy {
            val ptr = dlsym(null, "posix_spawn_file_actions_addchdir_np")
                ?: return@lazy null

            @Suppress("UNCHECKED_CAST")
            ptr as CPointer<CFunction<(CValuesRef<posix_spawn_file_actions_t>, CPointer<ByteVarOf<Byte>>) -> Int>>
        }
    }
}
