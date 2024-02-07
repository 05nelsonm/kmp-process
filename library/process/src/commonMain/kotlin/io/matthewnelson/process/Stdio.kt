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

/**
 * Standard input/output stream types for [Process]
 * `stdin`, `stdout`, and `stderr`.
 * */
public sealed class Stdio private constructor() {

    /**
     * Inherit the system's standard input/output.
     * */
    public data object Inherit: Stdio()

    /**
     * Dumps [Process] output to the system's
     * "null file" destination (e.g. `/dev/null`)
     * */
    public data object Null: Stdio()

    /**
     * Connects the [Process] with its parent via
     * a pipe.
     *
     * This is the default type for all [Process]
     * standard input/output.
     * */
    public data object Pipe: Stdio()
}
