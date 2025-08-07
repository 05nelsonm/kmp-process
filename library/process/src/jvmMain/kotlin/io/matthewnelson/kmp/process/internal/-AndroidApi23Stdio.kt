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
@file:Suppress("NOTHING_TO_INLINE")

package io.matthewnelson.kmp.process.internal

import io.matthewnelson.kmp.file.ANDROID
import io.matthewnelson.kmp.file.Closeable
import io.matthewnelson.kmp.file.FileStream
import io.matthewnelson.kmp.file.FileStream.Companion.asInputStream
import io.matthewnelson.kmp.file.FileStream.Companion.asOutputStream
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.openRead
import io.matthewnelson.kmp.file.openWrite
import io.matthewnelson.kmp.process.Stdio
import kotlin.concurrent.Volatile

/**
 * Android API 23 and below does not have [java.lang.ProcessBuilder.Redirect]. This
 * is a supplemental implementation for [PlatformBuilder.spawn] which will open all
 * [Stdio.File] needed prior to spawning the process.
 * */
internal class AndroidApi23Stdio private constructor(
    internal val stdio: Stdio.Config,
): Closeable {

    internal val isStderrSameFileAsStdout = stdio.isStderrSameFileAsStdout()

    @Volatile
    private var _stdin: FileStream.Read? = null
    @Volatile
    private var _stdout: FileStream.Write? = null
    @Volatile
    private var _stderr: FileStream.Write? = null

    internal val stdinFS: ReadStream? by lazy { _stdin?.asInputStream(closeParentOnClose = true) }
    internal val stdoutFS: WriteStream? by lazy { _stdout?.asOutputStream(closeParentOnClose = true) }
    internal val stderrFS: WriteStream? by lazy { _stderr?.asOutputStream(closeParentOnClose = true) }

    override fun close() {
        val stdio = arrayOf(_stdin, _stdout, _stderr)
        _stdin = null
        _stdout = null
        _stderr = null

        var threw: Throwable? = null
        stdio.forEach { closeable ->
            if (closeable == null) return@forEach

            try {
                closeable.close()
            } catch (e: IOException) {
                if (threw == null) {
                    threw = e
                } else {
                    threw.addSuppressed(e)
                }
            }
        }
        if (threw != null) throw threw
    }

    internal companion object {

        @JvmSynthetic
        @Throws(IOException::class)
        internal fun getOrNull(stdio: Stdio.Config): AndroidApi23Stdio? {
            val sdkInt = ANDROID.SDK_INT ?: return null
            if (sdkInt >= 24) return null

            // /proc/self/fd/{0, 1, 2} are all symlinked to /dev/null
            val b = Stdio.Config.Builder.get()
            b.stdin = if (stdio.stdin is Stdio.Inherit) Stdio.Null else stdio.stdin
            b.stdout = if (stdio.stdout is Stdio.Inherit) Stdio.Null else stdio.stdout
            b.stderr = if (stdio.stderr is Stdio.Inherit) Stdio.Null else stdio.stderr

            // Android 23-
            val ret = AndroidApi23Stdio(b.build(null))

            try {
                ret._stdin = ret.stdio.stdin.fileOrNull(isStdin = true)?.file?.openRead()
                ret._stdout = ret.stdio.stdout.fileOrNull(isStdin = false)?.openWrite()
                if (!ret.isStderrSameFileAsStdout) {
                    ret._stderr = ret.stdio.stderr.fileOrNull(isStdin = false)?.openWrite()
                }

                // convert FileStreams to Input/Output streams
                ret.stdinFS
                ret.stdoutFS
                ret.stderrFS
            } catch (e: IOException) {
                try {
                    ret.close()
                } catch (ee: IOException) {
                    e.addSuppressed(ee)
                }
                throw e
            }

            return ret
        }
    }
}

private inline fun Stdio.fileOrNull(isStdin: Boolean): Stdio.File? {
    if (this !is Stdio.File) return null
    // JvmProcess's stdin OutputStream gets closed if STDIO_NULL is expressed.
    if (isStdin && file == STDIO_NULL) return null
    return this
}

@Throws(IOException::class)
private inline fun Stdio.File.openWrite(): FileStream.Write {
    return file.openWrite(excl = null, appending = append)
}
