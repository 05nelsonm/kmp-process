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
@file:Suppress("NOTHING_TO_INLINE")

package io.matthewnelson.kmp.process.internal

import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.SysDirSep
import io.matthewnelson.kmp.file.path
import io.matthewnelson.kmp.process.Output
import io.matthewnelson.kmp.process.Process
import io.matthewnelson.kmp.process.Signal
import io.matthewnelson.kmp.process.Stdio
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

internal expect val IsDesktop: Boolean

@Throws(UnsupportedOperationException::class)
internal expect inline fun Process.Current.platformPID(): Int

internal inline val IsWindows: Boolean get() = STDIO_NULL.path == "NUL"

private const val DOT = "."
private const val DOT_DOT = ".."
private val SLASH = "$SysDirSep"

@Throws(IOException::class)
@OptIn(ExperimentalContracts::class)
internal inline fun String.checkFileName(paramName: () -> String) {
    contract { callsInPlace(paramName, InvocationKind.AT_MOST_ONCE) }
    if (isEmpty()) {
        val n = paramName()
        throw IOException("$n cannot be empty")
    }
    when (this) {
        DOT, DOT_DOT, SLASH -> {
            val n = paramName()
            throw IOException("$n cannot be '$this'")
        }
        else -> return
    }
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

@Throws(IndexOutOfBoundsException::class)
internal inline fun ByteArray.checkBounds(offset: Int, len: Int) {
    size.checkBounds(offset, len)
}

@Throws(IndexOutOfBoundsException::class)
internal fun Int.checkBounds(offset: Int, len: Int) {
    val size = this
    if (offset < 0) throw IndexOutOfBoundsException("offset[$offset] < 0")
    if (len < 0) throw IndexOutOfBoundsException("len[$len] < 0")
    if (offset > size - len) throw IndexOutOfBoundsException("offset[$offset] > size[$size] - len[$len]")
}

@Throws(IndexOutOfBoundsException::class)
internal inline fun Output.Data.checkCopyBounds(dest: ByteArray, destOffset: Int, indexStart: Int, indexEnd: Int) {
    size.checkCopyBounds(dest.size, destOffset, indexStart, indexEnd)
}

@Throws(IndexOutOfBoundsException::class)
internal fun Int.checkCopyBounds(destSize: Int, destOffset: Int, indexStart: Int, indexEnd: Int) {
    val size = this
    if (indexStart < 0) throw IndexOutOfBoundsException("indexStart[$indexStart] < 0")
    if (indexStart > indexEnd) throw IndexOutOfBoundsException("indexStart[$indexStart] > indexEnd[$indexEnd]")
    if (indexEnd > size) throw IndexOutOfBoundsException("indexEnd[$indexEnd] > size[$size]")

    if (destSize < 0) throw IndexOutOfBoundsException("dest.size[$destSize] < 0")
    if (destSize == 0) {
        if (destOffset != 0) {
            throw IndexOutOfBoundsException("destOffset[$destOffset] !in 0..0")
        }
    } else {
        if (destOffset !in (0 until destSize)) {
            throw IndexOutOfBoundsException("destOffset[$destOffset] !in 0..${destSize - 1}")
        }
    }
    val len = (indexEnd - indexStart)
    val capacity = destSize - destOffset
    if (len > capacity) {
        throw IndexOutOfBoundsException(
            "(indexEnd[$indexEnd] - indexStart[$indexStart]) > (dest.size[$destSize] - destOffset[$destOffset])"
        )
    }
}
