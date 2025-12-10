/*
 * Copyright (c) 2025 Matthew Nelson
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

package io.matthewnelson.kmp.process.internal

import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.async.AsyncFs
import io.matthewnelson.kmp.file.wrapIOException
import io.matthewnelson.kmp.process.Output
import io.matthewnelson.kmp.process.Process
import io.matthewnelson.kmp.process.ProcessException
import io.matthewnelson.kmp.process.Signal
import io.matthewnelson.kmp.process.Stdio
import io.matthewnelson.kmp.process.internal.spawn.posixSpawn
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

internal actual class PlatformBuilder private actual constructor() {

    internal actual val env: MutableMap<String, String> by lazy { parentEnvironment() }

    @Throws(IOException::class)
    internal actual fun output(
        command: String,
        args: List<String>,
        chdir: File?,
        env: Map<String, String>,
        stdio: Stdio.Config,
        options: Output.Options,
        destroy: Signal,
    ): Output = blockingOutput(command, args, chdir, env, stdio, options, destroy)

    @Throws(CancellationException::class, IOException::class)
    internal actual suspend fun spawnAsync(
        fs: AsyncFs,
        command: String,
        args: List<String>,
        chdir: File?,
        env: Map<String, String>,
        stdio: Stdio.Config,
        destroy: Signal,
        handler: ProcessException.Handler,
        isOutput: Boolean,
    ): Process = withContext(fs.ctx) {
        spawn(
            command,
            args,
            chdir,
            env,
            stdio,
            destroy,
            handler,
        ).apply {
            if (isOutput) input?.configureForAsyncOutput()
        }
    }

    @Throws(IOException::class)
    internal actual fun spawn(
        command: String,
        args: List<String>,
        chdir: File?,
        env: Map<String, String>,
        stdio: Stdio.Config,
        destroy: Signal,
        handler: ProcessException.Handler,
    ): Process {
        val process = try {
            posixSpawn(
                command,
                args,
                chdir,
                env,
                stdio,
                destroy,
                handler,
            )
        } catch (e: UnsupportedOperationException) {
            throw e.wrapIOException()
        }

        if (process == null) {
            // Only reason it would be null was that file_actions or attrs structs
            // failed their initialization calls.
            throw IOException("Failed to initialize posix_spawn c structs")
        }

        return process
    }

    internal actual companion object {
        internal actual fun get(): PlatformBuilder = PlatformBuilder()
    }
}
