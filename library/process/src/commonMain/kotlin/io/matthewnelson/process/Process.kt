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
import io.matthewnelson.process.internal.JavaLock
import kotlin.jvm.JvmField
import kotlin.jvm.JvmName

/**
 * A Process.
 *
 * @see [Builder]
 * @see [io.matthewnelson.process.waitFor]
 * */
public abstract class Process internal constructor(
    @JvmField
    public val command: String,
    @JvmField
    public val args: List<String>,
    @JvmField
    public val environment: Map<String, String>,

    @Suppress("UNUSED_PARAMETER")
    unused: JavaLock,
): PlatformProcess() {

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
    @get:JvmName("isProcessAlive")
    public val isAlive: Boolean get() = try {
        exitCode()
        false
    } catch (_: ProcessException) {
        true
    }

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
     *
     * @see [PlatformProcessBuilder]
     * */
    public class Builder(
        @JvmField
        public val command: String
    ): PlatformProcessBuilder() {

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
