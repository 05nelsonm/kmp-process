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

import io.matthewnelson.encoding.core.EncoderDecoder.Companion.DEFAULT_BUFFER_SIZE
import io.matthewnelson.kmp.process.internal.RealLineOutputFeed
import kotlin.jvm.JvmInline

/**
 * DEPRECATED since `0.6.0`
 * @suppress
 * */
@Deprecated(
    message = "Replaced by internal implementation due to unintended boxing. Do not use.",
    level = DeprecationLevel.WARNING,
)
@JvmInline
public actual value class ReadBuffer private actual constructor(private actual val _buf: Any) {

    public val buf: ByteArray get() = _buf as ByteArray

    public actual abstract class LineOutputFeed internal actual constructor() {

        @Throws(IllegalStateException::class)
        public actual abstract fun onData(buf: ReadBuffer, len: Int)
        public actual abstract fun close()
    }

    public actual companion object {

        @InternalProcessApi
        public actual fun allocate(): ReadBuffer {
            return ReadBuffer(ByteArray(DEFAULT_BUFFER_SIZE))
        }

        @InternalProcessApi
        public actual fun lineOutputFeed(
            dispatch: (line: String?) -> Unit,
        ): LineOutputFeed = RealLineOutputFeed(dispatch)

        @InternalProcessApi
        public fun of(buf: ByteArray): ReadBuffer = ReadBuffer(buf)
    }

    internal actual inline fun capacity(): Int = buf.size
    internal actual inline operator fun get(index: Int): Byte = buf[index]
}
