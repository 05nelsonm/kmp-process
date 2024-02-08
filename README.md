# kmp-process
[![badge-license]][url-license]
[![badge-latest-release]][url-latest-release]

[![badge-kotlin]][url-kotlin]
[![badge-kmp-file]][url-kmp-file]
[![badge-immutable]][url-immutable]

![badge-platform-android]
![badge-platform-jvm]
![badge-platform-linux]
![badge-platform-ios]
![badge-platform-macos]
![badge-support-apple-silicon]
![badge-support-linux-arm]

`Process` implementation for Kotlin Multiplatform

## Info

|         | Process Creation Method Used                                     |
|---------|------------------------------------------------------------------|
| `Jvm`   | `java.lang.ProcessBuilder`                                       |
| `Linux` | [posix_spawn][url-posix-spawn] & [posix_spawnp][url-posix-spawn] |
| `macOS` | [posix_spawn][url-posix-spawn] & [posix_spawnp][url-posix-spawn] |
| `iOS`   | [posix_spawn][url-posix-spawn] & [posix_spawnp][url-posix-spawn] |

## Usage

```kotlin
val expected = 5

val p = Process.Builder("sh")
    .args("-c")
    .args("""
        sleep 0.25
        echo "HOME: $${"HOME"}"
        exit $expected
    """.trimIndent())
    .withEnvironment {
        remove("HOME")
        // ...
    }
    .environment("HOME", myAppDir.absolutePath)
    .stdout(Stdio.Inherit)
    .spawn()

println("IS_ALIVE: ${p.isAlive}")
assertEquals(expected, p.waitFor(500.milliseconds))
```

```kotlin
val p = Process.Builder(myExecutable)
    .args("--some-flag")
    .args("someValue")
    .stdin(Stdio.Null)
    .stdout(Stdio.File.of("myExecutable.log", append = true))
    .stderr(Stdio.File.of("myExecutable.err"))
    .spawn()

// Jvm/Native block for specified duration
p.waitFor(5.seconds).let { code ->
    println("EXIT_CODE: ${code ?: "NULL"}")
}

// Jvm/Js/Native suspend coroutine for specified duration
p.waitForAsync(5.seconds, ::delay).let { code ->
    println("EXIT_CODE: ${code ?: "NULL"}")
}

try {
    println("EXIT_CODE: ${p.exitCode()}")
} catch (_: IllegalStateException) {}

// Send process `SIGTERM` signal
// Like calling `java.lang.Process.destroy()`
p.sigterm()

// Alternatively, send process `SIGKILL` signal.
// Like calling `java.lang.Process.destroyForcibly()`
p.sigkill()
```

<!-- TAG_VERSION -->
[badge-latest-release]: https://img.shields.io/badge/latest--release-Unreleased-blue.svg?style=flat
[badge-license]: https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat

<!-- TAG_DEPENDENCIES -->
[badge-immutable]: https://img.shields.io/badge/immutable-0.1.0-blue.svg?style=flat
[badge-kmp-file]: https://img.shields.io/badge/kmp--file-0.1.0--alpha06-blue.svg?style=flat
[badge-kotlin]: https://img.shields.io/badge/kotlin-1.9.21-blue.svg?logo=kotlin

<!-- TAG_PLATFORMS -->
[badge-platform-android]: http://img.shields.io/badge/-android-6EDB8D.svg?style=flat
[badge-platform-jvm]: http://img.shields.io/badge/-jvm-DB413D.svg?style=flat
[badge-platform-js]: http://img.shields.io/badge/-js-F8DB5D.svg?style=flat
[badge-platform-js-node]: https://img.shields.io/badge/-nodejs-68a063.svg?style=flat
[badge-platform-linux]: http://img.shields.io/badge/-linux-2D3F6C.svg?style=flat
[badge-platform-macos]: http://img.shields.io/badge/-macos-111111.svg?style=flat
[badge-platform-ios]: http://img.shields.io/badge/-ios-CDCDCD.svg?style=flat
[badge-platform-tvos]: http://img.shields.io/badge/-tvos-808080.svg?style=flat
[badge-platform-watchos]: http://img.shields.io/badge/-watchos-C0C0C0.svg?style=flat
[badge-platform-wasm]: https://img.shields.io/badge/-wasm-624FE8.svg?style=flat
[badge-platform-windows]: http://img.shields.io/badge/-windows-4D76CD.svg?style=flat
[badge-support-android-native]: http://img.shields.io/badge/support-[AndroidNative]-6EDB8D.svg?style=flat
[badge-support-apple-silicon]: http://img.shields.io/badge/support-[AppleSilicon]-43BBFF.svg?style=flat
[badge-support-js-ir]: https://img.shields.io/badge/support-[js--IR]-AAC4E0.svg?style=flat
[badge-support-linux-arm]: http://img.shields.io/badge/support-[LinuxArm]-2D3F6C.svg?style=flat

[url-latest-release]: https://github.com/05nelsonm/kmp-process/releases/latest
[url-license]: https://www.apache.org/licenses/LICENSE-2.0
[url-immutable]: https://github.com/05nelsonm/immutable
[url-kmp-file]: https://github.com/05nelsonm/kmp-file
[url-kotlin]: https://kotlinlang.org
[url-posix-spawn]: https://man7.org/linux/man-pages/man3/posix_spawn.3.html
