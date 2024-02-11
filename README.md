# kmp-process
[![badge-license]][url-license]
[![badge-latest-release]][url-latest-release]

[![badge-kotlin]][url-kotlin]
[![badge-kmp-file]][url-kmp-file]
[![badge-immutable]][url-immutable]

![badge-platform-android]
![badge-platform-jvm]
![badge-platform-js-node]
![badge-platform-linux]
![badge-platform-ios]
![badge-platform-macos]
![badge-support-apple-silicon]
![badge-support-linux-arm]

`Process` implementation for Kotlin Multiplatform.

API is highly inspired by `Node.js` [child_process][url-node-child_process] 
and `Rust` [Command][url-rust-command]

## Info

|           | Process Creation Method Used                                     |
|-----------|------------------------------------------------------------------|
| `Jvm`     | `java.lang.ProcessBuilder`                                       |
| `Node.js` | [child_process][url-node-child_process]                          |
| `Linux`   | [posix_spawn][url-posix-spawn] & [posix_spawnp][url-posix-spawn] |
| `macOS`   | [posix_spawn][url-posix-spawn] & [posix_spawnp][url-posix-spawn] |
| `iOS`     | [posix_spawn][url-posix-spawn] & [posix_spawnp][url-posix-spawn] |

## Example

```kotlin
val builder = Process.Builder(command = "cat")
    // Optional arguments
    .args("--show-ends")
    // Also accepts vararg and List<String>
    .args("--number", "--squeeze-blank")

    // Modify the Signal to send the Process
    // when Process.destroy is called (only sent
    // if the Process has not completed yet).
    .destroySignal(Signal.SIGKILL)

    // Take input from a file
    .stdin(Stdio.File.of("build.gradle.kts"))
    // Pipe output to system out
    .stdout(Stdio.Inherit)
    // Dump error output to log file
    .stderr(Stdio.File.of("logs/example_cat.err"))

    // Modify the environment variables inherited
    // from the current process (parent).
    .environment {        
        remove("HOME")
        // ...
    }
    // shortcut to set/overwrite an environment
    // variable
    .environment("HOME", myApplicationDir.path)

// Spawned process
builder.spawn().let { p ->

    try {
        // Blocking APIs (Jvm & Native).
        //
        // Alternatively, waitForAsync is available for all
        // platforms
        val code: Int? = p.waitFor(250.milliseconds)

        if (code == null) {
            println("Process did not complete after 250ms")
            // do something
        }
    } finally {
        p.destroy()
    }
}

// Direct output
builder.output { timeoutMillis = 500 }.let { output ->
    println(output.stdout)
    println(output.stderr)
    println(output.processError ?: "no errors")
    println(output.processInfo)
}
```

## Get Started

<!-- TAG_VERSION -->

```kotlin
dependencies {
    implementation("io.matthewnelson.kmp-process:process:[TODO]")
}
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
[url-node-child_process]: https://nodejs.org/api/child_process.html
[url-posix-spawn]: https://man7.org/linux/man-pages/man3/posix_spawn.3.html
[url-rust-command]: https://doc.rust-lang.org/std/process/struct.Command.html
