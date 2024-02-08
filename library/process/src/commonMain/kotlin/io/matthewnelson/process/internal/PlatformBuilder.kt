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

package io.matthewnelson.process.internal

import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.process.Process
import io.matthewnelson.process.Stdio

internal expect class PlatformBuilder internal constructor() {

    internal val env: MutableMap<String, String>

    @Throws(IOException::class)
    internal fun build(
        command: String,
        args: List<String>,
        env: Map<String, String>,
        stdio: Stdio.Config,
    ): Process
}
