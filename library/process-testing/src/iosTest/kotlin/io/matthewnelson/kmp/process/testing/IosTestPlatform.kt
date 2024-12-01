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
package io.matthewnelson.kmp.process.testing

import io.matthewnelson.kmp.file.toFile
import io.matthewnelson.kmp.tor.common.api.GeoipFiles
import io.matthewnelson.kmp.tor.common.api.ResourceLoader

internal actual val LOADER: ResourceLoader.Tor.Exec by lazy {
    IosTestLoader.getOrCreate() as ResourceLoader.Tor.Exec
}

// Need to mock executable files for ios simulator tests to use macOS
// compilations of tor.
private class IosTestLoader private constructor(): ResourceLoader.Tor.Exec() {
    companion object {
        fun getOrCreate(): Tor = getOrCreate(
            resourceDir = TorResourceBinder.RESOURCE_DIR,
            extract = {
                check(IOS_TOR_GEOIP.isNotBlank()) { "geoip file does not exist" }
                check(IOS_TOR_GEOIP6.isNotBlank()) { "geoip6 file does not exist" }

                val files = GeoipFiles(IOS_TOR_GEOIP.toFile(), IOS_TOR_GEOIP6.toFile())
                check(files.geoip.exists()) { "geoip file does not exist" }
                check(files.geoip6.exists()) { "geoip6 file does not exist" }

                files
            },
            extractTor = {
                check(IOS_TOR_EXECUTABLE.isNotBlank()) { "tor executable file does not exist" }

                val file = IOS_TOR_EXECUTABLE.toFile()
                check(file.exists()) { "tor executable file does not exist" }

                file
            },
            configureEnv = { /* no-op */ },
            toString = { "IosTestLoader" },
        )
    }
}
