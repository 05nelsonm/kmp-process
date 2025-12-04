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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "OPT_IN_USAGE")

package io.matthewnelson.kmp.process.internal.js

import io.matthewnelson.kmp.file.jsExternTryCatch
import kotlin.js.JsName

@JsName("Error")
internal actual external class JsError: JsAny {
    internal actual val message: String?
}

internal actual fun JsError.toThrowable(): Throwable {
    try {
        jsExternTryCatch { jsThrow(this) }
    } catch (t: Throwable) {
        return t
    }

    // Try Kotlin's implementation for WasmJs
    toThrowableOrNull()?.let { return it }

    // Total failure...
    return Throwable(message)
}

@Suppress("UNUSED")
private fun jsThrow(e: JsError) { js("throw e;") }
