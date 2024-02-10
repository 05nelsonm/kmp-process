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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "LocalVariableName", "ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT")

package io.matthewnelson.kmp.process.internal

import io.matthewnelson.kmp.file.DelicateFileApi
import io.matthewnelson.kmp.file.path
import io.matthewnelson.kmp.file.toIOException
import io.matthewnelson.kmp.process.Process
import io.matthewnelson.kmp.process.Signal
import io.matthewnelson.kmp.process.Stdio

internal actual class PlatformBuilder actual constructor() {

    internal actual val env: MutableMap<String, String> by lazy {
        try {
            val env = js("require('process')").env
            val keys = js("Object").keys(env).unsafeCast<Array<String>>()

            val map = LinkedHashMap<String, String>(keys.size, 1.0F)

            keys.forEach { key ->
                map[key] = env[key] as String
            }

            map
        } catch (_: Throwable) {
            LinkedHashMap(1, 1.0F)
        }
    }

    // @Throws(IOException::class)
    internal actual fun build(
        command: String,
        args: List<String>,
        env: Map<String, String>,
        stdio: Stdio.Config,
        destroy: Signal
    ): Process {
        val jsEnv = js("{}")
        env.entries.forEach { entry ->
            jsEnv[entry.key] = entry.value
        }

        val descriptors = Array<Number?>(3) { null }

        val jsStdio = try {
            val jsStdin = stdio.stdin.toJsStdio(isStdin = true)
            descriptors[0] = jsStdin as? Number

            val jsStdout = stdio.stdout.toJsStdio(isStdin = false)
            descriptors[1] = jsStdout as? Number

            val jsStderr = stdio.stderr.toJsStdio(isStdin = false)
            descriptors[2] = jsStderr as? Number

            arrayOf(jsStdin, jsStdout, jsStderr)
        } catch (t: Throwable) {
            descriptors.forEach {
                if (it == null) return@forEach
                try {
                    fs_closeSync(it)
                } catch (_: Throwable) {}
            }

            @OptIn(DelicateFileApi::class)
            throw t.toIOException()
        }

        val options = js("{}")
        options["env"] = jsEnv
        options["stdio"] = jsStdio
        options["detached"] = false
        options["shell"] = false
        options["windowsVerbatimArguments"] = false
        options["windowsHide"] = true
        options["killSignal"] = destroy.name

        val jsProcess = try {
            child_process_spawn(command, args.toTypedArray(), options)
        } catch (t: Throwable) {
            descriptors.forEach {
                if (it == null) return@forEach
                try {
                    fs_closeSync(it)
                } catch (_: Throwable) {}
            }

            @OptIn(DelicateFileApi::class)
            throw t.toIOException()
        }

        return NodeJsProcess(
            jsProcess,
            command,
            args,
            env,
            stdio,
            destroy,
        )
    }

    private companion object {

        // @Throw(Exception::class)
        private fun Stdio.toJsStdio(
            isStdin: Boolean
        ): Any = when (this) {
            is Stdio.Inherit -> "inherit"
            is Stdio.Pipe -> "pipe"
            is Stdio.File -> {
                when {
                    file == STDIO_NULL -> "ignore"
                    isStdin -> fs_openSync(file.path, "r")
                    append -> fs_openSync(file.path, "a")
                    else -> fs_openSync(file.path, "w")
                }
            }
        }
    }
}
