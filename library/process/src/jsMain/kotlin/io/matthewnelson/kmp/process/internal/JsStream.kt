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
@file:Suppress("ClassName")
@file:JsModule("stream")
@file:JsNonModule

package io.matthewnelson.kmp.process.internal

/** [docs](https://nodejs.org/api/stream.html#class-streamreadable) */
@JsName("Readable")
internal open external class stream_Readable {

    internal fun <R> on(
        eventName: String,
        listener: Function<R>,
    ): stream_Readable

    internal fun destroy()
}

/** [docs](https://nodejs.org/api/stream.html#class-streamwritable) */
@JsName("Writable")
internal open external class stream_Writable {

    internal fun <R> once(
        eventName: String,
        listener: Function<R>,
    ): stream_Writable

    // @Throws(Throwable::class)
    internal fun write(
        chunk: Uint8Array,
        callback: () -> Unit,
    ): Boolean

    internal fun end()

    internal val writable: Boolean
}
