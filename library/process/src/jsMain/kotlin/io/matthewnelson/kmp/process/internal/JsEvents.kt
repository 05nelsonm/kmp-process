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
@file:Suppress("ClassName", "UNUSED", "DEPRECATION_ERROR")
@file:JsModule("events")
@file:JsNonModule

package io.matthewnelson.kmp.process.internal

import io.matthewnelson.kmp.process.InternalProcessApi

/**
 * [docs](https://nodejs.org/api/events.html#class-eventemitter)
 * @suppress
 * */
@InternalProcessApi
@JsName("EventEmitter")
@Deprecated("Scheduled for removal. Do not use.", level = DeprecationLevel.ERROR) // ERROR OK, imo, b/c is InternalProcessApi
public open external class events_EventEmitter {

    public fun getMaxListeners(): Number

    public fun <R> on(
        eventName: String,
        listener: Function<R>,
    ): events_EventEmitter

    public fun <R> once(
        eventName: String,
        listener: Function<R>,
    ): events_EventEmitter

    public fun listeners(
        eventName: String,
    ): Array<Function<*>>

    public fun <R> removeListener(
        eventName: String,
        listener: Function<R>,
    ): events_EventEmitter

    public fun removeAllListeners(): events_EventEmitter
    public fun removeAllListeners(
        eventName: String,
    ): events_EventEmitter
}
