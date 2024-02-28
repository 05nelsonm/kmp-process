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

import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.InterruptedException
import io.matthewnelson.kmp.file.SysDirSep
import io.matthewnelson.kmp.file.toFile
import kotlin.time.Duration

internal actual val STDIO_NULL: File = (System.getProperty("os.name")
    ?.ifBlank { null }
    ?.contains("windows", ignoreCase = true)
    ?: (SysDirSep == '\\'))
    .let { isWindows -> if (isWindows) "NUL" else "/dev/null" }
    .toFile()

internal val ANDROID_SDK_INT: Int? by lazy {

    if (
        System.getProperty("java.runtime.name")
            ?.contains("android", ignoreCase = true) != true
    ) {
        // Not Android runtime
        return@lazy null
    }

    try {
        val clazz = Class.forName("android.os.Build\$VERSION")

        try {
            clazz?.getField("SDK_INT")?.getInt(null)
        } catch (_: Throwable) {
            clazz?.getField("SDK")?.get(null)?.toString()?.toIntOrNull()
        }
    } catch (_: Throwable) {
        null
    }
}

@Throws(InterruptedException::class)
@Suppress("NOTHING_TO_INLINE", "ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT")
internal actual inline fun Duration.threadSleep() {
    Thread.sleep(inWholeMilliseconds)
}

@Suppress("ACTUAL_WITHOUT_EXPECT")
internal actual typealias InputStream = java.io.InputStream
