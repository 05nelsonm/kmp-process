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
package io.matthewnelson.kmp.process.internal.js

import io.matthewnelson.kmp.process.internal.DoNotReferenceDirectly
import io.matthewnelson.kmp.process.internal.node.JsBuffer

@DoNotReferenceDirectly("JsObject.Companion.new()")
internal actual fun jsObjectNew(): JsObject = js("({})")

@DoNotReferenceDirectly("JsObject.getJsBufferOrNull(key)")
internal actual fun jsObjectGetJsBufferOrNull(obj: JsObject, key: String): JsBuffer? = js("obj[key]")
@DoNotReferenceDirectly("JsObject.getJsErrorOrNull(key)")
internal actual fun jsObjectGetJsErrorOrNull(obj: JsObject, key: String): JsError? = js("obj[key]")
@DoNotReferenceDirectly("JsObject.getInt(key)")
internal actual fun jsObjectGetInt(obj: JsObject, key: String): Int = js("obj[key]")
@DoNotReferenceDirectly("JsObject.getIntOrNull(key)")
internal actual fun jsObjectGetIntOrNull(obj: JsObject, key: String): Int? = js("obj[key]")
@DoNotReferenceDirectly("JsObject.getString(key)")
internal actual fun jsObjectGetString(obj: JsObject, key: String): String = js("obj[key]")
@DoNotReferenceDirectly("JsObject.getStringOrNull(key)")
internal actual fun jsObjectGetStringOrNull(obj: JsObject, key: String): String? = js("obj[key]")

@DoNotReferenceDirectly("JsObject.set[key] = value")
internal actual fun jsObjectSetInt(obj: JsObject, key: String, value: Int) { js("obj[key] = value") }
@DoNotReferenceDirectly("JsObject.set[key] = value")
internal actual fun jsObjectSetBoolean(obj: JsObject, key: String, value: Boolean) { js("obj[key] = value") }
@DoNotReferenceDirectly("JsObject.set[key] = value")
internal actual fun jsObjectSetString(obj: JsObject, key: String, value: String) { js("obj[key] = value") }
@DoNotReferenceDirectly("JsObject.set[key] = value")
internal actual fun jsObjectSetJsArray(obj: JsObject, key: String, value: JsArray) { js("obj[key] = value") }
@DoNotReferenceDirectly("JsObject.set[key] = value")
internal actual fun jsObjectSetJsObject(obj: JsObject, key: String, value: JsObject) { js("obj[key] = value") }
@DoNotReferenceDirectly("JsObject.set[key] = value")
internal actual fun jsObjectSetJsInt8Array(obj: JsObject, key: String, value: JsInt8Array) { js("obj[key] = value") }
