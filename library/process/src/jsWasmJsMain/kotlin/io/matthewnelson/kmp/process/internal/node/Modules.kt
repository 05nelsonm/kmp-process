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
package io.matthewnelson.kmp.process.internal.node

import io.matthewnelson.kmp.file.SysFsInfo

@get:Throws(UnsupportedOperationException::class)
internal val node_child_process: ModuleChildProcess by lazy {
    requireNodeJs { "child_process" }
    node_events
    node_stream
    nodeModuleChildProcess()
}

@get:Throws(UnsupportedOperationException::class)
internal val node_events: ModuleEvents by lazy {
    requireNodeJs { "events" }
    nodeModuleEvents()
}

@get:Throws(UnsupportedOperationException::class)
internal val node_fs: ModuleFs by lazy {
    requireNodeJs { "fs" }
    nodeModuleFs()
}

@get:Throws(UnsupportedOperationException::class)
internal val node_os: ModuleOs by lazy {
    requireNodeJs { "os" }
    nodeModuleOs()
}

@get:Throws(UnsupportedOperationException::class)
internal val node_process: ModuleProcess by lazy {
    requireNodeJs { "process" }
    nodeModuleProcess()
}

@get:Throws(UnsupportedOperationException::class)
internal val node_stream: ModuleStream by lazy {
    requireNodeJs { "stream" }
    nodeModuleStream()
}

internal const val CODE_MODULE_CHILD_PROCESS: String = "eval('require')('child_process')"
internal const val CODE_MODULE_EVENTS: String = "eval('require')('events')"
internal const val CODE_MODULE_FS: String = "eval('require')('fs')"
internal const val CODE_MODULE_OS: String = "eval('require')('os')"
internal const val CODE_MODULE_PROCESS: String = "eval('require')('process')"
internal const val CODE_MODULE_STREAM: String = "eval('require')('stream')"

internal expect fun nodeModuleChildProcess(): ModuleChildProcess
internal expect fun nodeModuleEvents(): ModuleEvents
internal expect fun nodeModuleFs(): ModuleFs
internal expect fun nodeModuleOs(): ModuleOs
internal expect fun nodeModuleProcess(): ModuleProcess
internal expect fun nodeModuleStream(): ModuleStream

@Suppress("NOTHING_TO_INLINE")
@Throws(UnsupportedOperationException::class)
private inline fun requireNodeJs(module: () -> String) {
    if (SysFsInfo.name == "FsJsNode") return
    val m = module()
    throw UnsupportedOperationException("Failed to load module[$m] >> FileSystem is not FsJsNode")
}
