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
import io.matthewnelson.kmp.process.internal.*
import io.matthewnelson.kmp.process.internal.PlatformBuilder
import io.matthewnelson.kmp.process.internal.SyntheticAccess
import io.matthewnelson.kmp.process.internal.appendProcessInfo
import io.matthewnelson.kmp.process.internal.commonWaitFor
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.jvm.JvmField
import kotlin.jvm.JvmName
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmSynthetic
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * A Process.
 *
 * Currently, only Jvm supports writing to a spawned process's
 * standard input which can be obtained via the `Process.stdin`
 * extension function. Use [Builder.output] if the process input
 * is needed. (See Issue #77)
 *
 * @see [Builder]
 * @see [Current]
 * @see [OutputFeed.Handler]
 * @see [Blocking]
 * */
public abstract class Process internal constructor(

    /**
     * The command being executed.
     * */
    @JvmField
    public val command: String,

    /**
     * Optional arguments being executed.
     * */
    @JvmField
    public val args: List<String>,

    /**
     * The current working directory, or `null` if
     * one was not set via [Builder.chdir].
     * */
    @JvmField
    public val cwd: File?,

    /**
     * The environment that was passed.
     * */
    @JvmField
    public val environment: Map<String, String>,

    /**
     * The I/O configuration of the process.
     * */
    @JvmField
    public val stdio: Stdio.Config,

//    /**
//     * A stream to write data to the process's standard
//     * input, or `null` if [Stdio.Config.stdin] is not
//     * [Stdio.Pipe].
//     * */
//    @JvmField
//    TODO: Issue #77
    @get:JvmSynthetic
    internal val input: StdinStream?,

    /**
     * The [Signal] utilized to stop the process (if
     * not already stopped) when [destroy] is called.
     * */
    @JvmField
    public val destroySignal: Signal,

    init: SyntheticAccess
): OutputFeed.Handler(stdio) {

    /**
     * The "rough" start time mark of the [Process]. This is **actually**
     * a time mark for when [Process] was instantiated, which for all
     * platforms is immediately after the underlying platform's process
     * implementation was created.
     * */
    @JvmField
    public val startTime: ComparableTimeMark = TimeSource.Monotonic.markNow()

    /**
     * Information about the currently running process
     * */
    public object Current {

        @JvmStatic
        public fun environment(): Map<String, String> = PlatformBuilder.get().env.toImmutableMap()

        @JvmStatic
        public fun pid(): Int = PlatformBuilder.myPid()
    }

    /**
     * Destroys the [Process] by:
     *  - Sending it [destroySignal] (if it has not completed yet)
     *  - Closes all input/output streams
     *  - Stops all [OutputFeed] production (See [OutputFeed.Waiter])
     *    - **NOTE:** This may not be immediate if there is buffered
     *      data on `stdout` or `stderr`. The contents of what are
     *      left on the stream(s) will be drained which may stay live
     *      briefly **after** destruction.
     *
     * This **MUST** be called after you are done with the [Process]
     * to ensure resource closure occurs.
     *
     * @see [Signal]
     * @see [Builder.spawn]
     * @see [OutputFeed.Waiter]
     * @return this [Process] instance
     * */
    public abstract fun destroy(): Process

    /**
     * Returns the exit code for which the process
     * completed with.
     *
     * @throws [IllegalStateException] if the [Process] has
     *   not exited yet
     * */
    @Throws(IllegalStateException::class)
    public abstract fun exitCode(): Int

    /**
     * Checks if the [Process] is still running
     * */
    @get:JvmName("isAlive")
    public val isAlive: Boolean get() = try {
        exitCode()
        false
    } catch (_: IllegalStateException) {
        true
    }

    /**
     * Returns the [Process] identifier (PID).
     *
     * **NOTE:** On Jvm this can return -1 (Unknown) if:
     *  - Unable to retrieve via `java.lang.Process.toString` output
     *  - `java.lang.Process.pid` method unavailable (Java 8 or Android Runtime)
     *
     * **NOTE:** On Js this can return -1 (Unknown) if called
     * immediately after [Process] creation and the underlying
     * child process has not spawned yet.
     * */
    public abstract fun pid(): Int

    /**
     * Delays the current coroutine until [Process] completion.
     *
     * @see [io.matthewnelson.kmp.process.Blocking.waitFor]
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
     * @param [duration] the [Duration] to wait
     * @see [io.matthewnelson.kmp.process.Blocking.waitFor]
     * @return The [Process.exitCode], or null if [duration] is
     *   exceeded without [Process] completion.
     * */
    public suspend fun waitForAsync(
        duration: Duration,
        delay: suspend (duration: Duration) -> Unit,
    ): Int? = commonWaitFor(duration) { delay(it) }

    /**
     * Creates a new [Process].
     *
     * e.g. (Shell commands on a Unix system)
     *
     *     val out = Process.Builder("sh")
     *         .args("-c")
     *         .args("sleep 1; exit 5")
     *         .destroySignal(Signal.SIGKILL)
     *         .stdin(Stdio.Null)
     *         .output { timeoutMillis = 1_500 }
     *
     *     assertEquals(5, out.processInfo.exitCode)
     *
     * e.g. (Executable file)
     *
     *     val p = Process.Builder(myExecutableFile)
     *         .args("--some-flag")
     *         .args("someValue")
     *         .args("--another-flag", "anotherValue")
     *         .environment {
     *             remove("HOME")
     *             // ...
     *         }
     *         .stdin(Stdio.Null)
     *         .stdout(Stdio.File.of("logs/myExecutable.log", append = true))
     *         .stderr(Stdio.File.of("logs/myExecutable.err"))
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
         * absolute + normalized path to use for [command].
         * */
        public constructor(executable: File): this(executable.absoluteFile.normalize().path)

        private val args = mutableListOf<String>()
        private var chdir: File? = null
        private var destroy: Signal = Signal.SIGTERM
        private val platform = PlatformBuilder.get()
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
         * Set the [Signal] to use when [Process.destroy] is called.
         * */
        public fun destroySignal(
            signal: Signal,
        ): Builder = apply { destroy = signal }

        /**
         * Changes the working directory of the spawned process.
         *
         * [directory] must exist, otherwise the process will fail
         * to be spawned.
         *
         * **WARNING:** `iOS` does not support changing directories!
         *   Specifying this option will result in a failure to
         *   spawn a process.
         * */
        public fun chdir(
            directory: File?,
        ): Builder = apply { chdir = directory }

        /**
         * Set/overwrite an environment variable
         *
         * By default, the new [Process] will inherit all environment
         * variables from the current one.
         * */
        public fun environment(
            key: String,
            value: String,
        ): Builder = apply { platform.env[key] = value }

        /**
         * Modify the environment via lambda
         *
         * By default, the new [Process] will inherit all environment
         * variables from the current one.
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
         * Blocks the current thread until [Process] completion,
         * [Output.Options.Builder.timeoutMillis] is exceeded,
         * or [Output.Options.Builder.maxBuffer] is exceeded.
         *
         * Utilizes the default [Output.Options]
         *
         * For a long-running [Process], [spawn] should be utilized.
         *
         * @return [Output]
         * @throws [IOException] if [Process] creation failed
         * */
        @Throws(IOException::class)
        public fun output(): Output = output { /* defaults */ }

        /**
         * Blocks the current thread until [Process] completion,
         * [Output.Options.Builder.timeoutMillis] is exceeded,
         * or [Output.Options.Builder.maxBuffer] is exceeded.
         *
         * For a long-running [Process], [spawn] should be utilized.
         *
         * @param [block] lambda to configure [Output.Options]
         * @return [Output]
         * @see [Output.Options.Builder]
         * @throws [IOException] if [Process] creation failed
         * */
        @Throws(IOException::class)
        public fun output(
            block: Output.Options.Builder.() -> Unit,
        ): Output {
            if (command.isBlank()) throw IOException("command cannot be blank")

            val options = Output.Options.Builder.build(block)
            val stdio = stdio.build(outputOptions = options)

            val args = args.toImmutableList()
            val env = platform.env.toImmutableMap()

            return platform.output(command, args, chdir, env, stdio, options, destroy)
        }

        /**
         * Spawns the [Process]
         *
         * **NOTE:** [Process.destroy] **MUST** be called before de-referencing
         * the [Process] instance in order to close resources. This is best done
         * via try/finally, or utilizing the other [spawn] function which handles
         * it automatically for you.
         *
         * @throws [IOException] if [Process] creation failed
         * */
        @Throws(IOException::class)
        public fun spawn(): Process {
            if (command.isBlank()) throw IOException("command cannot be blank")

            val stdio = stdio.build(outputOptions = null)

            val args = args.toImmutableList()
            val env = platform.env.toImmutableMap()

            return platform.spawn(command, args, chdir, env, stdio, destroy)
        }

        /**
         * Spawns the [Process] and calls [destroy] upon [block] closure.
         *
         * @throws [IOException] if [Process] creation failed
         * */
        @Throws(IOException::class)
        @OptIn(ExperimentalContracts::class)
        public inline fun <T: Any?> spawn(
            block: (process: Process) -> T,
        ): T {
            contract {
                callsInPlace(block, InvocationKind.AT_MOST_ONCE)
            }

            val p = spawn()

            val result = try {
                block(p)
            } finally {
                p.destroy()
            }

            return result
        }
    }

    public final override fun toString(): String = buildString {
        val exitCode = try {
            exitCode().toString()
        } catch (_: IllegalStateException) {
            "not exited"
        }

        appendProcessInfo(
            "Process",
            pid(),
            exitCode,
            command,
            args,
            cwd,
            stdio,
            destroySignal
        )
    }

    protected companion object {

        @JvmSynthetic
        internal val INIT = SyntheticAccess.new()
    }

    init {
        check(init == INIT) { "Process cannot be extended. Use Process.Builder" }
    }
}
