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

import io.matthewnelson.immutable.collections.toImmutableList
import io.matthewnelson.immutable.collections.toImmutableMap
import io.matthewnelson.process.internal.*
import io.matthewnelson.process.internal.commonArg
import io.matthewnelson.process.internal.commonEnvironment
import io.matthewnelson.process.internal.commonIsAlive
import io.matthewnelson.process.internal.commonWithEnvironment

/**
 * A Process.
 *
 * @see [Builder]
 * @see [io.matthewnelson.process.waitFor]
 * */
public actual sealed class Process actual constructor(
    public actual val command: String,
    public actual val args: List<String>,
    public actual val environment: Map<String, String>,
) {

    /**
     * Returns the exit code for which the process
     * completed with.
     *
     * @throws [ProcessException] if the [Process] has
     *   not exited yet
     * */
    @Throws(ProcessException::class)
    public actual abstract fun exitCode(): Int

    public actual val isAlive: Boolean get() = commonIsAlive()

    /**
     * Kills the [Process] via signal SIGTERM and closes
     * all Pipes.
     * */
    public actual abstract fun sigterm(): Process

    /**
     * Kills the [Process] via signal SIGKILL and closes
     * all Pipes.
     *
     * Note that for Android API < 26, sigterm is utilized
     * as java.lang.Process.destroyForcibly is unavailable.
     * */
    public actual abstract fun sigkill(): Process

    /**
     * Creates a new [Process].
     *
     * e.g. (shell commands)
     *
     *     val p = Process.Builder("sh")
     *         .arg("-c")
     *         .arg("sleep 1; exit 5")
     *         .environment("HOME", appDir.absolutePath)
     *         .start()
     *
     * e.g. (Executable file)
     *
     *     val p = Process.Builder(myExecutable.absolutePath)
     *         .arg("--some-flag")
     *         .arg("someValue")
     *         .arg("--another-flag", "anotherValue")
     *         .withEnvironment {
     *             remove("HOME")
     *             // ...
     *         }
     *         .start()
     * */
    public actual class Builder public actual constructor(
        public actual val command: String
    ) {

        private val env by lazy { parentEnvironment() }
        private val args = mutableListOf<String>()

        public actual fun arg(
            arg: String,
        ): Builder = commonArg(args, arg)

        public actual fun arg(
            vararg args: String,
        ): Builder = commonArg(this.args, *args)

        public actual fun arg(
            args: List<String>,
        ): Builder = commonArg(this.args, args)

        public actual fun environment(
            key: String,
            value: String,
        ): Builder = commonEnvironment(env, key, value)

        public actual fun withEnvironment(
            block: MutableMap<String, String>.() -> Unit
        ): Builder = commonWithEnvironment(env, block)

        @Throws(ProcessException::class)
        public actual fun start(): Process = createProcess(
            command = command,
            args = args.toImmutableList(),
            env = env.toImmutableMap(),
        )
    }
}
