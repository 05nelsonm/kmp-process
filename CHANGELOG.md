# CHANGELOG

## Version 0.3.0 (2025-06-11)
 - Updates `kotlin` to `2.1.21` [[#143]][143]
 - Updates `kmp-file` to `0.3.0` [[#143]][143] [[#154]][154]
 - Cleans up internal Kotlin/Native implementation by separating out `iOS` internals from 
   other targets where `fork`/`execve` APIs are available [[#144]][144]
 - Fixes cross-platform behavior differences when utilizing a relative file path command in 
   combination with `changeDir` [[#146]][146]
 - Adds support for the following targets [[#147]][147]
     - `androidNativeArm32`
     - `androidNativeArm64`
     - `androidNativeX64`
     - `androidNativeX86`
 - Fixes Android API 24 to 32 `ProcessBuilder.environment` not reflecting that of the 
   `Os.environ` [[#150]][150]
     - The Android `ProcessEnvironment` implementation for those API versions were caching 
       the native `environ` on first invocation and never updating to reflect the actual 
       environment for the lifetime of the application.
 - Deprecates `spawn {}` and replaces it with `useSpawn {}` [[#151]][151]
 - Fixes `Blocking.threadSleep` Kotlin/Native implementation when duration is greater than 
   1 second by invoking `usleep` in durations smaller than 1 second multiple times until
   the total duration desired has passed [[#153]][153]

## Version 0.2.1 (2025-05-09)
 - Updates `kotlinx.coroutines` to `1.10.2` [[#141]][141]
 - Fixes `NoClassDefFoundException` when `Process.Current.pid` is called on Java9+ whereby 
   module `java.management` is not present. An `UnsupportedOperationException` is now thrown
   in this event. [[#140]][140]

## Version 0.2.0 (2025-02-26)
 - Updates `kotlin` to `2.1.10` [[#133]][133]
 - Updates `immutable` to `0.2.0` [[#133]][133]
 - Updates `kmp-file` to `0.2.0` [[#133]][133]
 - Updates `kotlincrypto.bitops` to `0.2.0` [[#138]][138]
 - Updates `kotlinx.coroutines` to `1.10.1` [[#133]][133]
 - Replaces usage of `org.khronos.webgl` JS Array with locally defined types [[#136]][136]
 - Performance improvements to internal lock/synchronization usage [[#137]][137]

## Version 0.1.2 (2024-12-30)
 - Fixes inability to configure `detached` option on `Node.js` [[#132]][132]
 - Use `ArrayDeque` instead of `ArrayList` when buffering `stdout`/`stderr` [[#131]][131]

## Version 0.1.1 (2024-12-18)
 - Fixes inability to configure process options on `Node.js` for [[#128]][128]:
     - `shell`
     - `windowsVerbatimArguments`
     - `windowsHide`
 - Deprecates `Process.Builder.chdir` in favor of platform specific extension 
   function `Process.Builder.changeDir`, available for all non-Apple mobile 
   targets. [[#128]][128]
 - Adds `dokka` documentation at `https://kmp-process.matthewnelson.io` [[#126]][126]

## Version 0.1.0 (2024-12-01)
 - Updates test dependency to non `-SNAPSHOT` version of `kmp-tor-resource` [[#124]][124]

## Version 0.1.0-rc01 (2024-11-11)
 - Fixes multiplatform metadata manifest `unique_name` parameter for
   all source sets to be truly unique. [[#121]][121]
 - Updates jvm `.kotlin_module` with truly unique file name. [[#121]][121]
 - Updates dependencies [[#121]][121]
     - `immutable` -> `0.1.4`
     - `kmp-file` -> `0.1.1`
     - `kotlincrypto.endians` -> `0.3.1`

## Version 0.1.0-beta02 (2024-06-19)
 - Fixes issue for `Node.js` on Windows where `Process.destroy` may
   throw exception due to a bug in `libuv` version `1.48.0` which was
   introduced in `Node.js` version `21.6.2` [[#111]][111]
     - See issue [[#108]][108-issue] for details.
 - Adds the `Process.exitCodeOrNull` function to mitigate unnecessary
   production of stack traces [[#112]][112]
 - Adds an exception handling API for dealing with "internal-ish" `Process`
   errors, and "bad" `OutputFeed` implementations [[#113]][113]
     - By default `ProcessException.Handler.IGNORE` is utilized which
       preserves the behavior of previous versions of `kmp-process`.

## Version 0.1.0-beta01 (2024-06-15)
 - `AsyncWriteStream` improvements for `Node.js` implementation [[#98]][98]
 - `unref` is now called on `Process` when destroyed for `Node.js` [[#100]][100]
 - Adds usage of `posix_spawnp` for Native Unix when `command`
   is a program name (no file system separators present) [[#106]][106]
 - Updates dependencies [[#107]][107]
     - `immutable` -> `0.1.3`
     - `kmp-file` -> `0.1.0`
     - `kotlin` -> `1.9.24`
     - `kotlinx-coroutines` -> `1.8.1`

## Version 0.1.0-alpha03 (2024-04-01)
 - Fixes `OutputFeed` line parsing. Now checks for `CR` and `LF`
   line terminators [[#94]][94]
     - **NOTE:** `OutputFeed.onOutput` now dispatches `String?`
       instead of `String` to indicate End Of Stream via `null`
 - Uses  `NonCancellable` + `Dispatchers.IO` for `Jvm` & `Native`
   when utilizing `AsyncWriteStream` asynchronous APIs [[#95]][95]

## Version 0.1.0-alpha02 (2024-03-19)
 - Update dependencies [[#88]][88]
     - `immutable` -> `0.1.2`
     - `kmp-file` -> `0.1.0-beta03`
     - `kotlin` -> `1.9.23`
     - `kotlincrypto.endians` -> `0.3.0`
 - Adds support for `JPMS` via Multi-Release Jar [[#88]][88]
 - Close unneeded file descriptors for parent process [[#85]][85]
 - Use `compileOnly` for `coroutines` dependency for `Jvm` [[#89]][89]

## Version 0.1.0-alpha01 (2024-03-06)
 - Initial Release

[85]: https://github.com/05nelsonm/kmp-process/pull/85
[88]: https://github.com/05nelsonm/kmp-process/pull/88
[89]: https://github.com/05nelsonm/kmp-process/pull/89
[94]: https://github.com/05nelsonm/kmp-process/pull/94
[95]: https://github.com/05nelsonm/kmp-process/pull/95
[98]: https://github.com/05nelsonm/kmp-process/pull/98
[100]: https://github.com/05nelsonm/kmp-process/pull/100
[106]: https://github.com/05nelsonm/kmp-process/pull/106
[107]: https://github.com/05nelsonm/kmp-process/pull/107
[108-issue]: https://github.com/05nelsonm/kmp-process/issues/108
[111]: https://github.com/05nelsonm/kmp-process/pull/111
[112]: https://github.com/05nelsonm/kmp-process/pull/112
[113]: https://github.com/05nelsonm/kmp-process/pull/113
[121]: https://github.com/05nelsonm/kmp-process/pull/121
[124]: https://github.com/05nelsonm/kmp-process/pull/124
[126]: https://github.com/05nelsonm/kmp-process/pull/126
[128]: https://github.com/05nelsonm/kmp-process/pull/128
[131]: https://github.com/05nelsonm/kmp-process/pull/131
[132]: https://github.com/05nelsonm/kmp-process/pull/132
[133]: https://github.com/05nelsonm/kmp-process/pull/133
[136]: https://github.com/05nelsonm/kmp-process/pull/136
[137]: https://github.com/05nelsonm/kmp-process/pull/137
[138]: https://github.com/05nelsonm/kmp-process/pull/138
[140]: https://github.com/05nelsonm/kmp-process/pull/140
[141]: https://github.com/05nelsonm/kmp-process/pull/141
[143]: https://github.com/05nelsonm/kmp-process/pull/143
[144]: https://github.com/05nelsonm/kmp-process/pull/144
[146]: https://github.com/05nelsonm/kmp-process/pull/146
[147]: https://github.com/05nelsonm/kmp-process/pull/147
[150]: https://github.com/05nelsonm/kmp-process/pull/150
[151]: https://github.com/05nelsonm/kmp-process/pull/151
[153]: https://github.com/05nelsonm/kmp-process/pull/153
[154]: https://github.com/05nelsonm/kmp-process/pull/154
