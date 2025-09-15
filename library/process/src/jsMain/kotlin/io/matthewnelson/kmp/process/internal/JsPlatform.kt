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
@file:Suppress("NOTHING_TO_INLINE", "DEPRECATION_ERROR")

package io.matthewnelson.kmp.process.internal

import io.matthewnelson.kmp.process.InternalProcessApi

/** @suppress */
@InternalProcessApi
@Deprecated("Scheduled for removal. Do not use.", level = DeprecationLevel.ERROR) // ERROR OK, imo, b/c is InternalProcessApi
public inline fun <T: events_EventEmitter> T.onError(
    noinline block: (err: dynamic) -> Unit,
): T {
    on("error", block)
    return this
}

/** @suppress */
@InternalProcessApi
@Deprecated("Scheduled for removal. Do not use.", level = DeprecationLevel.ERROR) // ERROR OK, imo, b/c is InternalProcessApi
public inline fun <T: events_EventEmitter> T.onceError(
    noinline block: (err: dynamic) -> Unit,
): T {
    once("error", block)
    return this
}
