package io.matthewnelson.process

import io.matthewnelson.kmp.file.SysTempDir
import io.matthewnelson.kmp.file.path
import io.matthewnelson.kmp.file.resolve
import io.matthewnelson.kmp.tor.resource.tor.TorResources
import io.matthewnelson.process.internal.NativeProcess
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import platform.posix.SIGTERM
import platform.posix.kill
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ProcessUnitTest {

    private companion object {
        private val installer = TorResources(installationDir = SysTempDir.resolve("process"))
    }

    @Test
    fun givenExecutableFile_whenExecuteAsProcess_thenIsSuccessful() = runTest(timeout = 25.seconds) {
        val paths = installer.install()

        // TODO: iOS error
        //  dyld[33687]: DYLD_ROOT_PATH not set for simulator program
        //  .
        //  Simulator targets run the macos tor binaries just fine. May
        //  need to reconfigure kmp-tor-resource to use those instead of
        //  compiling the ios simulator targets?
        //  .
        //  https://github.com/05nelsonm/kmp-tor-resource/issues/33
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
            .start() as NativeProcess

        currentCoroutineContext()[Job]?.invokeOnCompletion {
            p.pid?.let { pid -> kill(pid, SIGTERM)  }
        }

        println("PID[${p.pid}]")
        println("CMD[${p.command}]")
        p.args.forEach { arg -> println("ARG[$arg]") }

        withContext(Dispatchers.Default) {
            delay(300.milliseconds)
        }

        if (p.pid != null) {
            withContext(Dispatchers.Default) {
                delay(5.seconds)
            }
        }
    }
}
