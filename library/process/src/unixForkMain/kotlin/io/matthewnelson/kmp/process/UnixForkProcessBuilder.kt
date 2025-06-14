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
package io.matthewnelson.kmp.process

/**
 * By default, [posix_spawn](https://man7.org/linux/man-pages/man3/posix_spawn.3.html) and
 * [posix_spawnp](https://man7.org/linux/man-pages/man3/posix_spawn.3.html) are always preferred
 * when spawning processes, given that the necessary API availability is present (e.g. Android
 * Native requires a device API of 28+, or 34+ if changing the process' working directory via
 * [changeDir]). If API availability is **not** present, then an alternative implementation using
 * [fork](https://man7.org/linux/man-pages/man2/fork.2.html) and [execve](https://man7.org/linux/man-pages/man2/execve.2.html)
 * is fallen back to. This option allows you to skip over [posix_spawn](https://man7.org/linux/man-pages/man3/posix_spawn.3.html),
 * regardless of its API availability, and go directly to the alternative implementation using
 * [fork](https://man7.org/linux/man-pages/man2/fork.2.html) and [execve](https://man7.org/linux/man-pages/man2/execve.2.html).
 *
 * Default: `true`
 *
 * @param [use] if `true`, [posix_spawn](https://man7.org/linux/man-pages/man3/posix_spawn.3.html)
 *   or [posix_spawnp](https://man7.org/linux/man-pages/man3/posix_spawn.3.html) will be used, if
 *   available, with a fallback to using [fork](https://man7.org/linux/man-pages/man2/fork.2.html)
 *   and [execve](https://man7.org/linux/man-pages/man2/execve.2.html). If `false`, then the
 *   [fork](https://man7.org/linux/man-pages/man2/fork.2.html) and [execve](https://man7.org/linux/man-pages/man2/execve.2.html)
 *   implementation will **always** be used.
 * */
public fun Process.Builder.usePosixSpawn(
    use: Boolean,
): Process.Builder = apply {
    platform().usePosixSpawn = use
}
