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
@file:OptIn(DoNotReferenceDirectly::class, ExperimentalWasmJsInterop::class)
@file:Suppress("NOTHING_TO_INLINE", "RedundantNullableReturnType", "UNUSED")

package io.matthewnelson.kmp.process.internal.js

import io.matthewnelson.kmp.process.internal.DoNotReferenceDirectly
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsName
import kotlin.js.js

@JsName("Object")
internal external class JsObject: JsAny {
    internal companion object {
        internal fun keys(obj: JsObject): JsArray
    }
}

internal inline fun JsObject.Companion.new(): JsObject = jsObjectNew()
@DoNotReferenceDirectly("JsObject.Companion.new()")
internal fun jsObjectNew(): JsObject = js("({})")

internal inline fun <T: JsAny> JsObject.getJsAny(key: String): T = jsObjectGetJsAny(this, key)
@DoNotReferenceDirectly("JsObject.getJsAny(key)")
internal fun <T: JsAny> jsObjectGetJsAny(obj: JsObject, key: String): T = js("obj[key]")

internal inline fun <T: JsAny> JsObject.getJsAnyOrNull(key: String): T? = jsObjectGetJsAnyOrNull(this, key)
@DoNotReferenceDirectly("JsObject.getJsAny(key)")
internal fun <T: JsAny> jsObjectGetJsAnyOrNull(obj: JsObject, key: String): T? = js("obj[key]")

internal inline fun JsObject.getInt(key: String): Int = jsObjectGetInt(this, key)
@DoNotReferenceDirectly("JsObject.getIng(key)")
internal fun jsObjectGetInt(obj: JsObject, key: String): Int = js("obj[key]")

internal inline fun JsObject.getIntOrNull(key: String): Int? = jsObjectGetIntOrNull(this, key)
@DoNotReferenceDirectly("JsObject.getIntOrNull(key)")
internal fun jsObjectGetIntOrNull(obj: JsObject, key: String): Int? = js("obj[key]")

internal inline fun JsObject.getString(key: String): String = jsObjectGetString(this, key)
@DoNotReferenceDirectly("JsObject.getString(key)")
internal fun jsObjectGetString(obj: JsObject, key: String): String = js("obj[key]")

internal inline fun JsObject.getStringOrNull(key: String): String? = jsObjectGetStringOrNull(this, key)
@DoNotReferenceDirectly("JsObject.getStringOrNull(key)")
internal fun jsObjectGetStringOrNull(obj: JsObject, key: String): String? = js("obj[key]")

internal inline operator fun JsObject.set(key: String, value: Int) { jsObjectSetInt(this, key, value) }
@DoNotReferenceDirectly("JsObject.set[key] = value")
internal fun jsObjectSetInt(obj: JsObject, key: String, value: Int) { js("obj[key] = value") }

internal inline operator fun JsObject.set(key: String, value: Boolean) { jsObjectSetBoolean(this, key, value) }
@DoNotReferenceDirectly("JsObject.set[key] = value")
internal fun jsObjectSetBoolean(obj: JsObject, key: String, value: Boolean) { js("obj[key] = value") }

internal inline operator fun JsObject.set(key: String, value: String) { jsObjectSetString(this, key, value) }
@DoNotReferenceDirectly("JsObject.set[key] = value")
internal fun jsObjectSetString(obj: JsObject, key: String, value: String) { js("obj[key] = value") }

internal inline operator fun <T: JsAny?> JsObject.set(key: String, value: T) { jsObjectSetAny(this, key, value) }
@DoNotReferenceDirectly("JsObject.set[key] = value")
internal fun <T: JsAny?> jsObjectSetAny(obj: JsObject, key: String, value: T) { js("obj[key] = value") }
