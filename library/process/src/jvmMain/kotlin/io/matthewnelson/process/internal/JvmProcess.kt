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
package io.matthewnelson.process.internal

import io.matthewnelson.process.Process
import io.matthewnelson.process.ProcessException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

internal class JvmProcess private constructor(
    command: String,
    args: List<String>,
    env: Map<String, String>,
    private val jProcess: java.lang.Process,
): Process(command, args, env, JavaLock.get()) {

    @Throws(ProcessException::class)
    override fun exitCode(): Int {
        val result: Int? = try {
            jProcess.exitValue()
        } catch (_: IllegalThreadStateException) {
            null
        }

        return result ?: throw ProcessException("Process hasn't exited")
    }

    override fun isAlive(): Boolean = jProcess.isAlive

    override fun getInputStream(): InputStream = jProcess.inputStream
    override fun getOutputStream(): OutputStream = jProcess.outputStream
    override fun getErrorStream(): InputStream = jProcess.errorStream

    @Throws(InterruptedException::class)
    override fun waitFor(): Int = jProcess.waitFor()
    @Throws(InterruptedException::class)
    override fun waitFor(timeout: Long, unit: TimeUnit?): Boolean = jProcess.waitFor(timeout, unit)

    @Throws(IllegalThreadStateException::class)
    override fun exitValue(): Int = jProcess.exitValue()

    override fun destroy() { jProcess.destroy() }
    override fun destroyForcibly(): java.lang.Process = jProcess.destroyForcibly()

    @Suppress("Since15")
    @Throws(UnsupportedOperationException::class)
    override fun toHandle(): ProcessHandle = jProcess.toHandle()

    @Suppress("Since15")
    @Throws(UnsupportedOperationException::class)
    override fun supportsNormalTermination(): Boolean = jProcess.supportsNormalTermination()

    internal companion object {

        @JvmSynthetic
        internal fun of(
            command: String,
            args: List<String>,
            env: Map<String, String>,
            delegate: java.lang.Process
        ): JvmProcess = JvmProcess(
            command,
            args,
            env,
            delegate,
        )
    }
}
