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
@file:Suppress("KotlinRedundantDiagnosticSuppress", "UnnecessaryOptInAnnotation")

package io.matthewnelson.kmp.process.internal

import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.path
import io.matthewnelson.kmp.file.toFile
import kotlinx.cinterop.*
import platform.posix.*

internal actual val STDIO_NULL: File = "/dev/null".toFile()

@Suppress("NOTHING_TO_INLINE")
internal expect inline fun PlatformBuilder.parentEnvironment(): MutableMap<String, String>

@OptIn(ExperimentalForeignApi::class)
internal actual fun File.isProgramOrNull(): Boolean? = memScoped {
    val stat = alloc<stat>()

    // use stat to resolve any links
    if (stat(path, stat.ptr) != 0) {
        return@memScoped null
    }

    // TODO: Check if executable?
    @OptIn(UnsafeNumber::class)
    val mode = stat.st_mode.toInt()
    (mode and S_IFMT) == S_IFREG
}
