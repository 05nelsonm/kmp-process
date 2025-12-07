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
@file:Suppress("LocalVariableName", "NOTHING_TO_INLINE")

package io.matthewnelson.kmp.process.internal

import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.absoluteFile2
import io.matthewnelson.kmp.file.async.AsyncFs
import io.matthewnelson.kmp.file.async.with
import io.matthewnelson.kmp.file.canonicalFile2
import io.matthewnelson.kmp.file.mkdirs2
import io.matthewnelson.kmp.file.normalize
import io.matthewnelson.kmp.file.parentFile
import io.matthewnelson.kmp.file.path
import io.matthewnelson.kmp.process.Output
import io.matthewnelson.kmp.process.Stdio
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.cancellation.CancellationException

internal expect val STDIO_NULL: File

@Throws(IOException::class)
internal fun File.isCanonicallyEqualTo(
    other: File,
): Boolean = isCanonicallyEqualTo(
    other = other,
    _absoluteFile2 = File::absoluteFile2,
    _canonicalFile2 = File::canonicalFile2,
)

@Throws(CancellationException::class, IOException::class)
internal suspend fun File.isCanonicallyEqualTo(
    other: File,
    fs: AsyncFs,
): Boolean = fs.with {
    isCanonicallyEqualTo(
        other = other,
        _absoluteFile2 = { absoluteFile2Async() },
        _canonicalFile2 = { canonicalFile2Async() },
    )
}

@Throws(IOException::class)
@OptIn(ExperimentalContracts::class)
private inline fun File.isCanonicallyEqualTo(
    other: File,
    _absoluteFile2: File.() -> File,
    _canonicalFile2: File.() -> File,
): Boolean {
    contract {
        callsInPlace(_absoluteFile2, InvocationKind.UNKNOWN)
        callsInPlace(_canonicalFile2, InvocationKind.UNKNOWN)
    }

    if (this == other) return true

    return try {
        this._canonicalFile2() == other._canonicalFile2()
    } catch (_: IOException) {
        this._absoluteFile2().normalize() == other._absoluteFile2().normalize()
    }
}

@Throws(IOException::class)
@OptIn(ExperimentalContracts::class)
internal inline fun Stdio.Config.isStderrSameFileAsStdout(
    _isCanonicallyEqualTo: File.(File) -> Boolean = File::isCanonicallyEqualTo,
): Boolean {
    contract { callsInPlace(_isCanonicallyEqualTo, InvocationKind.UNKNOWN) }
    if (stdout !is Stdio.File) return false
    if (stderr !is Stdio.File) return false
    return stdout.file._isCanonicallyEqualTo(stderr.file)
}

@Throws(IOException::class)
@OptIn(ExperimentalContracts::class)
internal inline fun ((Stdio, Stdio, Stdio) -> Stdio.Config).build(
    b: Stdio.Config.Builder,
    outputOptions: Output.Options?,
    _isCanonicallyEqualTo: File.(File) -> Boolean = File::isCanonicallyEqualTo,
    _mkdirs2: File.(String?, Boolean) -> File = File::mkdirs2,
): Stdio.Config {
    contract {
        callsInPlace(_isCanonicallyEqualTo, InvocationKind.UNKNOWN)
        callsInPlace(_mkdirs2, InvocationKind.UNKNOWN)
    }

    val isOutput = outputOptions != null
    val stdin = b.stdin.let { stdio ->
        if (outputOptions?.hasInput == true) return@let Stdio.Pipe
        if (isOutput && stdio is Stdio.Pipe) return@let Stdio.Null

        if (stdio !is Stdio.File) return@let stdio
        stdio.file.path.checkFileName { "stdin file name" }
        if (!stdio.append) return@let stdio
        Stdio.File.of(stdio.file, append = false)
    }

    val stdout = if (isOutput) Stdio.Pipe else b.stdout
    val stderr = if (isOutput) Stdio.Pipe else b.stderr

    arrayOf(
        "stdout" to stdout,
        "stderr" to stderr,
    ).forEach { (name, stdio) ->
        if (stdio !is Stdio.File) return@forEach
        if (stdio.file == STDIO_NULL) return@forEach
        stdio.file.path.checkFileName { "$name file name" }

        if (stdin is Stdio.File) {
            if (stdin.file._isCanonicallyEqualTo(stdio.file)) {
                throw IOException("$name cannot be the same file as stdin")
            }
        }

        stdio.file.parentFile?._mkdirs2(/* mode = */ null, /* mustCreate = */ false)
    }

    return this(stdin, stdout, stderr)
}
