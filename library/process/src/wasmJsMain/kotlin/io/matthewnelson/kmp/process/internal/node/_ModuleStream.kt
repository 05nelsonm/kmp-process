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
@file:OptIn(DelicateFileApi::class)

package io.matthewnelson.kmp.process.internal.node

import io.matthewnelson.kmp.file.DelicateFileApi
import io.matthewnelson.kmp.file.jsExternTryCatch
import io.matthewnelson.kmp.process.ReadBuffer
import io.matthewnelson.kmp.process.internal.DoNotReferenceDirectly
import kotlin.js.JsName

/** [docs](https://nodejs.org/api/stream.html#class-streamreadable) */
@JsName("Readable")
internal actual external interface JsReadable {
    fun <T: JsAny?> on(
        event: String,
        listener: (T) -> Unit,
    ): JsReadable
    actual fun destroy()
}

internal actual inline fun JsReadable.onClose(
    noinline block: () -> Unit,
): JsReadable = jsExternTryCatch { on<JsAny?>("close") { _ -> block() } }

internal actual inline fun JsReadable.onData(
    noinline block: (data: ReadBuffer) -> Unit,
): JsReadable {
    @OptIn(DoNotReferenceDirectly::class)
    val listener = onDataListener(block)
    return jsExternTryCatch { on("data", listener) }
}
