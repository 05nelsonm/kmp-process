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
package io.matthewnelson.kmp.process.internal.stdio

import io.matthewnelson.kmp.process.StdinStream
import io.matthewnelson.kmp.process.internal.stream_Writable

internal class RealStdinStream internal constructor(
    private val stream: stream_Writable,
): StdinStream() {

    // @Throws(IllegalArgumentException::class, IndexOutOfBoundsException::class, IOException::class)
    override fun write(buf: ByteArray, offset: Int, len: Int) {
        // TODO
        super.write(buf, offset, len)
    }

    // @Throws(IOException::class)
    override fun flush() {
        // TODO
        super.flush()
    }

    // @Throws(IOException::class)
    override fun close() {
        // TODO
        super.close()
    }
}
