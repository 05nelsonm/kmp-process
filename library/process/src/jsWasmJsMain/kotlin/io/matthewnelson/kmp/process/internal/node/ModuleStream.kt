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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "NOTHING_TO_INLINE")

package io.matthewnelson.kmp.process.internal.node

import io.matthewnelson.kmp.process.internal.Bit8Array
import io.matthewnelson.kmp.process.internal.DoNotReferenceDirectly
import io.matthewnelson.kmp.process.internal.js.typed.JsInt8Array
import io.matthewnelson.kmp.process.internal.js.typed.JsUint8Array
import io.matthewnelson.kmp.process.internal.js.typed.new
import kotlinx.coroutines.CompletableJob

/** [docs](https://nodejs.org/api/stream.html) */
internal external interface ModuleStream {
    //
}

/** [docs](https://nodejs.org/api/stream.html#class-streamwritable) */
internal external interface JsWritable {
    val writable: Boolean
    fun end()
    fun once(
        event: String,
        listener: () -> Unit,
    ): JsWritable

    @DoNotReferenceDirectly("JsWritable.write(buf, offset, len, job)")
    fun write(
        chunk: JsUint8Array,
        callback: () -> Unit,
    ): Boolean
}

/** [docs](https://nodejs.org/api/stream.html#class-streamreadable) */
internal expect interface JsReadable {
//    fun on(
//        event: String,
//        listener: (JsAny?/dynamic) -> Unit,
//    ): JsReadable
    fun destroy()
}

// Assumes size/offset/len has already been validated
internal expect inline fun JsWritable.write(
    buf: ByteArray,
    offset: Int,
    len: Int,
    latch: CompletableJob,
): Boolean

internal expect inline fun JsReadable.onClose(
    noinline block: () -> Unit,
): JsReadable

internal expect inline fun JsReadable.onData(
    noinline block: (data: Bit8Array) -> Unit,
): JsReadable

@DoNotReferenceDirectly("JsReadable.onData")
internal inline fun onDataListener(
    noinline block: (data: Bit8Array) -> Unit
): (JsUint8Array) -> Unit = { data ->
    val int8 = JsInt8Array.new(data.buffer)
    block(Bit8Array(storage = int8))
}
