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
package io.matthewnelson.kmp.process.test.api

import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.SysTempDir
import io.matthewnelson.kmp.file.path
import io.matthewnelson.kmp.file.resolve
import io.matthewnelson.kmp.process.Stdio
import io.matthewnelson.kmp.tor.common.api.ResourceLoader

internal val IsWindows = Stdio.Null.file.path == "NUL"

internal expect val IsAppleSimulator: Boolean

internal expect val IsNodeJs: Boolean

internal expect val LOADER: ResourceLoader.Tor.Exec

internal object TorResourceBinder: ResourceLoader.RuntimeBinder {

    internal val RESOURCE_DIR: File by lazy {

        // This is OK for Android Runtime, as only geoip files will be installed
        // to the Context.cacheDir/kmp_process. libtor.so is extracted to the
        // Context.applicationInfo.nativeLibraryDir automatically, so.
        SysTempDir.resolve("kmp_process")
    }
}
