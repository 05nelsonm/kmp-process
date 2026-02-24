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
package io.matthewnelson.kmp.process.internal.js.typed

import io.matthewnelson.kmp.process.internal.DoNotReferenceDirectly
import io.matthewnelson.kmp.process.internal.js.array.CODE_JS_ARRAY_GET
import io.matthewnelson.kmp.process.internal.js.array.CODE_JS_ARRAY_SET
import io.matthewnelson.kmp.process.internal.js.buffer.JsArrayBufferLike

@DoNotReferenceDirectly("JsUint8Array.Companion.new(length)")
internal actual fun jsUint8Array(length: Int): JsUint8Array = js(CODE_JS_NEW_UINT8_LENGTH)
@DoNotReferenceDirectly("JsUint8Array.Companion.new(buffer)")
internal actual fun jsUint8Array(buffer: JsArrayBufferLike): JsUint8Array = js(CODE_JS_NEW_UINT8_BUFFER)

@DoNotReferenceDirectly("array[index]")
internal actual fun jsGet(array: JsUint8Array, index: Int): Short = js(CODE_JS_ARRAY_GET)
@DoNotReferenceDirectly("array[index] = value")
internal actual fun jsSet(array: JsUint8Array, index: Int, value: Byte) { js(CODE_JS_ARRAY_SET) }
