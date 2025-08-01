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

import io.matthewnelson.kmp.tor.common.api.ResourceLoader
import io.matthewnelson.kmp.tor.resource.exec.tor.ResourceLoaderTorExec

internal actual val IsAppleSimulator: Boolean = false

internal actual val IsNodeJs: Boolean = false

internal actual val LOADER: ResourceLoader.Tor.Exec by lazy {
    ResourceLoaderTorExec.getOrCreate(TorResourceBinder.RESOURCE_DIR) as ResourceLoader.Tor.Exec
}
