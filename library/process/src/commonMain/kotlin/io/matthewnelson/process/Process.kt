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

/**
 * A Process.
 *
 * @see [Builder]
 * @see [io.matthewnelson.process.waitFor]
 * */
public expect sealed class Process(
    command: String,
    args: List<String>,
    environment: Map<String, String>,
) {
    public val command: String
    public val args: List<String>
    public val environment: Map<String, String>

    /**
     * Returns the exit code for which the process
     * completed with.
     *
     * @throws [ProcessException] if the [Process] has
     *   not exited yet
     * */
    @Throws(ProcessException::class)
    public abstract fun exitCode(): Int

    // java.lang.Process.isAlive() is only available for
    // Android API 26+. This provides the functionality
    // w/o conflicting with java.lang.Process' function.
    public val isAlive: Boolean

    /**
     * Kills the [Process] via signal SIGTERM and closes
     * all Pipes.
     * */
    public abstract fun sigterm(): Process

    /**
     * Kills the [Process] via signal SIGKILL and closes
     * all Pipes.
     *
     * Note that for Android API < 26, sigterm is utilized
     * as java.lang.Process.destroyForcibly is unavailable.
     * */
    public abstract fun sigkill(): Process

    public class Builder(command: String) {
        public val command: String

        public fun arg(arg: String): Builder
        public fun arg(vararg args: String): Builder
        public fun arg(args: List<String>): Builder

        public fun environment(key: String, value: String): Builder
        public fun withEnvironment(block: MutableMap<String, String>.() -> Unit): Builder

        @Throws(ProcessException::class)
        public fun start(): Process
    }
}
