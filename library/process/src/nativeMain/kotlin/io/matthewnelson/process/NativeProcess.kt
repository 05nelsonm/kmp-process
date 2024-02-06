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
package io.matthewnelson.process

import io.matthewnelson.process.internal.errnoToProcessException
import kotlinx.cinterop.*
import platform.posix.*
import kotlin.concurrent.AtomicReference
import kotlin.time.Duration

internal class NativeProcess
@Throws(ProcessException::class)
internal constructor(
    private val pid: Int,
    command: String,
    args: List<String>,
    env: Map<String, String>,
): Process(command, args, env) {

    init {
        if (pid <= 0) {
            // TODO: Close pipes #Issue 2
            throw ProcessException("pid[$pid] must be greater than 0")
        }
    }

    private val _exitCode = AtomicReference<Int?>(null)

    @Throws(ProcessException::class)
    override fun exitCode(): Int {
        _exitCode.value?.let { return it }

        @OptIn(ExperimentalForeignApi::class)
        memScoped {
            val statLoc = alloc<IntVar>()

            when (waitpid(pid, statLoc.ptr, WNOHANG or WUNTRACED)) {
                0 -> { /* unavailable status */ }
                pid -> {
                    val code = statLoc.value shr 8 and 0x000000FF
                    _exitCode.compareAndSet(null, code)
                    // TODO: Close Pipes Issue #2
                }
                else -> throw errnoToProcessException(errno)
            }
        }

        return _exitCode.value ?: throw ProcessException("Process hasn't exited")
    }

    override fun waitFor(): Int {
        var exitCode: Int? = null
        while (exitCode == null) {
            exitCode = waitFor(Duration.INFINITE)
        }
        return exitCode
    }

    override fun sigterm(): Process {
        if (isAlive) kill(pid, SIGTERM)
        return this
    }

    override fun sigkill(): Process {
        if (isAlive) kill(pid, SIGKILL)
        return this
    }
}
