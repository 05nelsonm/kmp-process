/*
 * Copyright (c) 2024 Matthew Nelson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
package io.matthewnelson.process.internal

import io.matthewnelson.process.Process
import io.matthewnelson.process.ProcessException
import kotlinx.cinterop.*
import platform.posix.WNOHANG
import platform.posix.WUNTRACED
import platform.posix.errno
import platform.posix.waitpid
import kotlin.concurrent.AtomicReference

internal class NativeProcess
@Throws(ProcessException::class)
internal constructor(
    internal val pid: Int,
    command: String,
    args: List<String>,
    env: Map<String, String>,
): Process(command, args, env, JavaLock.get()) {

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
                }
                else -> throw errnoToProcessException(errno)
            }
        }

        return _exitCode.value ?: throw ProcessException("Process hasn't exited")
    }
}
