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
@file:Suppress("LocalVariableName", "PropertyName", "RedundantCompanionReference", "RedundantVisibilityModifier")

package io.matthewnelson.kmp.process

import io.matthewnelson.immutable.collections.toImmutableList
import io.matthewnelson.immutable.collections.toImmutableMap
import io.matthewnelson.kmp.file.*
import io.matthewnelson.kmp.file.async.AsyncFs
import io.matthewnelson.kmp.process.ProcessException.Companion.CTX_DESTROY
import io.matthewnelson.kmp.process.internal.PID
import io.matthewnelson.kmp.process.internal.PlatformBuilder
import io.matthewnelson.kmp.process.internal.appendProcessInfo
import io.matthewnelson.kmp.process.internal.checkFileName
import io.matthewnelson.kmp.process.internal.commonOutput
import io.matthewnelson.kmp.process.internal.commonWaitFor
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.DeprecationLevel
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
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
    init: Any,
): OutputFeed.Handler(stdio), Closeable {

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
         * @throws [UnsupportedOperationException] when:
         *   - Java9+ and module 'java.management' is not present
         *   - Js/WasmJs Browser
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
     * @return this [Process] instance
     *
     * @see [Signal]
     * @see [OutputFeed.Waiter]
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
     * Destroys the [Process].
     *
     * @see [use]
     * @see [destroy]
     * */
    public final override fun close() { destroy() }

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
     *         // Synchronous API (All platforms)
     *         .createOutput { timeoutMillis = 1_500 }
     *
     *     assertEquals(5, out.processInfo.exitCode)
     *
     * e.g. (Executable file)
     *
     *     val b = Process.Builder(myExecutableFile)
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
     *
     *     // Synchronous API (Jvm/Native)
     *     b.createProcess().use { p ->
     *         // ...
     *     }
     *
     *     // Asynchronous API (All platforms)
     *     myScope.launch {
     *         b.createProcessAsync().use { p ->
     *             // ...
     *         }
     *     }
     * */
    public class Builder(

        /**
         * The command to run, such as a program on `PATH`, or a file path (relative or
         * absolute) to a program.
         * */
        @JvmField
        public val command: String
    ): Blocking.Builder() {

        /**
         * An alternate constructor for an executable [File]. Will take the absolute & normalized
         * path to use for [command].
         *
         * @throws [IOException] If [absoluteFile2] has to reference the filesystem to construct
         *   an absolute path and fails due to a filesystem security exception.
         * @throws [UnsupportedOperationException] on Js/WasmJs Browser
         * */
        public constructor(executable: File): this(executable.absoluteFile2().normalize().path)

        private val _args = mutableListOf<String>()
        private var _async: AsyncFs = AsyncFs.Default
        private var _chdir: File? = null
        private var _signal: Signal = Signal.SIGTERM
        @get:JvmSynthetic
        internal val _platform = PlatformBuilder.get()
        private var _handler: ProcessException.Handler? = null
        private val _stdio = Stdio.Config.Builder.get()

        /**
         * Add a single argument.
         * */
        public fun args(arg: String): Builder = apply { _args.add(arg) }

        /**
         * Add multiple arguments.
         * */
        public fun args(vararg args: String): Builder = apply { args.forEach { _args.add(it) } }

        /**
         * Add multiple arguments.
         * */
        public fun args(args: List<String>): Builder = apply { args.forEach { _args.add(it) } }

        /**
         * DEFAULT: `Dispatchers.IO` (Jvm/Native), `Dispatchers.Default` (Js/WasmJs)
         *
         * Configure the [CoroutineContext] to utilize for [createProcessAsync]. If `null`,
         * the `DEFAULT` will be used.
         * */
        public fun async(context: CoroutineContext?): Builder = apply { _async = AsyncFs.of(context) }

        /**
         * DEFAULT: [Signal.SIGTERM]
         *
         * Configure the [Signal] to use when [Process.destroy] is called.
         * */
        public fun destroySignal(signal: Signal): Builder = apply { _signal = signal }

        /**
         * Configure/overwrite an environment variable
         *
         * By default, the new [Process] will inherit all environment
         * variables from the current one (its parent process).
         * */
        public fun environment(key: String, value: String): Builder = apply { _platform.env[key] = value }

        /**
         * Configure the process' environment via lambda
         *
         * By default, the new [Process] will inherit all environment
         * variables from the current one (its parent process).
         * */
        public fun environment(block: MutableMap<String, String>.() -> Unit): Builder = apply { block(_platform.env) }

        /**
         * DEFAULT: `null` (i.e. use [ProcessException.Handler.IGNORE])
         *
         * Configure a [ProcessException.Handler] to manage internal-ish
         * [Process] errors for spawned processes.
         *
         * **NOTE:** [createOutput] utilizes its own [ProcessException.Handler]
         * and does **not** use whatever may be set by [onError].
         *
         * @see [ProcessException]
         * */
        public fun onError(handler: ProcessException.Handler?): Builder = apply { _handler = handler }

        /**
         * DEFAULT: [Stdio.Pipe]
         *
         * Configure the standard input source
         *
         * @see [Stdio]
         * */
        public fun stdin(source: Stdio): Builder = apply { _stdio.stdin = source }

        /**
         * DEFAULT: [Stdio.Pipe]
         *
         * Configure the standard output destination
         *
         * @see [Stdio]
         * */
        public fun stdout(destination: Stdio): Builder = apply { _stdio.stdout = destination }

        /**
         * DEFAULT: [Stdio.Pipe]
         *
         * Configure the standard error destination
         *
         * @see [Stdio]
         * */
        public fun stderr(destination: Stdio): Builder = apply { _stdio.stderr = destination }

        /**
         * Create the [Process] asynchronously using the configured [async] context.
         *
         * **NOTE:** [Process.destroy] **MUST** be called before de-referencing the
         * process instance in order to close resources. This is best done via a
         * try/finally block, or utilization of the [Closeable.use] function which
         * handles it for you.
         *
         * See: [Blocking.Builder.createProcess](https://kmp-process.matthewnelson.io/library/process/io.matthewnelson.kmp.process/-blocking/-builder/create-process.html)
         * @see [use]
         * @see [async]
         * @see [createOutput]
         * @see [createOutputAsync]
         *
         * @return The [Process]
         *
         * @throws [CancellationException]
         * @throws [IOException] If [Process] creation failed.
         * @throws [UnsupportedOperationException] On Js/WasmJs Browser.
         * */
        @Throws(CancellationException::class, IOException::class)
        public suspend fun createProcessAsync(): Process {
            val fs = _async

            return platformSpawn(
                _build = { options -> buildAsync(fs, options) },
                _spawn = { command, args, chdir, env, stdio, signal, handler ->
                    spawnAsync(fs, command, args, chdir, env, stdio, signal, handler)
                },
            )
        }

        /**
         * Blocks the current thread until [Process] completion,
         * [Output.Options.Builder.timeoutMillis] is exceeded,
         * or [Output.Options.Builder.maxBuffer] is exceeded.
         *
         * Utilizes the default [Output.Options].
         *
         * **NOTE:** For a long-running [Process], `createProcess`
         * or [createProcessAsync] + [use] should be preferred.
         *
         * @return The [Output]
         *
         * @see [createOutputAsync]
         *
         * @throws [IOException] If [Process] creation failed.
         * @throws [UnsupportedOperationException] On Js/WasmJs Browser.
         * */
        @Throws(IOException::class)
        public fun createOutput(): Output = createOutput(b = Output.Options.Builder.get())

        /**
         * Blocks the current thread until [Process] completion,
         * [Output.Options.Builder.timeoutMillis] is exceeded,
         * or [Output.Options.Builder.maxBuffer] is exceeded.
         *
         * **NOTE:** For a long-running [Process], `createProcess`
         * or [createProcessAsync] + [use] should be preferred.
         *
         * @param [block] lambda to configure [Output.Options]
         *
         * @return The [Output]
         *
         * @see [Output.Options.Builder]
         * @see [createOutputAsync]
         *
         * @throws [IOException] If [Process] creation failed.
         * @throws [UnsupportedOperationException] on Js/WasmJs Browser.
         * */
        @Throws(IOException::class)
        @OptIn(ExperimentalContracts::class)
        public inline fun createOutput(block: Output.Options.Builder.() -> Unit): Output {
            contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
            val b = Output.Options.Builder.get().apply(block)
            return createOutput(b)
        }

        /**
         * Creates the [Process] asynchronously using the configured [async] context
         * and suspends until its completion, [Output.Options.Builder.timeoutMillis]
         * is exceeded, or [Output.Options.Builder.maxBuffer] is exceeded.
         *
         * Utilizes the default [Output.Options].
         *
         * **NOTE:** For a long-running [Process], [createProcessAsync] or
         * `createProcess` + [use] should be preferred.
         *
         * @return The [Output]
         *
         * @see [async]
         * @see [createOutput]
         *
         * @throws [CancellationException]
         * @throws [IOException] If [Process] creation failed.
         * @throws [UnsupportedOperationException] On Js/WasmJs Browser.
         * */
        @Throws(CancellationException::class, IOException::class)
        public suspend fun createOutputAsync(): Output = createOutputAsync(b = Output.Options.Builder.get())

        /**
         * Creates the [Process] asynchronously using the configured [async] context
         * and suspends until its completion, [Output.Options.Builder.timeoutMillis]
         * is exceeded, or [Output.Options.Builder.maxBuffer] is exceeded.
         *
         * **NOTE:** For a long-running [Process], [createProcessAsync] or
         * `createProcess` + [use] should be preferred.
         *
         * @param [block] lambda to configure [Output.Options]
         *
         * @return The [Output]
         *
         * @see [async]
         * @see [createOutput]
         * @see [Output.Options.Builder]
         *
         * @throws [CancellationException]
         * @throws [IOException] If [Process] creation failed.
         * @throws [UnsupportedOperationException] on Js/WasmJs Browser.
         * */
        @OptIn(ExperimentalContracts::class)
        @Throws(CancellationException::class, IOException::class)
        public suspend inline fun createOutputAsync(block: Output.Options.Builder.() -> Unit): Output {
            contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
            val b = Output.Options.Builder.get().apply(block)
            return createOutputAsync(b)
        }

        @JvmSynthetic
        @PublishedApi
        @Throws(IOException::class)
        internal fun createOutput(b: Output.Options.Builder): Output {
            return platformOutput(
                b = b,
                _build = Stdio.Config.Builder::build,
                _output = PlatformBuilder::output,
            )
        }

        @JvmSynthetic
        @PublishedApi
        @Throws(CancellationException::class, IOException::class)
        internal suspend fun createOutputAsync(b: Output.Options.Builder): Output {
            val fs = _async

            return platformOutput(
                b = b,
                _build = { options -> buildAsync(fs, options) },
                _output = { command, args, chdir, env, stdio, options, destroy ->
                    commonOutput(
                        command,
                        args,
                        chdir,
                        env,
                        stdio,
                        options,
                        destroy,
                        _spawn = { command2, args2, chdir2, env2, stdio2, destroy2, handler ->
                            spawnAsync(fs, command2, args2, chdir2, env2, stdio2, destroy2, handler, isOutput = true)
                        },

                        // Jvm/Native uses Dispatcher.IO under the hood for these calls. If
                        // AsyncFs.ctx is single-threaded, the underlying blocking calls could
                        // make for a bad time.
                        _close = { closeAsync() },
                        _write = { buf, offset, len -> writeAsync(buf, offset, len) },

                        _sleep = { duration -> delay(duration) },
                        // Using withContext b/c test coroutine dispatcher will advance time
                        // which we do NOT want here, as output may get missed.
                        _sleepWithContext = { duration -> withContext(AsyncFs.Default.ctx) { delay(duration) } },

                        _awaitStop = { awaitStopAsync() },
                        _waitFor = { waitForAsync() },
                    )
                },
            )
        }

        /** @suppress */
        @Throws(IOException::class)
        protected override fun createProcessProtected(): Process {
            // For Blocking.Builder
            return platformSpawn(
                _build = Stdio.Config.Builder::build,
                _spawn = PlatformBuilder::spawn,
            )
        }

        @Throws(IOException::class)
        @OptIn(ExperimentalContracts::class)
        private inline fun platformSpawn(
            _build: Stdio.Config.Builder.(Output.Options?) -> Stdio.Config,
            _spawn: PlatformBuilder.(String, List<String>, File?, Map<String, String>, Stdio.Config, Signal, ProcessException.Handler) -> Process,
        ): Process {
            contract {
                callsInPlace(_build, InvocationKind.AT_MOST_ONCE)
                callsInPlace(_spawn, InvocationKind.AT_MOST_ONCE)
            }
            command.checkFileName { "command" }
            val stdio = _stdio._build(/* outputOptions = */ null)

            val args = _args.toImmutableList()
            val env = _platform.env.toImmutableMap()
            val handler = _handler ?: ProcessException.Handler.IGNORE

            return _platform._spawn(command, args, _chdir, env, stdio, _signal, handler)
        }

        @Throws(IOException::class)
        @OptIn(ExperimentalContracts::class)
        private inline fun platformOutput(
            b: Output.Options.Builder,
            _build: Stdio.Config.Builder.(Output.Options?) -> Stdio.Config,
            _output: PlatformBuilder.(String, List<String>, File?, Map<String, String>, Stdio.Config, Output.Options, Signal) -> Output,
        ): Output {
            contract {
                callsInPlace(_build, InvocationKind.AT_MOST_ONCE)
                callsInPlace(_output, InvocationKind.AT_MOST_ONCE)
            }
            command.checkFileName { "command" }
            val options = b.build()
            val stdio = _stdio._build(/* outputOptions = */ options)

            val args = _args.toImmutableList()
            val env = _platform.env.toImmutableMap()

            return _platform._output(command, args, _chdir, env, stdio, options, _signal)
        }

        /**
         * DEPRECATED since `0.5.0`
         * @suppress
         * */
        @Throws(IOException::class)
        @Deprecated(
            message = "Renamed to createOutput",
            replaceWith = ReplaceWith("createOutput()"),
            level = DeprecationLevel.WARNING,
        )
        public fun output(): Output = createOutput()

        /**
         * DEPRECATED since `0.5.0`
         * @suppress
         * */
        @Throws(IOException::class)
        @Deprecated(
            message = "Renamed to createOutput and inlined",
            replaceWith = ReplaceWith("createOutput(block)"),
            level = DeprecationLevel.WARNING,
        )
        public fun output(
            block: Output.Options.Builder.() -> Unit,
        ): Output = createOutput(block)

        /**
         * DEPRECATED since `0.5.0`
         * See: https://github.com/05nelsonm/kmp-process/issues/198
         * @suppress
         * */
        @Throws(IOException::class)
        @Deprecated(
            message = "Replaced with createProcess (Jvm/Native) and createProcessAsync (All platforms)",
            level = DeprecationLevel.WARNING,
        )
        public fun spawn(): Process = createProcessProtected()

        /**
         * DEPRECATED since `0.5.0`
         * See: https://github.com/05nelsonm/kmp-process/issues/198
         * @suppress
         * */
        @Throws(IOException::class)
        @OptIn(ExperimentalContracts::class)
        @Deprecated(
            message = "Replaced with Closeable.use functionality. Use Builder.{createProcess/createProcessAsync}.use { ... } instead.",
            level = DeprecationLevel.WARNING,
        )
        public inline fun <T: Any?> useSpawn(block: (process: Process) -> T): T {
            contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
            @Suppress("DEPRECATION")
            return spawn().use(block)
        }

        /**
         * DEPRECATED since `0.3.0`
         * @suppress
         * */
        @Throws(IOException::class)
        @OptIn(ExperimentalContracts::class)
        @Deprecated(
            message = "Replaced with Closeable.use functionality",
            replaceWith = ReplaceWith("useSpawn(block)"),
            level = DeprecationLevel.WARNING,
        )
        public inline fun <T: Any?> spawn(block: (process: Process) -> T): T {
            contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
            @Suppress("DEPRECATION")
            return useSpawn(block)
        }

        /**
         * DEPRECATED since `0.1.1`
         * @suppress
         * */
        @Deprecated(
            message = "Not available for iOS/tvOS/watchOS targets. Use Builder.changeDir",
            replaceWith = ReplaceWith("this.changeDir(directory)", "io.matthewnelson.kmp.process.changeDir"),
            level = DeprecationLevel.ERROR,
        )
        public fun chdir(
            directory: File?,
        ): Builder = apply { _chdir = directory }
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

        @get:JvmSynthetic
        internal val INIT = Any()
    }

    init {
        check(init == INIT) { "Process cannot be extended. Use Process.Builder" }
    }
}
