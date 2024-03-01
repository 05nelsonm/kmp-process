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
package io.matthewnelson.kmp.process

import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.normalize
import io.matthewnelson.kmp.file.parentFile
import io.matthewnelson.kmp.file.toFile
import io.matthewnelson.kmp.process.internal.STDIO_NULL
import io.matthewnelson.kmp.process.internal.isCanonicallyEqualTo
import kotlin.jvm.*

/**
 * Standard input/output stream types for [Process]
 * `stdin`, `stdout`, and `stderr`.
 * */
public sealed class Stdio private constructor() {

    /**
     * Inherit the system's standard input/output stream.
     * */
    public data object Inherit: Stdio()

    /**
     * Connects the [Process] with its parent via
     * a pipe.
     *
     * This is the default type for all [Process]
     * standard input/output streams.
     * */
    public data object Pipe: Stdio()

    public companion object {

        /**
         * Uses the system's "null file" destination
         * for the standard input/output stream.
         *
         * Windows: `NUL`
         * Unix: `/dev/null`
         * */
        @JvmStatic
        @get:JvmName("Null")
        public val Null: File get() = File.of(STDIO_NULL)
    }

    /**
     * Uses the specified [file] for the standard
     * input/output stream.
     *
     * Note that [append] is ignored when using with
     * [Process.Builder.stdin]
     *
     * @see [Null]
     * */
    public class File private constructor(
        @JvmField
        public val file: io.matthewnelson.kmp.file.File,
        @JvmField
        public val append: Boolean,
    ): Stdio() {

        public companion object {

            @JvmStatic
            @JvmOverloads
            public fun of(
                path: String,
                append: Boolean = false,
            ): File = of(path.toFile(), append)

            @JvmStatic
            @JvmOverloads
            public fun of(
                file: io.matthewnelson.kmp.file.File,
                append: Boolean = false,
            ): File {
                if (file == STDIO_NULL) return NULL
                return File(file.normalize(), append)
            }

            private val NULL = File(STDIO_NULL, append = false)
        }

        override fun equals(other: Any?): Boolean {
            return  other is File
                    && other.file == file
                    && other.append == append
        }

        override fun hashCode(): Int {
            var result = 42
            result = result * 31 + file.hashCode()
            result = result * 31 + append.hashCode()
            return result
        }
    }

    public class Config private constructor(
        @JvmField
        public val stdin: Stdio,
        @JvmField
        public val stdout: Stdio,
        @JvmField
        public val stderr: Stdio,
    ) {

        internal class Builder private constructor() {

            internal var stdin: Stdio = Pipe
            internal var stdout: Stdio = Pipe
            internal var stderr: Stdio = Pipe

            @Throws(IOException::class)
            internal fun build(outputOptions: Output.Options?): Config {
                val isOutput = outputOptions != null

                val stdin = stdin.let { stdio ->
                    if (outputOptions?.hasInput == true) return@let Pipe
                    if (isOutput && stdio is Pipe) return@let Null

                    if (stdio !is File) return@let stdio
                    if (!stdio.append) return@let stdio
                    File.of(stdio.file, append = false)
                }

                val stdout = if (isOutput) Pipe else stdout
                val stderr = if (isOutput) Pipe else stderr

                listOf(
                    "stdout" to stdout,
                    "stderr" to stderr
                ).forEach { (name, stdio) ->
                    if (stdio !is File) return@forEach
                    if (stdio.file == STDIO_NULL) return@forEach

                    if (stdin is File && stdin.file.isCanonicallyEqualTo(stdio.file)) {
                        throw IOException("$name cannot be the same file as stdin")
                    }

                    val parent = stdio.file
                        .parentFile
                        ?: return@forEach

                    if (!parent.exists() && !parent.mkdirs()) {
                        throw IOException("Failed to create parent directory for $name[${stdio.file}]")
                    }
                }

                return Config(stdin, stdout, stderr)
            }

            internal companion object {

                @JvmSynthetic
                internal fun get() = Builder()
            }
        }

        @get:JvmSynthetic
        internal val isStderrSameFileAsStdout: Boolean get() {
            if (stdout !is File) return false
            if (stderr !is File) return false

            return stdout.file.isCanonicallyEqualTo(stderr.file)
        }

        @JvmSynthetic
        internal fun iterator(): Iterator<Pair<String, Stdio>> = listOf(
            "stdin" to stdin,
            "stdout" to stdout,
            "stderr" to stderr,
        ).iterator()

        override fun equals(other: Any?): Boolean {
            return  other is Config
                    && other.stdin == stdin
                    && other.stdout == stdout
                    && other.stderr == stderr
        }

        override fun hashCode(): Int {
            var result = 17
            result = result * 31 + stdin.hashCode()
            result = result * 31 + stdout.hashCode()
            result = result * 31 + stderr.hashCode()
            return result
        }

        override fun toString(): String = buildString {
            appendLine("Stdio.Config: [")
            append("    stdin: ")
            appendLine(stdin)
            append("    stdout: ")
            appendLine(stdout)
            append("    stderr: ")
            appendLine(stderr)
            append(']')
        }
    }

    final override fun toString(): String = buildString {
        append("Stdio.")
        when (this@Stdio) {
            is File -> {
                append("File[file=")
                append(file)
                append(", append=")
                append(append)
                append(']')
            }
            is Inherit -> append("Inherit")
            is Pipe -> append("Pipe")
        }
    }
}
