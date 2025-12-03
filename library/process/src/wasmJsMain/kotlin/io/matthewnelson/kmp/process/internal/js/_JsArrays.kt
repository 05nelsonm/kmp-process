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

@DoNotReferenceDirectly("JsArray.getString(index)")
internal actual fun jsArrayGetString(array: JsArray, index: Int): String = js("array[index]")
@DoNotReferenceDirectly("JsArray.set[index] = value")
internal actual fun jsArraySetString(array: JsArray, index: Int, value: String) { js("array[index] = value") }
@DoNotReferenceDirectly("JsArray.set[index] = value")
internal actual fun jsArraySetDouble(array: JsArray, index: Int, value: Double) { js("array[index] = value") }
@DoNotReferenceDirectly("JsArrayBufferView.set[index] = value")
internal actual fun <T: JsArrayBufferView> jsArraySet(array: T, index: Int, value: Byte) { js("array[index] = value") }
