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
@file:Suppress("NOTHING_TO_INLINE")

package io.matthewnelson.kmp.process.internal

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import kotlinx.cinterop.toKString
import platform.posix.closedir
import platform.posix.dirent
import platform.posix.environ
import platform.posix.errno
import platform.posix.fdopendir
import platform.posix.readdir

@OptIn(ExperimentalForeignApi::class)
internal actual inline fun PlatformBuilder.parentEnvironment(): MutableMap<String, String> {
    val map = LinkedHashMap<String, String>(10, 1.0F)
    val env = environ ?: return map
    var i = 0
    while (true) {
        val arg = env[i++]?.toKString() ?: break
        val key = arg.substringBefore('=')
        val value = arg.substringAfter('=')
        map[key] = value
    }
    return map
}

internal actual inline val ChildProcess.FD_DIR: String get() = "/proc/self/fd"

/**
 * [action] return [Unit] to break from loop, or `null` to continue.
 *
 * @return [errno] if `fdopendir` fails, otherwise `null`.
 * */
@OptIn(ExperimentalForeignApi::class)
internal actual inline fun ChildProcess.parseDir(fdDir: Int, action: (CPointer<dirent>) -> Unit?): Int? {
    val dir = fdopendir(fdDir) ?: return errno

    try {
        var entry: CPointer<dirent>? = readdir(dir)
        while (entry != null) {
            if (action(entry) != null) break
            entry = readdir(dir)
        }
    } finally {
        closedir(dir)
    }

    return null
}
