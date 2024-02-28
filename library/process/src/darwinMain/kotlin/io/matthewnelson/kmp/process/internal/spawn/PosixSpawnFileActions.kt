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
import io.matthewnelson.kmp.process.internal.check
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.alloc
import kotlinx.cinterop.ptr
import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalForeignApi::class)
internal actual value class PosixSpawnFileActions private actual constructor(
    private val _ref: CValuesRef<*>,
) {

    internal val ref: CValuesRef<posix_spawnattr_tVar> get() {
        @Suppress("UNCHECKED_CAST")
        return _ref as CValuesRef<posix_spawn_file_actions_tVar>
    }

    internal actual fun adddup2(fd: Int, newFd: Int): Int {
        return posix_spawn_file_actions_adddup2(ref, fd, newFd)
    }

    @OptIn(ExperimentalNativeApi::class)
    @Throws(IOException::class, UnsupportedOperationException::class)
    internal actual fun addchdir_np(chdir: File, scope: MemScope): Int {
        val platform = Platform.osFamily

        @OptIn(ExperimentalNativeApi::class)
        throw when (platform) {
            // fall back to fork & exec
            OsFamily.MACOSX -> UnsupportedOperationException()
            else -> IOException("posix_spawn_file_actions_addchdirnp is not supported on $platform")
        }
    }

    internal actual companion object {

        @Throws(IOException::class)
        internal actual fun MemScope.posixSpawnFileActionsInit(): PosixSpawnFileActions {
            val fileActions = alloc<posix_spawn_file_actions_tVar>()
            posix_spawn_file_actions_init(fileActions.ptr).check()
            defer { posix_spawn_file_actions_destroy(fileActions.ptr) }
            return PosixSpawnFileActions(fileActions.ptr)
        }
    }
}
