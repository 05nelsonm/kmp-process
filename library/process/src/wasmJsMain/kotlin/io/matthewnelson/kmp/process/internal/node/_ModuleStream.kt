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
@file:OptIn(DelicateFileApi::class, DoNotReferenceDirectly::class)
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "NOTHING_TO_INLINE", "UNUSED", "OPT_IN_USAGE")

package io.matthewnelson.kmp.process.internal.node

import io.matthewnelson.kmp.file.DelicateFileApi
import io.matthewnelson.kmp.file.jsExternTryCatch
import io.matthewnelson.kmp.process.internal.Bit8Array
import io.matthewnelson.kmp.process.internal.DoNotReferenceDirectly
import io.matthewnelson.kmp.process.internal.js.typed.JsUint8Array
import io.matthewnelson.kmp.process.internal.js.typed.new
import io.matthewnelson.kmp.process.internal.js.typed.set
import kotlinx.coroutines.CompletableJob

/** [docs](https://nodejs.org/api/stream.html#class-streamreadable) */
internal actual external interface JsReadable {
    actual fun destroy()
}

internal actual inline fun JsWritable.write(
    buf: ByteArray,
    offset: Int,
    len: Int,
    latch: CompletableJob,
): Boolean {
    val chunk = JsUint8Array.new(len)
    repeat(len) { i -> chunk[i] = buf[i + offset].toUByte() }
    latch.invokeOnCompletion { repeat(len) { i -> chunk[i] = 0u } }
    return jsExternTryCatch { write(chunk, latch::complete) }
}

internal actual inline fun JsReadable.onClose(
    noinline block: () -> Unit,
): JsReadable = jsExternTryCatch {
    jsReadableOn(this, "close", block)
}

internal actual inline fun JsReadable.onData(
    noinline block: (data: Bit8Array) -> Unit,
): JsReadable {
    val listener = onDataListener(block)
    return jsExternTryCatch { jsReadableOn(this, "data", listener) }
}

@DoNotReferenceDirectly("JsReadable.onClose")
internal fun jsReadableOn(
    readable: JsReadable,
    event: String,
    listener: () -> Unit,
): JsReadable = js("readable.on(event, listener)")

@DoNotReferenceDirectly("JsReadable.onData")
internal fun <T: JsAny> jsReadableOn(
    readable: JsReadable,
    event: String,
    listener: (T) -> Unit,
): JsReadable = js("readable.on(event, listener)")
