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
@file:OptIn(DoNotReferenceDirectly::class)
@file:Suppress("NOTHING_TO_INLINE")

package io.matthewnelson.kmp.process.internal.js

import io.matthewnelson.kmp.process.internal.DoNotReferenceDirectly
import io.matthewnelson.kmp.process.internal.node.JsBuffer
import kotlin.js.JsName

@JsName("Object")
internal sealed external class JsObject {
    internal companion object {
        internal fun keys(obj: JsObject): JsArray
    }
}

internal inline fun JsObject.Companion.new(): JsObject = jsObjectNew()

internal inline fun JsObject.getJsBufferOrNull(key: String): JsBuffer? = jsObjectGetJsBufferOrNull(this, key)
internal inline fun JsObject.getJsErrorOrNull(key: String): JsError? = jsObjectGetJsErrorOrNull(this, key)
internal inline fun JsObject.getInt(key: String): Int = jsObjectGetInt(this, key)
internal inline fun JsObject.getIntOrNull(key: String): Int? = jsObjectGetIntOrNull(this, key)
internal inline fun JsObject.getString(key: String): String = jsObjectGetString(this, key)
internal inline fun JsObject.getStringOrNull(key: String): String? = jsObjectGetStringOrNull(this, key)

internal inline operator fun JsObject.set(key: String, value: Int) { jsObjectSetInt(this, key, value) }
internal inline operator fun JsObject.set(key: String, value: Boolean) { jsObjectSetBoolean(this, key, value) }
internal inline operator fun JsObject.set(key: String, value: String) { jsObjectSetString(this, key, value) }
internal inline operator fun JsObject.set(key: String, value: JsArray) { jsObjectSetJsArray(this, key, value) }
internal inline operator fun JsObject.set(key: String, value: JsObject) { jsObjectSetJsObject(this, key, value) }
internal inline operator fun JsObject.set(key: String, value: JsInt8Array) { jsObjectSetJsInt8Array(this, key, value) }

internal const val CODE_JS_OBJECT_NEW = "({})"
internal const val CODE_JS_OBJECT_GET = "obj[key]"
internal const val CODE_JS_OBJECT_SET = "obj[key] = value"

@DoNotReferenceDirectly("JsObject.Companion.new()")
internal expect fun jsObjectNew(): JsObject

@DoNotReferenceDirectly("obj.getJsBufferOrNull(key)")
internal expect fun jsObjectGetJsBufferOrNull(obj: JsObject, key: String): JsBuffer?
@DoNotReferenceDirectly("obj.getJsErrorOrNull(key)")
internal expect fun jsObjectGetJsErrorOrNull(obj: JsObject, key: String): JsError?
@DoNotReferenceDirectly("obj.getInt(key)")
internal expect fun jsObjectGetInt(obj: JsObject, key: String): Int
@DoNotReferenceDirectly("obj.getIntOrNull(key)")
internal expect fun jsObjectGetIntOrNull(obj: JsObject, key: String): Int?
@DoNotReferenceDirectly("obj.getString(key)")
internal expect fun jsObjectGetString(obj: JsObject, key: String): String
@DoNotReferenceDirectly("obj.getStringOrNull(key)")
internal expect fun jsObjectGetStringOrNull(obj: JsObject, key: String): String?

@DoNotReferenceDirectly("obj[key] = value")
internal expect fun jsObjectSetInt(obj: JsObject, key: String, value: Int)
@DoNotReferenceDirectly("obj[key] = value")
internal expect fun jsObjectSetBoolean(obj: JsObject, key: String, value: Boolean)
@DoNotReferenceDirectly("obj[key] = value")
internal expect fun jsObjectSetString(obj: JsObject, key: String, value: String)
@DoNotReferenceDirectly("obj[key] = value")
internal expect fun jsObjectSetJsArray(obj: JsObject, key: String, value: JsArray)
@DoNotReferenceDirectly("obj[key] = value")
internal expect fun jsObjectSetJsObject(obj: JsObject, key: String, value: JsObject)
@DoNotReferenceDirectly("obj[key] = value")
internal expect fun jsObjectSetJsInt8Array(obj: JsObject, key: String, value: JsInt8Array)
