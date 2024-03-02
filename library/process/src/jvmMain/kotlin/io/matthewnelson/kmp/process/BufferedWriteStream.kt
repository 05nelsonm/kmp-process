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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package io.matthewnelson.kmp.process

import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.process.internal.WriteStream
import java.io.BufferedOutputStream

public actual sealed class BufferedWriteStream actual constructor(
    stream: WriteStream,
): BufferedOutputStream(stream, 1) {

    // java.lang.Process's stdin stream (when Stdio.Pipe) is a
    // ProcessPipeOutputStream which extends BufferedOutputStream
    // already.
    //
    // This is simply to provide blocking APIs to AsyncWriteStream
    // and also be compatible with Java only consumers by extending
    // and overriding BufferedOutputStream.
    private val stream = stream.buffered()

    @Throws(IllegalArgumentException::class, IndexOutOfBoundsException::class, IOException::class)
    public actual final override fun write(buf: ByteArray, offset: Int, len: Int) { stream.write(buf, offset, len) }

    @Throws(IOException::class)
    public actual final override fun write(buf: ByteArray) { stream.write(buf, 0, buf.size) }

    @Throws(IOException::class)
    public final override fun write(b: Int) { stream.write(b) }

    @Throws(IOException::class)
    public actual final override fun close() { stream.close() }

    @Throws(IOException::class)
    public actual final override fun flush() { stream.flush() }
}
