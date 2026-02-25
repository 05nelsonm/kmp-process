/*
 * Copyright (c) 2026 Matthew Nelson
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
@file:Suppress("ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT")

package io.matthewnelson.kmp.process.internal

import io.matthewnelson.kmp.process.Output
import io.matthewnelson.kmp.process.internal.js.typed.JsUint8Array
import io.matthewnelson.kmp.process.internal.js.typed.asJsUint8Array

//@Throws(IOException::class)
internal actual fun Output.Options.jsWasmJsConsumeInputBytes(): JsUint8Array? {
    val b = consumeInputBytes() ?: return null
    val a = Bit8Array(b.size) { i -> b[i] }.storage.asJsUint8Array()
    b.fill(0)
    return a
}
