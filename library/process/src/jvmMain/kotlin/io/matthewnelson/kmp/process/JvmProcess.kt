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
@file:JvmName("JvmProcess")

package io.matthewnelson.kmp.process

import java.io.BufferedOutputStream

/**
 * Currently, only Jvm supports writing input to `stdin`.
 *
 * This extension function provides access for that.
 * */
@get:JvmName("stdin")
public val Process.stdin: BufferedOutputStream? get() {
    // input is only ever non-null when it is Stdio.Pipe,
    // which on Java is a ProcessPipeOutputStream, which
    // is an instance of BufferedOutputStream.
    return input?.buffered()
}
