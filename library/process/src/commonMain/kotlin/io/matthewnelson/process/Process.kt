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

import kotlin.time.Duration

/**
 * A Process.
 *
 * @see [Builder]
 * */
public expect sealed class Process(
    command: String,
    args: List<String>,
    environment: Map<String, String>,
    stdio: Stdio.Config,
) {
    public val command: String
    public val args: List<String>
    public val environment: Map<String, String>
    public val stdio: Stdio.Config

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
     * Blocks the current thread until [Process] completion.
     *
     * @return The [Process.exitCode]
     * @throws [InterruptedException]
     * @throws [UnsupportedOperationException] on Node.js
     * */
    @Throws(InterruptedException::class, UnsupportedOperationException::class)
    public abstract fun waitFor(): Int

    /**
     * Blocks the current thread for the specified [timeout],
     * or until [Process.exitCode] is available (i.e. the
     * [Process] completed).
     *
     * @param [timeout] the [Duration] to wait
     * @return The [Process.exitCode], or null if [timeout] is
     *   exceeded without [Process] completion.
     * @throws [InterruptedException]
     * @throws [UnsupportedOperationException] on Node.js
     * */
    @Throws(InterruptedException::class, UnsupportedOperationException::class)
    public abstract fun waitFor(timeout: Duration): Int?

    /**
     * Delays the current coroutine until [Process] completion.
     *
     * @return The [Process.exitCode]
     * */
    public abstract suspend fun waitForAsync(): Int

    /**
     * Delays the current coroutine for the specified [timeout],
     * or until [Process.exitCode] is available (i.e. the
     * [Process] completed).
     *
     * @param [timeout] the [Duration] to wait
     * @return The [Process.exitCode], or null if [timeout] is
     *   exceeded without [Process] completion.
     * */
    public abstract suspend fun waitForAsync(timeout: Duration): Int?

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
     * @param [command] The command to run. On `Linux`, `macOS` and `iOS` if
     *   [command] is a relative file path or program name (e.g. `ping`) then
     *   `posix_spawnp` is utilized. If it is an absolute file path
     *   (e.g. `/usr/bin/ping`), then `posix_spawn` is utilized.
     * */
    public class Builder(command: String) {
        public val command: String

        public fun args(arg: String): Builder
        public fun args(vararg args: String): Builder
        public fun args(args: List<String>): Builder

        public fun environment(key: String, value: String): Builder
        public fun withEnvironment(block: MutableMap<String, String>.() -> Unit): Builder

        public fun stdin(source: Stdio): Builder
        public fun stdout(destination: Stdio): Builder
        public fun stderr(destination: Stdio): Builder

        @Throws(ProcessException::class)
        public fun spawn(): Process
    }
}
