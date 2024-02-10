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

import io.matthewnelson.immutable.collections.immutableListOf
import io.matthewnelson.kmp.file.toFile
import io.matthewnelson.kmp.process.internal.STDIO_NULL
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
                return File(file, append)
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

        /* isInput, Stdio */
        @JvmSynthetic
        internal fun iterator(): Iterator<Pair<Boolean, Stdio>> = immutableListOf(
            Pair(true, stdin),
            Pair(false, stdout),
            Pair(false, stderr),
        ).iterator()

        internal class Builder private constructor() {

            internal var stdin: Stdio = Pipe
            internal var stdout: Stdio = Pipe
            internal var stderr: Stdio = Pipe

            internal fun build(): Config {
                val stdin = stdin.let {
                    if (it !is File) return@let it
                    if (!it.append) return@let it
                    File.of(it.file, append = false)
                }

                return Config(stdin, stdout, stderr)
            }

            internal companion object {

                @JvmSynthetic
                internal fun get() = Builder()
            }
        }

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
