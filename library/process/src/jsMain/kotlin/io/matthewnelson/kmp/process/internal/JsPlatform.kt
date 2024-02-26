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
@file:Suppress("KotlinRedundantDiagnosticSuppress")

package io.matthewnelson.kmp.process.internal

import io.matthewnelson.kmp.file.*

internal actual val STDIO_NULL: File by lazy {
    val isWindows = try {
        os_platform() == "win32"
    } catch (_: Throwable) {
        SysDirSep == '\\'
    }

    (if (isWindows) "NUL" else "/dev/null").toFile()
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun stream_Readable.onClose(
    noinline block: () -> Unit,
): stream_Readable = on("close", block)

@Suppress("NOTHING_TO_INLINE")
internal inline fun stream_Readable.onData(
    noinline block: (data: String) -> Unit,
): stream_Readable {
    val cb: (chunk: dynamic) -> Unit = { chunk ->
        // can be either a String or a Buffer (fucking stupid...)

        val result = try {
            val buf = Buffer.wrap(chunk)
            val utf8 = buf.toUtf8()
            buf.fill()
            utf8
        } catch (_: IllegalArgumentException) {
            try {
                chunk as String
            } catch (_: ClassCastException) {
                null
            }
        }

        if (!result.isNullOrEmpty()) block(result)
    }

    return on("data", cb)
}
