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

package io.matthewnelson.kmp.process.internal

import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.process.*

internal expect class PlatformBuilder private constructor() {

    internal val env: MutableMap<String, String>

    @Throws(IOException::class)
    internal fun output(
        command: String,
        args: List<String>,
        chdir: File?,
        env: Map<String, String>,
        stdio: Stdio.Config,
        options: Output.Options,
        destroy: Signal,
    ): Output

    @Throws(IOException::class)
    internal fun spawn(
        command: String,
        args: List<String>,
        chdir: File?,
        env: Map<String, String>,
        stdio: Stdio.Config,
        destroy: Signal,
        handler: ProcessException.Handler,
    ): Process

    internal companion object {

        internal fun get(): PlatformBuilder

        internal fun myPid(): Int
    }
}
