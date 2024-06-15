# CHANGELOG

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
