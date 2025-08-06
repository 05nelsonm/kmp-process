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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "RedundantVisibilityModifier")

package io.matthewnelson.kmp.process

import io.matthewnelson.kmp.file.Closeable
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.process.internal.WriteStream
import java.io.BufferedOutputStream

/**
 * TODO
 * */
public actual sealed class BufferedWriteStream actual constructor(
    stream: WriteStream,
): BufferedOutputStream(stream), Closeable {

    /**
     * TODO
     *
     * @throws [IOException]
     * @throws [IndexOutOfBoundsException]
     * */
    @Throws(IOException::class)
    public actual final override fun write(buf: ByteArray, offset: Int, len: Int) {
        super.write(buf, offset, len)
    }

    /**
     * TODO
     * */
    @Throws(IOException::class)
    public actual final override fun write(buf: ByteArray) {
        super.write(buf)
    }

    /**
     * TODO
     * */
    @Throws(IOException::class)
    public final override fun write(b: Int) {
        super.write(b)
    }

    /**
     * TODO
     * */
    @Throws(IOException::class)
    public actual override fun flush() {
        super.flush()
    }

    /**
     * TODO
     * */
    @Throws(IOException::class)
    public actual override fun close() {
        super.close()
    }
}
