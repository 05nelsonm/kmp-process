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
![badge-platform-wasm]
![badge-platform-android-native]
![badge-platform-linux]
![badge-platform-ios]
![badge-platform-macos]

`Process` implementation for Kotlin Multiplatform.

API docs available at [https://kmp-process.matthewnelson.io][url-docs]

API is highly inspired by `Node.js` [child_process][url-node-child_process] 
and `Rust` [Command][url-rust-command]

## Info

| Platform         | Process Creation Method Used                                                        |
|------------------|-------------------------------------------------------------------------------------|
| `Android`        | `java.lang.ProcessBuilder`                                                          |
| `Jvm`            | `java.lang.ProcessBuilder`                                                          |
| `Js/Node`        | [spawn][url-node-spawn] and [spawnSync][url-node-spawn-sync]                        |
| `WasmJs/Node`    | [spawn][url-node-spawn] and [spawnSync][url-node-spawn-sync]                        |
| `Native/Android` | [posix_spawn][url-posix-spawn] or [fork][url-posix-fork]/[execve][url-posix-execve] |
| `Native/Linux`   | [posix_spawn][url-posix-spawn] or [fork][url-posix-fork]/[execve][url-posix-execve] |
| `Native/macOS`   | [posix_spawn][url-posix-spawn] or [fork][url-posix-fork]/[execve][url-posix-execve] |
| `Native/iOS`     | [posix_spawn][url-posix-spawn]                                                      |

**NOTE:** `java.lang.ProcessBuilder` and `java.lang.Process` Java 8 functionality is backported for 
Android API 23 and below. Testing covers API 15+.

**NOTE:** Spawning of processes for non-`macOS` Apple targets (i.e. `iOS`, etc.) will work on 
simulators when utilizing executables compiled for `macOS` (and codesigned). Unfortunately, due 
to the `com.apple.security.app-sandbox` entitlement inhibiting modification of a file's permissions 
to set as executable, `posix_spawn` will likely fail unless you have access an executable already on 
the device.  

## Example

```kotlin
val builder = Process.Builder(command = "cat")
    // Optional arguments
    .args("--show-ends")
    // Also accepts vararg and List<String>
    .args("--number", "--squeeze-blank")

    // Change the process's working directory (extension
    // function available for non-apple mobile).
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

// Spawn the process (Blocking APIs for Jvm/Native)
builder.createProcess().let { p ->

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

// Spawn the process (Async APIs for all platforms)
myScope.launch {
    builder.createProcessAsync().use { p ->

        val exitCode: Int? = p.waitForAsync(500.milliseconds)

        if (exitCode == null) {
            println("Process did not complete after 500ms")
            // do something
        }

        // wait until process completes. If myScope
        // is cancelled, will automatically pop out.
        p.waitForAsync()
    } // << Process.destroy automatically called on Closeable.use lambda closure
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
builder.stdout(Stdio.Pipe).stderr(Stdio.Pipe).createProcess().use { p ->

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
} // << Process.destroy automatically called on Closeable.use lambda closure

// Wait for asynchronous stdout/stderr output to stop
// after Process.destroy is called
myScope.launch {
    val exitCode = builder.createProcessAsync().use { p ->
        p.stdoutFeed { line ->
            // do something
        }.stderrFeed { line ->
            // do something
        }.waitForAsync(50.milliseconds)

        p // return Process to spawn lambda
    } // << Process.destroy automatically called on Closeable.use lambda closure

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
}.createProcess().use { p ->
    p.stdoutFeed { line ->
        myOtherClassThatHasABugAndWillThrowException.parse(line)
    }.waitFor()
}
```

## Get Started

<!-- TAG_VERSION -->

```kotlin
dependencies {
    implementation("io.matthewnelson.kmp-process:process:0.4.0")
}
```

**NOTE:** For Java9+ consumers, module `java.management` is required. See [issue-139][url-issue-139]

<!-- TAG_VERSION -->
[badge-latest-release]: https://img.shields.io/badge/latest--release-0.4.0-blue.svg?style=flat
[badge-license]: https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat

<!-- TAG_DEPENDENCIES -->
[badge-coroutines]: https://img.shields.io/badge/kotlinx.coroutines-1.10.2-blue.svg?logo=kotlin
[badge-bitops]: https://img.shields.io/badge/kotlincrypto.bitops-0.3.0-blue.svg?style=flat
[badge-immutable]: https://img.shields.io/badge/immutable-0.3.0-blue.svg?style=flat
[badge-kmp-file]: https://img.shields.io/badge/kmp--file-0.6.0--SNAPSHOT-blue.svg?style=flat
[badge-kotlin]: https://img.shields.io/badge/kotlin-2.2.21-blue.svg?logo=kotlin

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
[badge-platform-wasi]: https://img.shields.io/badge/-wasi-18a033.svg?style=flat
[badge-platform-windows]: http://img.shields.io/badge/-windows-4D76CD.svg?style=flat
[badge-platform-android-native]: http://img.shields.io/badge/-android--native-6EDB8D.svg?style=flat

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
[url-issue-139]: https://github.com/05nelsonm/kmp-process/issues/139
