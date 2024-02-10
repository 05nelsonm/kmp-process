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
@file:Suppress("ClassName", "FunctionName")
@file:JsModule("child_process")
@file:JsNonModule

package io.matthewnelson.kmp.process.internal

import io.matthewnelson.kmp.process.InternalProcessApi

/** [docs](https://nodejs.org/api/child_process.html#child_processspawncommand-args-options) */
@JsName("spawn")
internal external fun child_process_spawn(
    command: String,
    args: Array<String>,
    options: dynamic,
): child_process_ChildProcess

/** [docs](https://nodejs.org/api/child_process.html#class-childprocess) */
@JsName("ChildProcess")
@OptIn(InternalProcessApi::class)
internal external class child_process_ChildProcess: events_EventEmitter {

    internal val exitCode: Number?

    internal fun kill(signal: String): Boolean

    internal val killed: Boolean

    internal val pid: Number?

    internal val signalCode: String?
}
