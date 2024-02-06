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
@file:Suppress("KotlinRedundantDiagnosticSuppress")

package io.matthewnelson.process.internal

import io.matthewnelson.process.Process
import io.matthewnelson.process.ProcessException
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@Suppress("NOTHING_TO_INLINE")
internal inline fun Process.Builder.commonArgs(
    bArgs: MutableList<String>,
    arg: String,
): Process.Builder = apply { bArgs.add(arg) }

@Suppress("NOTHING_TO_INLINE")
internal inline fun Process.Builder.commonArgs(
    bArgs: MutableList<String>,
    args: Array<out String>,
): Process.Builder = apply { args.forEach { bArgs.add(it) } }

@Suppress("NOTHING_TO_INLINE")
internal inline fun Process.Builder.commonArgs(
    bArgs: MutableList<String>,
    args: List<String>,
): Process.Builder = apply { args.forEach { bArgs.add(it) } }

@Suppress("NOTHING_TO_INLINE")
internal inline fun Process.Builder.commonEnvironment(
    bEnv: MutableMap<String, String>,
    key: String,
    value: String,
): Process.Builder = apply { bEnv[key] = value }

@Suppress("NOTHING_TO_INLINE")
@OptIn(ExperimentalContracts::class)
internal inline fun Process.Builder.commonWithEnvironment(
    bEnv: MutableMap<String, String>,
    block: MutableMap<String, String>.() -> Unit,
): Process.Builder {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    bEnv.apply(block)
    return this
}

@Suppress("NOTHING_TO_INLINE")
@Throws(ProcessException::class)
internal inline fun Process.Builder.commonCheckCommand() {
    if (command.isNotBlank()) return
    throw ProcessException("command cannot be blank")
}
