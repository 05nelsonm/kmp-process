/*
 * Copyright (c) 2024 Matthew Nelson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
package io.matthewnelson.process

import io.matthewnelson.immutable.collections.toImmutableList
import io.matthewnelson.immutable.collections.toImmutableMap
import io.matthewnelson.process.internal.createProcess
import io.matthewnelson.process.internal.parentEnvironment

public abstract class Process internal constructor(
    public val command: String,
    public val args: List<String>,
    public val environment: Map<String, String>,
) {

    public class Builder(public val command: String) {

        private val env = parentEnvironment()
        private val args = mutableListOf<String>()

        public fun arg(
            arg: String,
        ): Builder = apply {
            args.add(arg)
        }

        public fun arg(
            vararg args: String,
        ): Builder = apply {
            args.forEach { this.args.add(it) }
        }

        public fun arg(
            args: List<String>,
        ): Builder = apply {
            args.forEach { this.args.add(it) }
        }

        public fun environment(
            key: String,
            value: String,
        ): Builder = apply {
            env[key] = value
        }

        public fun withEnvironment(
            block: MutableMap<String, String>.() -> Unit,
        ): Builder = apply {
            block(env)
        }

        @Throws(ProcessException::class)
        public fun start(): Process {
            if (command.isBlank()) throw ProcessException("command cannot be blank")

            return createProcess(
                command = command,
                args = args.toImmutableList(),
                env = env.toImmutableMap(),
            )
        }
    }
}
