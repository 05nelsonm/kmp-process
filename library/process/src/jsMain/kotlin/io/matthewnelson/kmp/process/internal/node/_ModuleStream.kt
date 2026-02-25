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
@file:OptIn(DoNotReferenceDirectly::class)
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "NOTHING_TO_INLINE")

package io.matthewnelson.kmp.process.internal.node

import io.matthewnelson.kmp.process.internal.Bit8Array
import io.matthewnelson.kmp.process.internal.DoNotReferenceDirectly
import io.matthewnelson.kmp.process.internal.js.typed.JsUint8Array
import io.matthewnelson.kmp.process.internal.js.typed.asJsInt8Array
import io.matthewnelson.kmp.process.internal.js.typed.new
import kotlinx.coroutines.CompletableJob

/** [docs](https://nodejs.org/api/stream.html#class-streamreadable) */
internal actual external interface JsReadable {
    fun on(
        event: String,
        listener: Function<Unit>,
    ): JsReadable
    actual fun destroy()
}

internal actual inline fun JsWritable.write(
    buf: ByteArray,
    offset: Int,
    len: Int,
    latch: CompletableJob,
): Boolean {
    var chunk = JsUint8Array.new(buf.asJsInt8Array().buffer)
    if (!(offset == 0 && len == buf.size)) {
        chunk = chunk.subarray(start = offset, end = offset + len)
    }
    return write(chunk, latch::complete)
}

internal actual inline fun JsReadable.onClose(
    noinline block: () -> Unit,
): JsReadable = on("close", block)

internal actual inline fun JsReadable.onData(
    noinline block: (data: Bit8Array) -> Unit,
): JsReadable = on("data", onDataListener(block))
