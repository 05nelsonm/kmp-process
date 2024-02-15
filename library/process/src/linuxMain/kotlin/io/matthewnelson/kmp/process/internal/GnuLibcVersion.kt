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

package io.matthewnelson.kmp.process.internal

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString

internal actual class GnuLibcVersion internal constructor(
    private val major: UByte,
    private val minor: UByte,
) {

    internal actual fun isAtLeast(
        major: UByte,
        minor: UByte,
    ): Boolean {
        if (this.major > major) return true
        if (this.major < major) return false
        return this.minor >= minor
    }

    internal actual companion object {

        @Throws(NullPointerException::class)
        internal actual fun getOrNull(): GnuLibcVersion? {
            @OptIn(ExperimentalForeignApi::class)
            val version = gnu_get_libc_version()
                ?.toKString()
                ?.split('.')!!

            val major = version
                .elementAtOrNull(0)
                ?.toUByteOrNull()!!
            val minor = version
                .elementAtOrNull(1)
                ?.toUByteOrNull()!!

            return GnuLibcVersion(major, minor)
        }
    }

    override fun toString(): String = "GnuLibcVersion[$major.$minor]"
}
