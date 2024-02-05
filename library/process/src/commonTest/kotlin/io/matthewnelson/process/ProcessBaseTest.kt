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

import io.matthewnelson.kmp.file.SysTempDir
import io.matthewnelson.kmp.file.path
import io.matthewnelson.kmp.file.resolve
import io.matthewnelson.kmp.tor.resource.tor.TorResources
import io.matthewnelson.process.internal.commonWaitFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

abstract class ProcessBaseTest {

    private companion object {
        private val installer = TorResources(installationDir = SysTempDir.resolve("process"))
    }

    @Test
    fun givenExecutableFile_whenExecuteAsProcess_thenIsSuccessful() = runTest {
        val paths = installer.install()

        val p = Process.Builder(paths.tor.path)
            .arg("--DataDirectory")
            .arg(installer.installationDir.resolve("data").path)
            .arg("--CacheDirectory")
            .arg(installer.installationDir.resolve("cache").path)
            .arg("--GeoIPFile")
            .arg(paths.geoip.path)
            .arg("--GeoIPv6File")
            .arg(paths.geoip6.path)
            .arg("--DormantCanceledByStartup")
            .arg("1")
            .arg("--ControlPort")
            .arg("auto")
            .arg("--SocksPort")
            .arg("auto")
            .arg("--DisableNetwork")
            .arg("1")
            .arg("--RunAsDaemon")
            .arg("0")
            .environment("HOME", installer.installationDir.path)
            .start()

        println("CMD[${p.command}]")
        p.args.forEach { arg -> println("ARG[$arg]") }

        try {
            p.commonWaitFor(5.seconds, sleep = {
                withContext(Dispatchers.Default) { delay(it) }
            })
        } finally {
            p.sigterm()
        }
    }
}
