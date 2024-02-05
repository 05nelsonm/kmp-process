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

package io.matthewnelson.process

import io.matthewnelson.process.internal.JvmProcess
import java.io.IOException

// jvmMain
public actual sealed class PlatformProcessBuilder {

    private val jProcessBuilder = ProcessBuilder(emptyList())

    @get:JvmName("env")
    protected actual val env: MutableMap<String, String> by lazy {
        jProcessBuilder.environment()
    }

    @Throws(ProcessException::class)
    protected actual fun createProcess(
        command: String,
        args: List<String>,
        env: Map<String, String>
    ): Process = try {
        val commands = ArrayList<String>(args.size + 1)
        commands.add(command)
        commands.addAll(args)

        jProcessBuilder.command(commands)

        JvmProcess.of(command, args, env, jProcessBuilder.start())
    } catch (e: IOException) {
        throw ProcessException(e)
    }
}
