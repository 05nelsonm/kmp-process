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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "KotlinRedundantDiagnosticSuppress")

package io.matthewnelson.kmp.process.internal

import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.process.Output
import io.matthewnelson.kmp.process.Process
import io.matthewnelson.kmp.process.Signal
import io.matthewnelson.kmp.process.Stdio
import io.matthewnelson.kmp.process.internal.stdio.StdioHandle.Companion.openHandle
import platform.posix.getpid

@Suppress("NOTHING_TO_INLINE")
internal expect inline fun PlatformBuilder.parentEnvironment(): MutableMap<String, String>

// nativeMain
internal actual class PlatformBuilder private actual constructor() {

    internal actual val env: MutableMap<String, String> by lazy {
        parentEnvironment()
    }

    @Throws(IOException::class)
    internal actual fun output(
        command: String,
        args: List<String>,
        env: Map<String, String>,
        stdio: Stdio.Config,
        options: Output.Options,
        destroy: Signal,
    ): Output = blockingOutput(command, args, env, stdio, options, destroy)

    @Throws(IOException::class)
    internal actual fun spawn(
        command: String,
        args: List<String>,
        env: Map<String, String>,
        stdio: Stdio.Config,
        destroy: Signal,
    ): Process {

        val handle = stdio.openHandle()

        try {
            return posixSpawn(command, args, env, handle, destroy)
        } catch (_: UnsupportedOperationException) {
            /* ignore and try fork/exec */
        } catch (e: IOException) {
            handle.close()
            throw e
        }

        try {
            return forkExec(command, args, env, handle, destroy)
        } catch (e: IOException) {
            handle.close()
            throw e
        }
    }

    internal actual companion object {

        internal actual fun get(): PlatformBuilder = PlatformBuilder()

        internal actual fun myPid(): Int = getpid()
    }
}
