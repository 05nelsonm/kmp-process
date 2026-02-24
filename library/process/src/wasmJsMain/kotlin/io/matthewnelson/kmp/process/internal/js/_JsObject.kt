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
@file:Suppress("OPT_IN_USAGE")

package io.matthewnelson.kmp.process.internal.js

import io.matthewnelson.kmp.process.internal.DoNotReferenceDirectly
import io.matthewnelson.kmp.process.internal.js.typed.JsUint8Array

@DoNotReferenceDirectly("JsObject.Companion.new()")
internal actual fun jsObjectNew(): JsObject = js(CODE_JS_OBJECT_NEW)

@DoNotReferenceDirectly("obj.getJsBufferOrNull(key)")
internal actual fun jsObjectGetJsBufferOrNull(obj: JsObject, key: String): JsUint8Array? = js(CODE_JS_OBJECT_GET)
@DoNotReferenceDirectly("obj.getJsErrorOrNull(key)")
internal actual fun jsObjectGetJsErrorOrNull(obj: JsObject, key: String): JsError? = js(CODE_JS_OBJECT_GET)
@DoNotReferenceDirectly("obj.getInt(key)")
internal actual fun jsObjectGetInt(obj: JsObject, key: String): Int = js(CODE_JS_OBJECT_GET)
@DoNotReferenceDirectly("obj.getIntOrNull(key)")
internal actual fun jsObjectGetIntOrNull(obj: JsObject, key: String): Int? = js(CODE_JS_OBJECT_GET)
@DoNotReferenceDirectly("obj.getString(key)")
internal actual fun jsObjectGetString(obj: JsObject, key: String): String = js(CODE_JS_OBJECT_GET)
@DoNotReferenceDirectly("obj.getStringOrNull(key)")
internal actual fun jsObjectGetStringOrNull(obj: JsObject, key: String): String? = js(CODE_JS_OBJECT_GET)

@DoNotReferenceDirectly("obj[key] = value")
internal actual fun jsObjectSetInt(obj: JsObject, key: String, value: Int) { js(CODE_JS_OBJECT_SET) }
@DoNotReferenceDirectly("obj[key] = value")
internal actual fun jsObjectSetBoolean(obj: JsObject, key: String, value: Boolean) { js(CODE_JS_OBJECT_SET) }
@DoNotReferenceDirectly("obj[key] = value")
internal actual fun jsObjectSetString(obj: JsObject, key: String, value: String) { js(CODE_JS_OBJECT_SET) }
@DoNotReferenceDirectly("obj[key] = value")
internal actual fun jsObjectSetJsArray(obj: JsObject, key: String, value: JsArray) { js(CODE_JS_OBJECT_SET) }
@DoNotReferenceDirectly("obj[key] = value")
internal actual fun jsObjectSetJsObject(obj: JsObject, key: String, value: JsObject) { js(CODE_JS_OBJECT_SET) }
@DoNotReferenceDirectly("obj[key] = value")
internal actual fun jsObjectSetJsInt8Array(obj: JsObject, key: String, value: JsInt8Array) { js(CODE_JS_OBJECT_SET) }
