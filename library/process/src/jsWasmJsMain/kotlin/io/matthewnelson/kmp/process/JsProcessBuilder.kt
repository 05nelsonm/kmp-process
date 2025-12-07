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

/**
 * DEFAULT: `false`
 *
 * Configures the `detached` option for `spawn`.
 *
 * **NOTE:** This setting is ignored when using [Process.Builder.createOutput]
 * and [Process.Builder.createOutputAsync].
 *
 * **NOTE:** `unref` will be called immediately on the underlying child process.
 * It is upon API consumers to ensure an appropriate [Stdio] configuration is had
 * for their detached [Process], as detailed in the documentation linked below.
 *
 * [docs#spawn](https://nodejs.org/api/child_process.html#optionsdetached)
 * */
public fun Process.Builder.detached(
    enable: Boolean,
): Process.Builder = apply {
    _platform.detached = enable
}

/**
 * DEFAULT: `false`
 *
 * Configures the `shell` option for `spawn` and `spawnSync`
 *
 * [docs#spawn](https://nodejs.org/api/child_process.html#child_processspawncommand-args-options)
 * [docs#spawnSync](https://nodejs.org/api/child_process.html#child_processspawnsynccommand-args-options)
 * */
public fun Process.Builder.shell(
    enable: Boolean,
): Process.Builder = apply {
    _platform.shell = enable
}

/**
 * DEFAULT: `false`
 *
 * Configures the `shell` option for `spawn` and `spawnSync`.
 *
 * [docs#spawn](https://nodejs.org/api/child_process.html#child_processspawncommand-args-options)
 * [docs#spawnSync](https://nodejs.org/api/child_process.html#child_processspawnsynccommand-args-options)
 * */
public fun Process.Builder.shell(
    shell: String,
): Process.Builder = apply {
    _platform.shell = shell
}

/**
 * DEFAULT: `false`
 *
 * Configures the `windowsVerbatimArguments` option for `spawn` and `spawnSync`
 *
 * [docs#spawn](https://nodejs.org/api/child_process.html#child_processspawncommand-args-options)
 * [docs#spawnSync](https://nodejs.org/api/child_process.html#child_processspawnsynccommand-args-options)
 * */
public fun Process.Builder.windowsVerbatimArguments(
    enable: Boolean,
): Process.Builder = apply {
    _platform.windowsVerbatimArguments = enable
}

/**
 * DEFAULT: `true`
 *
 * Configures the `windowsHide` option for `spawn` and `spawnSync`
 *
 * [docs#spawn](https://nodejs.org/api/child_process.html#child_processspawncommand-args-options)
 * [docs#spawnSync](https://nodejs.org/api/child_process.html#child_processspawnsynccommand-args-options)
 * */
public fun Process.Builder.windowsHide(
    enable: Boolean,
): Process.Builder = apply {
    _platform.windowsHide = enable
}
