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
@file:OptIn(DelicateFileApi::class)
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "NOTHING_TO_INLINE", "OPT_IN_USAGE")

package io.matthewnelson.kmp.process.internal.node

import io.matthewnelson.kmp.file.DelicateFileApi
import io.matthewnelson.kmp.file.jsExternTryCatch
import io.matthewnelson.kmp.process.internal.DoNotReferenceDirectly
import io.matthewnelson.kmp.process.internal.js.JsError
import io.matthewnelson.kmp.process.internal.js.toThrowable
import kotlin.js.JsName

/** [docs](https://nodejs.org/api/events.html#class-eventemitter) */
@JsName("EventEmitter")
internal actual external interface JsEventEmitter {
    @DoNotReferenceDirectly("JsEventEmitter.onError")
    fun <T: JsAny?> on(
        event: String,
        listener: (T) -> Unit,
    ): JsEventEmitter
}

internal actual inline fun <T: JsEventEmitter> T.onError(
    noinline block: (Throwable) -> Unit,
): T {
    val listener: (JsError) -> Unit = { jsError ->
        val t = jsError.toThrowable()
        block(t)
    }
    @OptIn(DoNotReferenceDirectly::class)
    jsExternTryCatch { on("error", listener) }
    return this
}
