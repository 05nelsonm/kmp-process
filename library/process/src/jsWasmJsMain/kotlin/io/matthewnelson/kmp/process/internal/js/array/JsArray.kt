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
@file:OptIn(DoNotReferenceDirectly::class)
@file:Suppress("NOTHING_TO_INLINE")

package io.matthewnelson.kmp.process.internal.js.array

import io.matthewnelson.kmp.process.internal.DoNotReferenceDirectly
import kotlin.js.JsName

@JsName("Array")
internal sealed external class JsArray: JsArrayLike {
    internal companion object {
        internal fun of(size: Int): JsArray
    }
}

internal inline fun JsArray.getString(index: Int): String = jsArrayGetString(this, index)
internal inline operator fun JsArray.set(index: Int, value: String) { jsArraySetString(this, index, value) }
internal inline operator fun JsArray.set(index: Int, value: Double) { jsArraySetDouble(this, index, value) }

@DoNotReferenceDirectly("array.getString(index)")
internal expect fun jsArrayGetString(array: JsArray, index: Int): String
@DoNotReferenceDirectly("array[index] = value")
internal expect fun jsArraySetString(array: JsArray, index: Int, value: String)
@DoNotReferenceDirectly("array[index] = value")
internal expect fun jsArraySetDouble(array: JsArray, index: Int, value: Double)
