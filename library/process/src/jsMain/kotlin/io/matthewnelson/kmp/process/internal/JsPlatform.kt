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
import io.matthewnelson.kmp.process.InternalProcessApi
import io.matthewnelson.kmp.process.ReadBuffer

internal actual val STDIO_NULL: File by lazy {
    val isWindows = try {
        os_platform() == "win32"
    } catch (_: Throwable) {
        SysDirSep == '\\'
    }

    (if (isWindows) "NUL" else "/dev/null").toFile()
}

internal actual val IsMobile: Boolean get() = try {
    os_platform() == "android"
} catch (_: Throwable) {
    false
}

/** @suppress */
@InternalProcessApi
@Suppress("NOTHING_TO_INLINE")
public inline fun <T: events_EventEmitter> T.onError(
    noinline block: (err: dynamic) -> Unit,
): T {
    on("error", block)
    return this
}

/** @suppress */
@InternalProcessApi
@Suppress("NOTHING_TO_INLINE")
public inline fun <T: events_EventEmitter> T.onceError(
    noinline block: (err: dynamic) -> Unit,
): T {
    once("error", block)
    return this
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun stream_Readable.onClose(
    noinline block: () -> Unit,
): stream_Readable = on("close", block)

@Suppress("NOTHING_TO_INLINE")
internal inline fun stream_Readable.onData(
    noinline block: (data: ReadBuffer) -> Unit,
): stream_Readable {
    val cb: (chunk: dynamic) -> Unit = { chunk ->
        @OptIn(InternalProcessApi::class)
        val buf = ReadBuffer.of(Buffer.wrap(chunk))
        block(buf)
        buf.buf.fill()
    }

    return on("data", cb)
}
