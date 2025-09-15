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
@file:OptIn(DelicateFileApi::class, ExperimentalWasmJsInterop::class)
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "NOTHING_TO_INLINE")

package io.matthewnelson.kmp.process.internal.node

import io.matthewnelson.kmp.file.DelicateFileApi
import io.matthewnelson.kmp.file.jsExternTryCatch
import io.matthewnelson.kmp.process.InternalProcessApi
import io.matthewnelson.kmp.process.ReadBuffer
import io.matthewnelson.kmp.process.internal.js.JsUint8Array
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsName

/** [docs](https://nodejs.org/api/stream.html) */
internal external interface ModuleStream {
    //
}

/** [docs](https://nodejs.org/api/stream.html#class-streamreadable) */
@JsName("Readable")
internal external interface JsReadable {
    fun <T: JsAny?> on(
        event: String,
        listener: (T) -> Unit,
    ): JsReadable
    fun destroy()
}

/** [docs](https://nodejs.org/api/stream.html#class-streamwritable) */
@JsName("Writable")
internal external interface JsWritable {
    val writable: Boolean
    fun end()
    fun once(
        event: String,
        listener: () -> Unit,
    ): JsWritable
    fun write(
        chunk: JsUint8Array,
        callback: () -> Unit,
    ): Boolean
}

internal inline fun JsReadable.onClose(
    noinline block: () -> Unit,
): JsReadable = jsExternTryCatch { on<JsAny?>("close") { _ -> block() } }

internal inline fun JsReadable.onData(
    noinline block: (data: ReadBuffer) -> Unit,
): JsReadable {
    val listener: (chunk: JsBuffer) -> Unit = { chunk ->
        @OptIn(InternalProcessApi::class)
        val buf = ReadBuffer.of(chunk.asBuffer())
        block(buf)
        buf.buf.fill()
    }
    return jsExternTryCatch { on("data", listener) }
}
