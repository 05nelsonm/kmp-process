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

import io.matthewnelson.kmp.process.internal.DoNotReferenceDirectly
import io.matthewnelson.kmp.process.internal.js.JsError
import kotlin.js.JsName

/** [docs](https://nodejs.org/api/events.html#class-eventemitter) */
@JsName("EventEmitter")
internal actual external interface JsEventEmitter {
    @DoNotReferenceDirectly("JsEventEmitter.onError")
    fun on(
        event: String,
        listener: Function<Unit>,
    ): JsEventEmitter
}

internal actual inline fun <T: JsEventEmitter> T.onError(
    noinline block: (Throwable) -> Unit,
): T {
    @OptIn(DoNotReferenceDirectly::class)
    on("error", block)
    return this
}
