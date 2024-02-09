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
package io.matthewnelson.kmp.process

import io.matthewnelson.immutable.collections.toImmutableList
import io.matthewnelson.immutable.collections.toImmutableMap
import io.matthewnelson.kmp.file.*
import io.matthewnelson.kmp.process.internal.STDIO_NULL
import io.matthewnelson.kmp.process.internal.PlatformBuilder
import io.matthewnelson.kmp.process.internal.commonWaitFor
import kotlin.jvm.JvmField
import kotlin.jvm.JvmName
import kotlin.time.Duration

/**
 * A Process.
 *
 * @see [Builder]
 * */
public abstract class Process internal constructor(
    @JvmField
    public val command: String,
    @JvmField
    public val args: List<String>,
    @JvmField
    public val environment: Map<String, String>,
    @JvmField
    public val stdio: Stdio.Config,
) {

    /**
     * Returns the exit code for which the process
     * completed with.
     *
     * @throws [IllegalStateException] if the [Process] has
     *   not exited yet
     * */
    @Throws(IllegalStateException::class)
    public abstract fun exitCode(): Int

    // java.lang.Process.isAlive() is only available for
    // Android API 26+. This provides the functionality
    // w/o conflicting with java.lang.Process' function.
    @get:JvmName("isAlive")
    public val isAlive: Boolean get() = try {
        exitCode()
        false
    } catch (_: IllegalStateException) {
        true
    }

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
     * Blocks the current thread for the specified [duration],
     * or until [Process.exitCode] is available (i.e. the
     * [Process] completed).
     *
     * @param [duration] the [Duration] to wait
     * @return The [Process.exitCode], or null if [duration] is
     *   exceeded without [Process] completion.
     * @throws [InterruptedException]
     * @throws [UnsupportedOperationException] on Node.js
     * */
    @Throws(InterruptedException::class, UnsupportedOperationException::class)
    public abstract fun waitFor(duration: Duration): Int?

    /**
     * Delays the current coroutine until [Process] completion.
     *
     * **NOTE:** This API requires the `kotlinx.coroutines` core
     * dependency (at a minimum) in order to pass in the
     * `kotlinx.coroutines.delay` function. Adding the dependency
     * to `kmp-process` for a single function to use in an API
     * that may not even be utilized (because [waitFor] exists for
     * non-JS) seemed ridiculous.
     *
     * e.g.
     *
     *     myProcess.waitForAsync(::delay)
     *
     * @param [delay] `kotlinx.coroutines.delay` function (e.g. `::delay`)
     * @return The [Process.exitCode]
     * */
    public suspend fun waitForAsync(
        delay: suspend (duration: Duration) -> Unit,
    ): Int {
        var exitCode: Int? = null
        while (exitCode == null) {
            exitCode = waitForAsync(Duration.INFINITE, delay)
        }
        return exitCode
    }

    /**
     * Delays the current coroutine for the specified [duration],
     * or until [Process.exitCode] is available (i.e. the
     * [Process] completed).
     *
     * **NOTE:** This API requires the `kotlinx.coroutines` core
     * dependency (at a minimum) in order to pass in the
     * `kotlinx.coroutines.delay` function. Adding the dependency
     * to `kmp-process` for a single function to use in an API
     * that may not even be utilized (because [waitFor] exists for
     * non-JS) seemed ridiculous.
     *
     * e.g.
     *
     *     myProcess.waitForAsync(250.milliseconds, ::delay)
     *
     * @param [duration] the [Duration] to wait
     * @param [delay] `kotlinx.coroutines.delay` function (e.g. `::delay`)
     * @return The [Process.exitCode], or null if [duration] is
     *   exceeded without [Process] completion.
     * */
    public suspend fun waitForAsync(
        duration: Duration,
        delay: suspend (duration: Duration) -> Unit,
    ): Int? {
        return commonWaitFor(duration) { delay(it) }
    }

    /**
     * Kills the [Process] via signal SIGTERM.
     * */
    public abstract fun sigterm(): Process

    /**
     * Kills the [Process] via signal SIGKILL.
     *
     * Note that for Android API < 26, [sigterm] is utilized
     * under the hood as java.lang.Process.destroyForcibly is
     * not available.
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
     * @param [command] The command to run. On Native `Linux`, `macOS` and
     *   `iOS`, if [command] is a relative file path or program name (e.g.
     *   `ping`) then `posix_spawnp` is utilized. If it is an absolute file
     *   path (e.g. `/usr/bin/ping`), then `posix_spawn` is utilized.
     * */
    public class Builder(
        @JvmField
        public val command: String
    ) {

        /**
         * Alternate constructor for an executable [File]. Will take the
         * normalized path to use for [command].
         * */
        public constructor(executable: File): this(executable.normalize().path)

        private val platform = PlatformBuilder()
        private val args = mutableListOf<String>()
        private val stdio = Stdio.Config.Builder.get()

        /**
         * Add a single argument
         * */
        public fun args(
            arg: String,
        ): Builder = apply { args.add(arg) }

        /**
         * Add multiple arguments
         * */
        public fun args(
            vararg args: String,
        ): Builder = apply { args.forEach { this.args.add(it) } }

        /**
         * Add multiple arguments
         * */
        public fun args(
            args: List<String>,
        ): Builder = apply { args.forEach { this.args.add(it) } }

        /**
         * Set/overwrite an environment variable
         *
         * By default, the new [Process] will inherit all
         * environment variables from the current one.
         * */
        public fun environment(
            key: String,
            value: String,
        ): Builder = apply { platform.env[key] = value }

        /**
         * Modify the environment via lambda
         *
         * By default, the new [Process] will inherit all
         * environment variables from the current one.
         * */
        public fun environment(
            block: MutableMap<String, String>.() -> Unit,
        ): Builder = apply { block(platform.env) }

        /**
         * Modify the standard input source
         *
         * @see [Stdio]
         * */
        public fun stdin(
            source: Stdio,
        ): Builder = apply { stdio.stdin = source }

        /**
         * Modify the standard output destination
         *
         * @see [Stdio]
         * */
        public fun stdout(
            destination: Stdio,
        ): Builder = apply { stdio.stdout = destination }

        /**
         * Modify the standard error output destination
         *
         * @see [Stdio]
         * */
        public fun stderr(
            destination: Stdio,
        ): Builder = apply { stdio.stderr = destination }

        /**
         * Spawn a new [Process]
         *
         * @throws [IOException] if [Process] creation failed
         * */
        @Throws(IOException::class)
        public fun spawn(): Process {
            if (command.isBlank()) throw IOException("command cannot be blank")
            command.toFile().let { cmd ->
                if (!cmd.isAbsolute()) return@let
                if (!cmd.exists()) throw FileNotFoundException("$cmd")
            }

            val stdio = stdio.build()

            stdio.iterator().forEach { (isInput, it) ->
                if (it !is Stdio.File) return@forEach
                if (it.file == STDIO_NULL) return@forEach

                if (isInput) {
                    if (!it.file.exists()) throw FileNotFoundException("stdin: $it")
                    return@forEach
                }

                // output
                val parent = it.file
                    .parentFile
                    ?: return@forEach

                if (!parent.exists() && !parent.mkdirs()) {
                    throw IOException("Failed to mkdirs for $parent")
                }
            }

            val args = args.toImmutableList()
            val env = platform.env.toImmutableMap()

            return platform.build(command, args, env, stdio)
        }
    }

    // TODO: equals
    // TODO: hashCode
    // TODO: toString
}
