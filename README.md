# kmp-process
[![badge-license]][url-license]
[![badge-latest-release]][url-latest-release]

[![badge-kotlin]][url-kotlin]
[![badge-coroutines]][url-coroutines]
[![badge-bitops]][url-bitops]
[![badge-immutable]][url-immutable]
[![badge-kmp-file]][url-kmp-file]

![badge-platform-android]
![badge-platform-jvm]
![badge-platform-js-node]
![badge-platform-linux]
![badge-platform-ios]
![badge-platform-macos]
![badge-support-apple-silicon]
![badge-support-linux-arm]

`Process` implementation for Kotlin Multiplatform.

API docs available at [https://kmp-process.matthewnelson.io][url-docs]

API is highly inspired by `Node.js` [child_process][url-node-child_process] 
and `Rust` [Command][url-rust-command]

## Info

|           | Process Creation Method Used                                                        |
|-----------|-------------------------------------------------------------------------------------|
| `Android` | `java.lang.ProcessBuilder`                                                          |
| `Jvm`     | `java.lang.ProcessBuilder`                                                          |
| `Node.js` | [spawn][url-node-spawn] and [spawnSync][url-node-spawn-sync]                        |
| `Linux`   | [posix_spawn][url-posix-spawn] or [fork][url-posix-fork]/[execve][url-posix-execve] |
| `macOS`   | [posix_spawn][url-posix-spawn] or [fork][url-posix-fork]/[execve][url-posix-execve] |
| `iOS`     | [posix_spawn][url-posix-spawn]                                                      |

**NOTE:** `java.lang.ProcessBuilder` and `java.lang.Process` Java 8 functionality is backported 
for Android and tested against API 15+.

**NOTE:** Spawning of processes for Apple mobile targets will work on simulators when utilizing 
executables compiled for `macOS`. Unfortunately due to the `com.apple.security.app-sandbox`
entitlement inhibiting modification of a file's permissions to set as executable, `posix_spawn` 
will likely fail on the device (unless executing a file already accessible on the OS that **is** 
executable).  

## Example

**NOTE:** Async API usage on `Jvm` & `Android` requires the `kotlinx.coroutines.core` dependency.

```kotlin
val builder = Process.Builder(command = "cat")
    // Optional arguments
    .args("--show-ends")
    // Also accepts vararg and List<String>
    .args("--number", "--squeeze-blank")

    // Change the process's working directory
    // (extension available for non-apple mobile).
    .changeDir(myApplicationDir)

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

// Spawned process (Blocking APIs for Jvm/Native)
builder.spawn().let { p ->

    try {
        val exitCode: Int? = p.waitFor(250.milliseconds)

        if (exitCode == null) {
            println("Process did not complete after 250ms")
            // do something
        }
    } finally {
        p.destroy()
    }
}

// Spawned process (Async APIs for all platforms)
myScope.launch {

    // Use spawn {} (with lambda) which will
    // automatically call destroy upon lambda closure,
    // instead of needing the try/finally block.
    builder.spawn { p ->

        val exitCode: Int? = p.waitForAsync(500.milliseconds)

        if (exitCode == null) {
            println("Process did not complete after 500ms")
            // do something
        }

        // wait until process completes. If myScope
        // is cancelled, will automatically pop out.
        p.waitForAsync()
    } // << Process.destroy automatically called on closure
}

// Direct output (Blocking API for all platforms)
builder.output {
    maxBuffer = 1024 * 24
    timeoutMillis = 500
}.let { output ->
    println(output.stdout)
    println(output.stderr)
    println(output.processError ?: "no errors")
    println(output.processInfo)
}

// Piping output (feeds are only functional with Stdio.Pipe)
builder.stdout(Stdio.Pipe).stderr(Stdio.Pipe).spawn { p ->

    val exitCode = p.stdoutFeed { line ->
        // single feed lambda

        // line dispatched from `stdout` bg thread (Jvm/Native) 
        println(line)
    }.stderrFeed(
        // vararg for attaching multiple OutputFeed at once
        // so no data is missed (reading starts on the first
        // OutputFeed attachment for that Pipe)
        OutputFeed { line ->
            // line dispatched from `stderr` bg thread (Jvm/Native)
            println(line)
        },
        OutputFeed { line ->
            // do something else
        },
    ).waitFor(5.seconds)

    println("EXIT_CODE[$exitCode]")
} // << Process.destroy automatically called on closure

// Wait for asynchronous stdout/stderr output to stop
// after Process.destroy is called
myScope.launch {
    val exitCode = builder.spawn { p ->
        p.stdoutFeed { line ->
            // do something
        }.stderrFeed { line ->
            // do something
        }.waitForAsync(50.milliseconds)

        p // return Process to spawn lambda
    } // << Process.destroy automatically called on closure

        // blocking APIs also available for Jvm/Native
        .stdoutWaiter()
        .awaitStopAsync()
        .stderrWaiter()
        .awaitStopAsync()
        .waitForAsync()
    
    println("EXIT_CODE[$exitCode]")
}

// Error handling API for "internal-ish" process errors.
// By default, ProcessException.Handler.IGNORE is used,
// but you may supplement that with your own handler.
builder.onError { e ->
    // e is always an instance of ProcessException
    //
    // Throwing an exception from here will be caught,
    // the process destroyed (to prevent zombie processes),
    // and then be re-thrown. That will likely cause a crash,
    // but you can do it and know that the process has been
    // cleaned up before getting crazy.

    when (e.context) {
        ProcessException.CTX_DESTROY -> {
            // Process.destroy had an issue, such as a
            // file descriptor closure failure on Native.
            e.cause.printStackTrace()
        }
        ProcessException.CTX_FEED_STDOUT,
        ProcessException.CTX_FEED_STDERR -> {
            // An attached OutputFeed threw exception
            // when a line was dispatched to it. Let's
            // get crazy and potentially crash the app.
            throw e
        }
        // Currently, the only other place a ProcessException
        // will come from is the `Node.js` implementation's
        // ChildProcess error listener.
        else -> e.printStackTrace()
    }
}.spawn { p ->
    p.stdoutFeed { line ->
        myOtherClassThatHasABugAndWillThrowException.parse(line)
    }.waitFor()
}
```

## Get Started

<!-- TAG_VERSION -->

```kotlin
dependencies {
    implementation("io.matthewnelson.kmp-process:process:0.2.0")
}
```

<!-- TAG_VERSION -->
[badge-latest-release]: https://img.shields.io/badge/latest--release-0.2.0-blue.svg?style=flat
[badge-license]: https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat

<!-- TAG_DEPENDENCIES -->
[badge-coroutines]: https://img.shields.io/badge/kotlinx.coroutines-1.10.1-blue.svg?logo=kotlin
[badge-bitops]: https://img.shields.io/badge/kotlincrypto.bitops-0.2.0-blue.svg?style=flat
[badge-immutable]: https://img.shields.io/badge/immutable-0.2.0-blue.svg?style=flat
[badge-kmp-file]: https://img.shields.io/badge/kmp--file-0.2.0-blue.svg?style=flat
[badge-kotlin]: https://img.shields.io/badge/kotlin-2.1.10-blue.svg?logo=kotlin

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

[url-docs]: https://kmp-process.matthewnelson.io
[url-latest-release]: https://github.com/05nelsonm/kmp-process/releases/latest
[url-license]: https://www.apache.org/licenses/LICENSE-2.0
[url-coroutines]: https://github.com/Kotlin/kotlinx.coroutines
[url-bitops]: https://github.com/KotlinCrypto/bitops
[url-immutable]: https://github.com/05nelsonm/immutable
[url-kmp-file]: https://github.com/05nelsonm/kmp-file
[url-kotlin]: https://kotlinlang.org
[url-node-child_process]: https://nodejs.org/api/child_process.html
[url-node-spawn]: https://nodejs.org/api/child_process.html#child_processspawncommand-args-options
[url-node-spawn-sync]: https://nodejs.org/api/child_process.html#child_processspawnsynccommand-args-options
[url-posix-execve]: https://man7.org/linux/man-pages/man2/execve.2.html
[url-posix-fork]: https://man7.org/linux/man-pages/man2/fork.2.html
[url-posix-spawn]: https://man7.org/linux/man-pages/man3/posix_spawn.3.html
[url-rust-command]: https://doc.rust-lang.org/std/process/struct.Command.html
