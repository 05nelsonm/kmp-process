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

internal actual val PATH_STDIO_NULL: String = (System.getProperty("os.name")
    ?.ifBlank { null }
    ?.contains("windows", ignoreCase = true)
    ?: (File.separatorChar == '\\'))
    .let { isWindows -> if (isWindows) "NUL" else "/dev/null" }

internal fun Stdio.toRedirect(
    isStdin: Boolean,
): ProcessBuilder.Redirect = when (this) {
    is Stdio.Inherit -> ProcessBuilder.Redirect.INHERIT
    is Stdio.Pipe -> ProcessBuilder.Redirect.PIPE
    is Stdio.File -> {
        when {
            path == PATH_STDIO_NULL -> if (isStdin) {
                REDIRECT_NULL_READ
            } else {
                REDIRECT_NULL_WRITE
            }
            isStdin -> ProcessBuilder.Redirect.from(File(path))
            append -> ProcessBuilder.Redirect.appendTo(File(path))
            else -> ProcessBuilder.Redirect.to(File(path))
        }
    }
}

private val REDIRECT_NULL_READ: ProcessBuilder.Redirect by lazy {
    ProcessBuilder.Redirect.from(File(PATH_STDIO_NULL))
}

private val REDIRECT_NULL_WRITE: ProcessBuilder.Redirect by lazy {
    val discard = try {
        Class.forName("java.lang.ProcessBuilder\$Redirect")
            ?.getField("DISCARD")
            ?.get(null) as? ProcessBuilder.Redirect
    } catch (_: Throwable) {
        null
    }

    if (discard != null) return@lazy discard

    ProcessBuilder.Redirect.to(File(PATH_STDIO_NULL))
}
