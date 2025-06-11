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
import io.matthewnelson.kmp.process.ProcessException.Companion.CTX_DESTROY
import io.matthewnelson.kmp.process.internal.PID
import io.matthewnelson.kmp.process.internal.PlatformBuilder
import io.matthewnelson.kmp.process.internal.SyntheticAccess
import io.matthewnelson.kmp.process.internal.appendProcessInfo
import io.matthewnelson.kmp.process.internal.commonWaitFor
import kotlinx.coroutines.delay
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
     * The current working directory, or `null` if one was
     * not set via [Process.Builder.changeDir](https://kmp-process.matthewnelson.io/library/process/io.matthewnelson.kmp.process/change-dir.html).
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

    /**
     * A stream to write data to the process's standard
     * input, or `null` if [Stdio.Config.stdin] is not
     * [Stdio.Pipe].
     * */
    @JvmField
    public val input: AsyncWriteStream?,

    /**
     * The [Signal] utilized to stop the process (if
     * not already stopped) when [destroy] is called.
     * */
    @JvmField
    public val destroySignal: Signal,

    private val handler: ProcessException.Handler,
    init: SyntheticAccess,
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
     * Information about the current process (i.e. the "parent" process).
     * */
    public object Current {

        /**
         * Retrieves the current process' environment
         * */
        @JvmStatic
        public fun environment(): Map<String, String> = PlatformBuilder.get().env.toImmutableMap()

        /**
         * Retrieves the current process' ID
         *
         * @throws [UnsupportedOperationException] if on Java9+ and module 'java.management' is not present.
         * */
        @JvmStatic
        public fun pid(): Int = PID.get()
    }

    /**
     * Destroys the [Process] by:
     *  - Sending it [destroySignal] (if it has not completed yet)
     *  - Closes all I/O streams
     *  - Stops all [OutputFeed] production (See [OutputFeed.Waiter])
     *    - **NOTE:** This may not be immediate if there is buffered
     *      data on `stdout` or `stderr`. The contents of what are
     *      left on the stream(s) will be drained which may stay live
     *      briefly **after** destruction.
     *
     * This **MUST** be called after you are done with the [Process]
     * to ensure resource closure occurs.
     *
     * **NOTE:** Depending on your [ProcessException.Handler], if an
     * error is produced (e.g. a file descriptor closure failure) and
     * you choose to throw it from [ProcessException.Handler.onException],
     * the caller of this function will receive the exception. You can
     * choose to ignore it (e.g. log only) by not throwing when the
     * [ProcessException.context] is equal to [CTX_DESTROY].
     *
     * @see [Signal]
     * @see [Builder.useSpawn]
     * @see [OutputFeed.Waiter]
     * @return this [Process] instance
     * */
    public fun destroy(): Process {
        try {
            destroyProtected(immediate = true)
        } catch (t: Throwable) {
            onError(t, context = CTX_DESTROY)
        }

        return this
    }

    /**
     * Returns the exit code for which the process
     * completed with.
     *
     * @throws [IllegalStateException] if the [Process] has
     *   not exited yet
     * */
    @Throws(IllegalStateException::class)
    public fun exitCode(): Int = exitCodeOrNull()
        ?: throw IllegalStateException("Process hasn't exited")

    /**
     * Returns the exit code for which the process
     * completed with, or `null` if it has not
     * exited yet.
     * */
    public abstract fun exitCodeOrNull(): Int?

    /**
     * Checks if the [Process] is still running
     * */
    @get:JvmName("isAlive")
    public val isAlive: Boolean get() = exitCodeOrNull() == null

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
     * **NOTE:** For Jvm & Android the `kotlinx.coroutines.core`
     * dependency is needed.
     *
     * **NOTE:** Care must be had when using Async APIs such that,
     * upon cancellation, [Process.destroy] is still called.
     *
     * See: [Blocking.waitFor](https://kmp-process.matthewnelson.io/library/process/io.matthewnelson.kmp.process/-blocking/wait-for.html)
     *
     * @return The [Process.exitCode]
     * */
    public suspend fun waitForAsync(): Int {
        var exitCode: Int? = null
        while (exitCode == null) {
            exitCode = waitForAsync(Duration.INFINITE)
        }
        return exitCode
    }

    /**
     * Delays the current coroutine for the specified [duration],
     * or until [Process.exitCode] is available (i.e. the
     * [Process] completed).
     *
     * **NOTE:** For Jvm & Android the `kotlinx.coroutines.core`
     * dependency is needed.
     *
     * **NOTE:** Care must be had when using Async APIs such that,
     * upon cancellation, [Process.destroy] is still called.
     *
     * See: [Blocking.waitFor](https://kmp-process.matthewnelson.io/library/process/io.matthewnelson.kmp.process/-blocking/wait-for.html)
     *
     * @param [duration] the [Duration] to wait
     * @return The [Process.exitCode], or null if [duration] is
     *   exceeded without [Process] completion.
     * */
    public suspend fun waitForAsync(
        duration: Duration,
    ): Int? = commonWaitFor(duration) { millis -> delay(millis) }

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

        private val _args = mutableListOf<String>()
        private var _chdir: File? = null
        private var _signal: Signal = Signal.SIGTERM
        private val _platform = PlatformBuilder.get()
        private var _handler: ProcessException.Handler? = null
        private val _stdio = Stdio.Config.Builder.get()

        /**
         * Add a single argument
         * */
        public fun args(
            arg: String,
        ): Builder = apply { _args.add(arg) }

        /**
         * Add multiple arguments
         * */
        public fun args(
            vararg args: String,
        ): Builder = apply { args.forEach { this._args.add(it) } }

        /**
         * Add multiple arguments
         * */
        public fun args(
            args: List<String>,
        ): Builder = apply { args.forEach { this._args.add(it) } }

        /**
         * Set the [Signal] to use when [Process.destroy] is called.
         * */
        public fun destroySignal(
            signal: Signal,
        ): Builder = apply { _signal = signal }

        /**
         * Set/overwrite an environment variable
         *
         * By default, the new [Process] will inherit all environment
         * variables from the current one.
         * */
        public fun environment(
            key: String,
            value: String,
        ): Builder = apply { _platform.env[key] = value }

        /**
         * Modify the environment via lambda
         *
         * By default, the new [Process] will inherit all environment
         * variables from the current one.
         * */
        public fun environment(
            block: MutableMap<String, String>.() -> Unit,
        ): Builder = apply { block(_platform.env) }

        /**
         * Set a [ProcessException.Handler] to manage internal
         * [Process] errors for spawned processes.
         *
         * By default, [ProcessException.Handler.IGNORE] is used
         * if one is not set.
         *
         * **NOTE:** [output] utilizes its own [ProcessException.Handler]
         * and does **not** use whatever may be set by [onError].
         *
         * @see [ProcessException]
         * */
        public fun onError(
            handler: ProcessException.Handler?,
        ): Builder = apply { this._handler = handler }

        /**
         * Modify the standard input source
         *
         * @see [Stdio]
         * */
        public fun stdin(
            source: Stdio,
        ): Builder = apply { _stdio.stdin = source }

        /**
         * Modify the standard output destination
         *
         * @see [Stdio]
         * */
        public fun stdout(
            destination: Stdio,
        ): Builder = apply { _stdio.stdout = destination }

        /**
         * Modify the standard error output destination
         *
         * @see [Stdio]
         * */
        public fun stderr(
            destination: Stdio,
        ): Builder = apply { _stdio.stderr = destination }

        /**
         * Blocks the current thread until [Process] completion,
         * [Output.Options.Builder.timeoutMillis] is exceeded,
         * or [Output.Options.Builder.maxBuffer] is exceeded.
         *
         * Utilizes the default [Output.Options]
         *
         * For a long-running [Process], [useSpawn] should be utilized.
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
         * For a long-running [Process], [useSpawn] should be utilized.
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
            val stdio = _stdio.build(outputOptions = options)

            val args = _args.toImmutableList()
            val env = _platform.env.toImmutableMap()

            return _platform.output(command, args, _chdir, env, stdio, options, _signal)
        }

        /**
         * Spawns the [Process]
         *
         * **NOTE:** [Process.destroy] **MUST** be called before de-referencing
         * the [Process] instance in order to close resources. This is best done
         * via try/finally, or the [useSpawn] function which handles it for you.
         *
         * @throws [IOException] if [Process] creation failed
         * */
        @Throws(IOException::class)
        public fun spawn(): Process {
            if (command.isBlank()) throw IOException("command cannot be blank")

            val stdio = _stdio.build(outputOptions = null)

            val args = _args.toImmutableList()
            val env = _platform.env.toImmutableMap()
            val handler = _handler ?: ProcessException.Handler.IGNORE

            return _platform.spawn(command, args, _chdir, env, stdio, _signal, handler)
        }

        /**
         * Spawns the [Process] and calls [Process.destroy] upon [block] closure.
         *
         * @throws [IOException] if [Process] creation failed
         * */
        @Throws(IOException::class)
        @OptIn(ExperimentalContracts::class)
        public inline fun <T: Any?> useSpawn(block: (process: Process) -> T): T {
            contract {
                callsInPlace(block, InvocationKind.AT_MOST_ONCE)
            }

            val p = spawn()

            val result = try {
                block(p)
            } catch (t: Throwable) {
                p.destroy()
                throw t
            }

            p.destroy()
            return result
        }

        /**
         * Spawns the [Process] and calls [Process.destroy] upon [block] closure.
         *
         * @throws [IOException] if [Process] creation failed
         * @suppress
         * */
        @Throws(IOException::class)
        @OptIn(ExperimentalContracts::class)
        @Deprecated(
            message = "Replaced with better nomenclature.",
            replaceWith = ReplaceWith("useSpawn(block)"),
        )
        public inline fun <T: Any?> spawn(block: (process: Process) -> T): T {
            contract {
                callsInPlace(block, InvocationKind.AT_MOST_ONCE)
            }

            return useSpawn(block)
        }

        /**
         * Changes the working directory of the spawned process.
         *
         * [directory] must exist, otherwise the process will fail
         * to be spawned.
         *
         * **WARNING:** `iOS` does not support changing directories!
         *   Specifying this option will result in a failure to
         *   spawn a process.
         * @suppress
         * */
        @Deprecated(
            message = "Not available for apple mobile targets resulting in spawn failure. Use changeDir.",
            replaceWith = ReplaceWith("this.changeDir(directory)", "io.matthewnelson.kmp.process.changeDir")
        )
        public fun chdir(
            directory: File?,
        ): Builder = apply { _chdir = directory }

        @JvmSynthetic
        internal fun platform(): PlatformBuilder = _platform
    }

    /** @suppress */
    public final override fun toString(): String = buildString {
        val exitCode = exitCodeOrNull()?.toString() ?: "not exited"

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

    /** @suppress */
    @Throws(Throwable::class)
    protected abstract fun destroyProtected(immediate: Boolean)

    /** @suppress */
    @Throws(Throwable::class)
    protected final override fun onError(t: Throwable, context: String) {
        if (handler == ProcessException.Handler.IGNORE) return

        val threw = try {
            handler.onException(ProcessException.of(context, t))
            null
        } catch (t: Throwable) {
            // Handler threw on us. Clean up shop.
            t
        }

        if (threw == null) return

        // Error was coming from somewhere other than destroy call.
        // Ensure we are destroyed to close everything down before
        // re-throwing.
        if (context != CTX_DESTROY) {
            try {
                // Because this error did not come from destroy,
                // Native must terminate Workers lazily because the
                // current thread may be the Worker (i.e. OutputFeed
                // threw exception). So, `immediate = false`.
                destroyProtected(immediate = false)
            } catch (t: Throwable) {
                threw.addSuppressed(t)
            }
        }

        throw threw
    }

    /** @suppress */
    protected companion object {

        @JvmSynthetic
        internal val INIT = SyntheticAccess.new()
    }

    init {
        check(init == INIT) { "Process cannot be extended. Use Process.Builder" }
    }
}
