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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "NOTHING_TO_INLINE", "DEPRECATION")

package io.matthewnelson.kmp.process

/**
 * DEPRECATED since `0.6.0`
 * @suppress
 * */
@Deprecated(
    message = "Replaced by internal implementation due to unintended boxing. Do not use.",
    level = DeprecationLevel.WARNING,
)
public expect value class ReadBuffer private constructor(private val _buf: Any) {

    public abstract class LineOutputFeed internal constructor() {

        @Throws(IllegalStateException::class)
        public abstract fun onData(buf: ReadBuffer, len: Int)

        public abstract fun close()
    }

    public companion object {

        @InternalProcessApi
        public fun allocate(): ReadBuffer

        @InternalProcessApi
        public fun lineOutputFeed(dispatch: (line: String?) -> Unit): LineOutputFeed
    }

    internal inline fun capacity(): Int
    internal inline operator fun get(index: Int): Byte
}
