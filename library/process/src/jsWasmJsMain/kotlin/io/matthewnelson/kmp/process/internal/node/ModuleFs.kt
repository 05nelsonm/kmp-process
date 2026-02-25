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

import io.matthewnelson.kmp.process.internal.js.JsError

/** [docs](https://nodejs.org/api/fs.html) */
internal sealed external interface ModuleFs {
    fun close(fd: Double, callback: (err: JsError?) -> Unit)
    fun fstat(fd: Double, callback: (err: JsError?, stats: JsStats?) -> Unit)
    fun open(path: String, flags: String, callback: (err: JsError?, fd: Double?) -> Unit)

    fun closeSync(fd: Double)
    fun fstatSync(fd: Double): JsStats
    fun openSync(path: String, flags: String): Double
}

/** [docs](https://nodejs.org/api/fs.html#class-fsstats) */
internal sealed external interface JsStats {
    fun isDirectory(): Boolean
}
