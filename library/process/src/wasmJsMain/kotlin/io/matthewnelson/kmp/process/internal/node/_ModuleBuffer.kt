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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "NOTHING_TO_INLINE", "OPT_IN_USAGE")

package io.matthewnelson.kmp.process.internal.node

import io.matthewnelson.kmp.file.Buffer
import kotlin.js.unsafeCast

@JsName("Buffer")
internal actual external interface JsBuffer: JsAny {
    actual fun copy(target: JsBuffer, targetStart: Double, sourceStart: Double, sourceEnd: Double)
    @JsName("readInt8")
    actual fun readInt8Unsafe(offset: Int): Byte
}

internal actual fun jsBufferAllocUnsafe(len: Int): JsBuffer = js("Buffer.allocUnsafe(len)")

internal actual inline fun JsBuffer.asBuffer(): Buffer = Buffer.wrap(this)
internal actual inline fun Buffer.asJsBuffer(): JsBuffer = unwrap().unsafeCast()
