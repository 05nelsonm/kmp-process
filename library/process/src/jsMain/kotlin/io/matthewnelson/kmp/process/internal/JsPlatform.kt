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
import org.khronos.webgl.ArrayBufferView
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

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

@Suppress("NOTHING_TO_INLINE")
@OptIn(ExperimentalContracts::class)
// @Throws(IllegalArgumentException::class, IndexOutOfBoundsException::class)
internal inline fun <T: ArrayBufferView> ByteArray.toJsArray(
    offset: Int = 0,
    len: Int = size - offset,
    checkBounds: Boolean = false,
    factory: (size: Int) -> T,
): T {
    contract {
        callsInPlace(factory, InvocationKind.AT_MOST_ONCE)
    }

    if (checkBounds) checkBounds(offset, len)
    val array = factory(len)
    val dArray = array.asDynamic()

    var aI = 0
    for (i in offset until offset + len) {
        dArray[aI++] = this[i]
    }

    return array
}

internal inline fun ArrayBufferView.fill() {
    val len = byteLength
    if (len == 0) return
    val a = asDynamic()
    for (i in 0 until len) {
        a[i] = 0
    }
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
