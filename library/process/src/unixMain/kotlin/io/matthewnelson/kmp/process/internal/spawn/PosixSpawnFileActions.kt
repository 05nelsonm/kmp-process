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
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope

@OptIn(ExperimentalForeignApi::class)
internal expect value class PosixSpawnFileActions private constructor(
    private val _ref: CValuesRef<*>,
) {

    internal fun adddup2(fd: Int, newFd: Int): Int

    // On Linux, an UnsupportedOperationException will be thrown
    // if unavailable in order to fall back to fork & exec.
    //
    // On macOS, an UnsupportedOperationException will be thrown
    // because the implementation is awful.
    //
    // On other Apple targets, an IOException will be thrown as
    // it is unsupported and neither is fork & exec. End early.
    @Throws(IOException::class, UnsupportedOperationException::class)
    internal fun addchdir_np(chdir: File, scope: MemScope): Int

    internal companion object {

        @Throws(IOException::class)
        internal fun MemScope.posixSpawnFileActionsInit(): PosixSpawnFileActions
    }
}
