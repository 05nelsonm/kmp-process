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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT")

package io.matthewnelson.process

import io.matthewnelson.immutable.collections.toImmutableList
import io.matthewnelson.immutable.collections.toImmutableMap
import io.matthewnelson.process.internal.*
import io.matthewnelson.process.internal.commonArgs
import io.matthewnelson.process.internal.commonEnvironment
import io.matthewnelson.process.internal.commonIsAlive
import io.matthewnelson.process.internal.commonWithEnvironment
import java.io.IOException
import kotlin.time.Duration

/**
 * A Process.
 *
 * @see [Builder]
 * */
public actual sealed class Process actual constructor(
    @JvmField
    public actual val command: String,
    @JvmField
    public actual val args: List<String>,
    @JvmField
    public actual val environment: Map<String, String>,
    @JvmField
    public actual val stdio: Stdio.Config,
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

    @get:JvmName("isAlive")
    public actual val isAlive: Boolean get() = commonIsAlive()

    /**
     * Blocks the current thread until [Process] completion.
     *
     * @return The [Process.exitCode]
     * @throws [InterruptedException]
     * */
    @Throws(InterruptedException::class)
    public actual abstract fun waitFor(): Int

    /**
     * Blocks the current thread for the specified [timeout],
     * or until [Process.exitCode] is available (i.e. the
     * [Process] completed).
     *
     * @param [timeout] the [Duration] to wait
     * @return The [Process.exitCode], or null if [timeout] is
     *   exceeded without [Process] completion.
     * @throws [InterruptedException]
     * */
    @Throws(InterruptedException::class)
    public actual abstract fun waitFor(timeout: Duration): Int?

    /**
     * Delays the current coroutine until [Process] completion.
     *
     * @return The [Process.exitCode]
     * */
    public actual abstract suspend fun waitForAsync(): Int

    /**
     * Delays the current coroutine for the specified [timeout],
     * or until [Process.exitCode] is available (i.e. the
     * [Process] completed).
     *
     * @param [timeout] the [Duration] to wait
     * @return The [Process.exitCode], or null if [timeout] is
     *   exceeded without [Process] completion.
     * */
    public actual abstract suspend fun waitForAsync(timeout: Duration): Int?

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
     *         .args("-c")
     *         .args("sleep 1; exit 5")
     *         .environment("HOME", appDir.absolutePath)
     *         .stdin(Stdio.Null)
     *         .stdout(Stdio.Inherit)
     *         .stderr(Stdio.Pipe)
     *         .spawn()
     *
     * e.g. (Executable file)
     *
     *     val p = Process.Builder(myExecutable.absolutePath)
     *         .args("--some-flag")
     *         .args("someValue")
     *         .args("--another-flag", "anotherValue")
     *         .withEnvironment {
     *             remove("HOME")
     *             // ...
     *         }
     *         .stdin(Stdio.Null)
     *         .stdout(Stdio.File.of("myProgram.log", append = true))
     *         .stderr(Stdio.File.of("myProgram.err"))
     *         .spawn()
     *
     * @param [command] The command to run.
     * */
    public actual class Builder public actual constructor(
        @JvmField
        public actual val command: String,
    ) {

        private val jProcessBuilder = ProcessBuilder(emptyList())
        private val env by lazy { jProcessBuilder.environment() }
        private val args = mutableListOf<String>()
        private val stdio = Stdio.Config.Builder()

        public actual fun args(
            arg: String,
        ): Builder = commonArgs(args, arg)

        public actual fun args(
            vararg args: String,
        ): Builder = commonArgs(this.args, args)

        public actual fun args(
            args: List<String>,
        ): Builder = commonArgs(this.args, args)

        public actual fun environment(
            key: String,
            value: String,
        ): Builder = commonEnvironment(env, key, value)

        public actual fun withEnvironment(
            block: MutableMap<String, String>.() -> Unit,
        ): Builder = commonWithEnvironment(env, block)

        public actual fun stdin(
            source: Stdio,
        ): Builder = commonStdin(stdio, source)

        public actual fun stdout(
            destination: Stdio,
        ): Builder = commonStdout(stdio, destination)

        public actual fun stderr(
            destination: Stdio,
        ): Builder = commonStderr(stdio, destination)

        @Throws(ProcessException::class)
        public actual fun spawn(): Process {
            commonCheckCommand()

            val args = args.toImmutableList()
            val env = env.toImmutableMap()
            val stdio = stdio.build()

            val jCommands = ArrayList<String>(args.size + 1)
            jCommands.add(command)
            jCommands.addAll(args)

            jProcessBuilder.command(jCommands)
            jProcessBuilder.configureRedirects(stdio)

            val jProcess = try {
                jProcessBuilder.start()
            } catch (e: IOException) {
                throw ProcessException(e)
            }

            return JvmProcess.of(command, args, env, stdio, jProcess)
        }
    }
}
