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
@file:Suppress("KotlinRedundantDiagnosticSuppress")

package io.matthewnelson.kmp.process.internal

import io.matthewnelson.kmp.file.*
import io.matthewnelson.kmp.process.Signal
import io.matthewnelson.kmp.process.Stdio

internal expect val STDIO_NULL: File

internal expect val IsMobile: Boolean

@Suppress("NOTHING_TO_INLINE")
internal inline val IsWindows: Boolean get() = STDIO_NULL.path == "NUL"

@Suppress("NOTHING_TO_INLINE")
internal inline fun File.isCanonicallyEqualTo(other: File): Boolean {
    if (this == other) return true

    val (thisFile, otherFile) = try {
        canonicalFile() to other.canonicalFile()
    } catch (_: IOException) {
        absoluteFile.normalize() to other.absoluteFile.normalize()
    }

    return thisFile == otherFile
}

internal fun StringBuilder.appendProcessInfo(
    className: String,
    pid: Int,
    exitCode: String,
    command: String,
    args: List<String>,
    cwd: File?,
    stdio: Stdio.Config,
    destroySignal: Signal,
) {
    append(className)
    appendLine(": [")

    append("    pid: ")
    appendLine(pid)

    append("    exitCode: ")
    appendLine(exitCode)

    append("    command: ")
    appendLine(command)

    append("    args: [")
    if (args.isEmpty()) {
        appendLine(']')
    } else {
        args.joinTo(
            this,
            separator = "\n        ",
            prefix = "\n        ",
            postfix = "\n    ]\n"
        )
    }

    append("    cwd: ")
    appendLine(cwd?.path ?: "")

    appendLine("    stdio: [")
    stdio.toString().lines().let { lines ->
        for (i in 1 until lines.size) {
            append("    ")
            appendLine(lines[i])
        }
    }

    append("    destroySignal: ")
    appendLine(destroySignal)
    append(']')
}

@Throws(IllegalArgumentException::class, IndexOutOfBoundsException::class)
internal fun ByteArray.checkBounds(offset: Int, len: Int) {
    size.checkBounds(offset, len)
}

@Suppress("NOTHING_TO_INLINE")
@Throws(IllegalArgumentException::class, IndexOutOfBoundsException::class)
internal inline fun Int.checkBounds(offset: Int, len: Int) {
    val size = this
    if (size - offset < len) throw IllegalArgumentException("Input too short")
    if (offset < 0 || len < 0 || offset > size - len) throw IndexOutOfBoundsException()
}
