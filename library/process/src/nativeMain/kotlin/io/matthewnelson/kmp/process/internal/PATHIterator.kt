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
@file:Suppress("PropertyName")

package io.matthewnelson.kmp.process.internal

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix._PATH_DEFPATH
import platform.posix.getenv

@OptIn(ExperimentalForeignApi::class)
internal class PATHIterator(val PATH: String): Iterator<String> {
    internal constructor(): this(getenv("PATH")?.toKString()?.ifBlank { null } ?: _PATH_DEFPATH)

    private var i: Int = 0
    private var iNext: Int = PATH.indexOf(SEP, startIndex = i)
    init { if (iNext == -1) iNext = PATH.length }

    override fun hasNext(): Boolean = iNext != -1

    override fun next(): String {
        if (!hasNext()) throw NoSuchElementException()
        val n = PATH.substring(i, iNext)
        i = iNext + 1
        if (i > PATH.length) {
            iNext = -1
        } else {
            iNext = PATH.indexOf(SEP, startIndex = i)
            if (iNext == -1) {
                iNext = PATH.length
            }
        }
        return n
    }

    internal companion object {
        internal val SEP: Char = if (IsWindows) ';' else ':'
    }
}
