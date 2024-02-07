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
package io.matthewnelson.process

import io.matthewnelson.process.internal.PATH_STDIO_NULL
import kotlin.jvm.JvmField
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

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
        public val Null: File get() = File.of(PATH_STDIO_NULL)
    }

    /**
     * Uses the specified [path] for the standard
     * input/output stream.
     *
     * Note that [append] is ignored when using with
     * [Process.Builder.stdin]
     *
     * @see [Null]
     * */
    public class File private constructor(
        @JvmField
        public val path: String,
        @JvmField
        public val append: Boolean,
    ): Stdio() {

        public companion object {

            @JvmStatic
            @JvmOverloads
            public fun of(
                path: String,
                append: Boolean = false,
            ): File {
                if (path == PATH_STDIO_NULL) return NULL
                return File(path, append)
            }

            private val NULL = File(PATH_STDIO_NULL, append = false)
        }

        override fun equals(other: Any?): Boolean {
            return  other is File
                    && other.path == path
                    && other.append == append
        }

        override fun hashCode(): Int {
            var result = 42
            result = result * 31 + path.hashCode()
            result = result * 31 + append.hashCode()
            return result
        }

        override fun toString(): String = "File[path=$path,append=$append]"
    }
}
