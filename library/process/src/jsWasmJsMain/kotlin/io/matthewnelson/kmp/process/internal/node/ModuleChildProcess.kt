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
package io.matthewnelson.kmp.process.internal.node

import io.matthewnelson.kmp.process.internal.js.JsArray
import io.matthewnelson.kmp.process.internal.js.JsObject
import kotlin.js.JsName

/** [docs](https://nodejs.org/api/child_process.html) */
internal external interface ModuleChildProcess {
    fun spawn(command: String, args: JsArray, options: JsObject): JsChildProcess
    fun spawnSync(command: String, args: JsArray, options: JsObject): JsObject
}

/** [docs](https://nodejs.org/api/child_process.html#class-childprocess) */
@JsName("ChildProcess")
internal external interface JsChildProcess: JsEventEmitter {
    val exitCode: Int?
    fun kill(signal: String): Boolean
    val killed: Boolean
    val pid: Int?
    val signalCode: String?
    val stdin: JsWritable?
    val stdout: JsReadable?
    val stderr: JsReadable?
    fun unref()
}
