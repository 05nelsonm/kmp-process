/*
 * Copyright (c) 2024 Matthew Nelson
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
package io.matthewnelson.process.internal

import io.matthewnelson.process.Stdio
import java.io.File

internal fun Stdio.toRedirect(): ProcessBuilder.Redirect = when (this) {
    Stdio.Inherit -> ProcessBuilder.Redirect.INHERIT
    Stdio.Null -> REDIRECT_DISCARD
    Stdio.Pipe -> ProcessBuilder.Redirect.PIPE
}

private val REDIRECT_DISCARD: ProcessBuilder.Redirect by lazy {
    val discard = try {
        Class.forName("java.lang.ProcessBuilder\$Redirect")
            ?.getField("DISCARD")
            ?.get(null) as? ProcessBuilder.Redirect
    } catch (_: Throwable) {
        null
    }

    if (discard != null) return@lazy discard

    val isWindows: Boolean = System.getProperty("os.name")
        ?.ifBlank { null }
        ?.contains("windows", ignoreCase = true)
        ?: (File.separatorChar == '\\')

    val nullFile = File(if (isWindows) "NUL" else "/dev/null")

    ProcessBuilder.Redirect.to(nullFile)
}
