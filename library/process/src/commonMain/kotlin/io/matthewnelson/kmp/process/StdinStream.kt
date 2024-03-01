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

internal expect abstract class StdinStream internal constructor() {

    @Throws(IllegalArgumentException::class, IndexOutOfBoundsException::class, IOException::class)
    public open fun write(buf: ByteArray, offset: Int, len: Int)

    @Throws(IOException::class)
    public fun write(buf: ByteArray)

    @Throws(IOException::class)
    public open fun close()

    @Throws(IOException::class)
    public open fun flush()
}
