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

import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.process.internal.check
import kotlinx.cinterop.*
import platform.linux.posix_spawnattr_destroy
import platform.linux.posix_spawnattr_init
import platform.linux.posix_spawnattr_t

@OptIn(ExperimentalForeignApi::class)
internal actual value class PosixSpawnAttrs private actual constructor(
    private val _ref: CValuesRef<*>,
) {

    internal val ref: CValuesRef<posix_spawnattr_t> get() {
        @Suppress("UNCHECKED_CAST")
        return _ref as CValuesRef<posix_spawnattr_t>
    }

    internal actual companion object {

        @Throws(IOException::class)
        internal actual fun MemScope.posixSpawnAttrInit(): PosixSpawnAttrs {
            val attrs = alloc<posix_spawnattr_t>()
            posix_spawnattr_init(attrs.ptr).check()
            defer { posix_spawnattr_destroy(attrs.ptr) }
            return PosixSpawnAttrs(attrs.ptr)
        }
    }
}
