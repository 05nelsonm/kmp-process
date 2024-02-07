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

package io.matthewnelson.process.internal

import io.matthewnelson.process.Process
import io.matthewnelson.process.ProcessException
import io.matthewnelson.process.Stdio
import io.matthewnelson.process.internal.PosixSpawnAttrs.Companion.posixSpawnAttrInit
import io.matthewnelson.process.internal.PosixSpawnFileActions.Companion.posixSpawnFileActionsInit
import kotlinx.cinterop.*
import platform.posix.pid_tVar

// unixMain
internal actual class PlatformBuilder internal actual constructor() {

    internal actual val env: MutableMap<String, String> by lazy {
        parentEnvironment()
    }

    @Throws(ProcessException::class)
    @OptIn(ExperimentalForeignApi::class)
    internal actual fun build(
        command: String,
        args: List<String>,
        env: Map<String, String>,
        stdio: Stdio.Config,
    ): Process = memScoped {

        // TODO: pipes Issue #2

        val fileActions = posixSpawnFileActionsInit()
        val attrs = posixSpawnAttrInit()

        val pid = alloc<pid_tVar>()

        // null terminated c-string array
        val argv = allocArray<CPointerVar<ByteVar>>(args.size + 2).apply {
            // First argument for posix_spawn's argv should be the executable name.
            // If command is the absolute path to the executable, take the final
            // path argument after last separator.
            this[0] = command.substringAfterLast('/').cstr.ptr

            var i = 1
            val iterator = args.iterator()
            while (iterator.hasNext()) {
                this[i++] = iterator.next().cstr.ptr
            }

            this[i] = null
        }

        // null terminated c-string array
        val envp = allocArray<CPointerVar<ByteVar>>(env.size + 1).apply {
            var i = 0
            val iterator = env.entries.iterator()
            while (iterator.hasNext()) {
                this[i++] = iterator.next().toString().cstr.ptr
            }

            this[i] = null
        }

        val result = if (command.startsWith('/')) {
            // Absolute path, utilize posix_spawn
            posixSpawn(command, pid.ptr, fileActions, attrs, argv, envp)
        } else {
            // relative path or program name, utilize posix_spawnp
            posixSpawnP(command, pid.ptr, fileActions, attrs, argv, envp)
        }

        // TODO: close things
        result.check()

        NativeProcess(pid.value, command, args, env, stdio)
    }
}
