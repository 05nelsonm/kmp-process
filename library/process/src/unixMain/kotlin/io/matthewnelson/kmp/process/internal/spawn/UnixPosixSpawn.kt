/*
 * Copyright (c) 2025 Matthew Nelson
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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "NOTHING_TO_INLINE", "FunctionName")

package io.matthewnelson.kmp.process.internal.spawn

import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.FileNotFoundException
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.SysDirSep
import io.matthewnelson.kmp.file.errnoToIOException
import io.matthewnelson.kmp.file.exists2
import io.matthewnelson.kmp.file.path
import io.matthewnelson.kmp.file.toFile
import io.matthewnelson.kmp.process.ProcessException
import io.matthewnelson.kmp.process.Signal
import io.matthewnelson.kmp.process.Stdio
import io.matthewnelson.kmp.process.internal.NativeProcess
import io.matthewnelson.kmp.process.internal.awaitExecOrFailure
import io.matthewnelson.kmp.process.internal.stdio.StdioDescriptor.Pipe.Companion.fdOpen
import io.matthewnelson.kmp.process.internal.stdio.StdioHandle.Companion.openHandle
import io.matthewnelson.kmp.process.internal.stdio.withFd
import io.matthewnelson.kmp.process.internal.toArgv
import io.matthewnelson.kmp.process.internal.toEnvp
import io.matthewnelson.kmp.process.internal.tryCloseSuppressed
import kotlinx.cinterop.AutofreeScope
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.NativePointed
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.alloc
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.posix.ENOENT
import platform.posix.STDERR_FILENO
import platform.posix.STDIN_FILENO
import platform.posix.STDOUT_FILENO
import platform.posix.X_OK
import platform.posix._SC_OPEN_MAX
import platform.posix.access
import platform.posix.errno
import platform.posix.pid_tVar
import platform.posix.set_posix_errno
import platform.posix.strerror
import platform.posix.sysconf

@OptIn(ExperimentalForeignApi::class)
internal expect class PosixSpawnScope: AutofreeScope {
    internal val hasCLOEXEC: Boolean
    override fun alloc(size: Long, align: Int): NativePointed
}

@OptIn(ExperimentalForeignApi::class)
@Throws(UnsupportedOperationException::class)
internal expect inline fun PosixSpawnScope.file_actions_addchdir(chdir: File): Int

@OptIn(ExperimentalForeignApi::class)
internal expect inline fun PosixSpawnScope.file_actions_addclose(fd: Int): Int

@OptIn(ExperimentalForeignApi::class)
internal expect inline fun PosixSpawnScope.file_actions_adddup2(fd: Int, newFd: Int): Int

@OptIn(ExperimentalForeignApi::class)
internal expect inline fun PosixSpawnScope.spawn(
    program: String,
    pid: CValuesRef<pid_tVar>,
    argv: CValuesRef<CPointerVar<ByteVar>>,
    envp: CValuesRef<CPointerVar<ByteVar>>,
): Int

@OptIn(ExperimentalForeignApi::class)
internal expect inline fun PosixSpawnScope.spawn_p(
    program: String,
    pid: CValuesRef<pid_tVar>,
    argv: CValuesRef<CPointerVar<ByteVar>>,
    envp: CValuesRef<CPointerVar<ByteVar>>,
): Int

/**
 * Returns `null` if platform support is not available, or if initialization calls of
 * `posix_spawnattr_init` or `posix_spawn_file_actions_init` failed.
 *
 * @param [requireChangeDir] If posix_spawn_file_actions_addchdir_np is required
 *
 * @throws [UnsupportedOperationException] if and only if on `iOS` and [requireChangeDir] is true.
 * */
@Throws(UnsupportedOperationException::class)
@OptIn(ExperimentalForeignApi::class)
internal expect inline fun <T: Any> posixSpawnScopeOrNull(
    requireChangeDir: Boolean,
    block: PosixSpawnScope.() -> T,
): T?

/**
 * Returns `null` if platform support is not available, or if initialization calls of
 * `posix_spawnattr_init` or `posix_spawn_file_actions_init` failed.
 *
 * @throws [IOException] if there was a failure to spawn the process
 * @throws [UnsupportedOperationException] if and only if on `iOS` and [chdir] is non-null.
 * */
@OptIn(ExperimentalForeignApi::class)
@Throws(IOException::class, UnsupportedOperationException::class)
internal fun posixSpawn(
    command: String,
    args: List<String>,
    chdir: File?,
    env: Map<String, String>,
    stdio: Stdio.Config,
    destroy: Signal,
    handler: ProcessException.Handler,
): NativeProcess? = posixSpawnScopeOrNull(requireChangeDir = chdir != null) {
    val fdsLimit = if (!hasCLOEXEC) {
        set_posix_errno(0)
        @OptIn(UnsafeNumber::class)
        @Suppress("RemoveRedundantCallsOfConversionMethods")
        val openMax = sysconf(_SC_OPEN_MAX).toInt()
        if (openMax == -1 && errno != 0) throw errnoToIOException(errno)
        if (openMax < 20) {
            // _SC_OPEN_MAX must not be less than _POSIX_OPEN_MAX (20)
            // as per documentation. Something must be wrong...
            //
            // In practice only a small number of fds are open, but this
            // will cover them all.
            1_000_000
        } else {
            openMax
        }
    } else {
        -1
    }

    if (chdir != null) {
        // posix_spawn_file_actions_addchdir returns a non-zero value to indicate the error.
        val ret = file_actions_addchdir(chdir)
        if (ret != 0) {
            // strdup failed or something
            val msg = strerror(ret)?.toKString() ?: "errno: $ret"
            throw IOException("posix_spawn_file_actions_addchdir_np failed >> $msg")
        }
    }

    val handle = stdio.openHandle()

    val pipe = try {
        Stdio.Pipe.fdOpen(readEndNonBlock = true)
    } catch (e: IOException) {
        throw handle.tryCloseSuppressed(e)
    }

    if (fdsLimit > 0) {
        val excludeFds = LinkedHashSet<Int>(9, 1.0f).apply {
            // Do not dup2/close {0/1/2}
            add(STDIN_FILENO); add(STDOUT_FILENO); add(STDERR_FILENO)

            // Do not dup2/close any of the handle's descriptors that are to be dup'd
            handle.dup2 { fd, _ -> add(fd); null }

            // Do not dup2/close the pipes. They are already configured with CLOEXEC.
            add(pipe.read.withFd { it })
            add(pipe.write.withFd { it })
        }

        // Unfortunately there is no way to specify "close all descriptors from the parent
        // process except these", so we are left with a hammer-ish technique to force close
        // any descriptors between 3 - _SC_OPEN_MAX (excluding descriptors we know have O_CLOEXEC).
        repeat(fdsLimit) { fd ->
            if (excludeFds.contains(fd)) return@repeat

            // dup2 will close the descriptor if one is open at fd
            var ret = file_actions_adddup2(/* fakeFd = */ 0, fd)
            if (ret == 0) {
                // Then close the dup of 0
                ret = file_actions_addclose(fd)
            }
            if (ret == 0) return@repeat

            val e = errnoToIOException(ret)
            handle.tryCloseSuppressed(e)
            throw pipe.tryCloseSuppressed(e)
        }
    }

    // handle closes automatically on any failures
    handle.dup2(action = { fd, newFd ->
        // posix_spawn_file_actions_adddup2 returns a non-zero value to indicate the error.
        when (val ret = file_actions_adddup2(fd, newFd)) {
            0 -> null
            // EBADF
            else -> {
                val e = errnoToIOException(ret)
                pipe.tryCloseSuppressed(e)
                e
            }
        }
    })

    val pid = try {
        val pidRef = alloc<pid_tVar>().apply { value = -1 }

        val argv = args.toArgv(command = command, scope = this)
        val envp = env.toEnvp(scope = this)

        // posix_spawn & posix_spawnp return a non-zero value to indicate an error
        // with its fork/vfork step.
        //
        // Linux can actually return ENOENT here for glibc 2.24+ and won't even spawn
        // the child process, whereas Android/iOS/macOS will only return a failure if
        // fork/vfork fail.
        val ret = if (command.contains(SysDirSep)) {
            // Under the hood, implementations will use execve
            spawn(command, pidRef.ptr, argv, envp)
        } else {
            // Under the hood, implementations will use execvpe
            spawn_p(command, pidRef.ptr, argv, envp)
        }

        val pid = pidRef.value
        if (ret != 0 || pid <= 0) {
            throw spawnFailureToIOException(command, chdir, spawnRet = ret)
        }

        pid
    } catch (e: IOException) {
        handle.tryCloseSuppressed(e)
        throw pipe.tryCloseSuppressed(e)
    }

    val p = NativeProcess(
        pid,
        handle,
        command,
        args,
        chdir,
        env,
        destroy,
        handler,
    )

    p.awaitExecOrFailure(
        ByteArray(1),
        pipe,
        onNonZeroExitCode = { code ->
            // Any failures with the post-fork, pre-exec/exec steps will
            // result in code 127 (according to the POSIX standard).
            if (code == 127) {
                spawnFailureToIOException(command, chdir, spawnRet = 0)
            } else {
                null
            }
        }
    )

    p
}

/*
* This is where we try to figure out, based off of inputs, what went wrong in the
* posix_spawn call to provide as detailed an error message as possible.
* */
private fun spawnFailureToIOException(command: String, chdir: File?, spawnRet: Int): IOException {
    val commandFile = command.toFile()
    var msg = if (command.contains(SysDirSep)) "posix_spawn" else "posix_spawnp"

    if (spawnRet != 0) {
        msg += " failed to spawn the process >> "
        @OptIn(ExperimentalForeignApi::class)
        msg += strerror(spawnRet)?.toKString() ?: "errno: $spawnRet"
        msg += "."
    } else {
        msg += " failed in its pre-exec/exec steps and exited the child process with code[127]."
    }

    var ioException: (String) -> IOException = if (spawnRet == ENOENT) ::FileNotFoundException else ::IOException

    if (chdir != null && chdir.isAbsolute()) {
        try {
            if (!chdir.exists2()) {
                msg += " Directory specified for changeDir does not seem to exist."
                ioException = ::FileNotFoundException
            }
        } catch (_: IOException) {}
    }

    if (
        // Relative path command whereby the child process' CWD
        // was the same as ours here (the parent process).
        (chdir == null && command.contains(SysDirSep))
        // Alternatively, can also check if it's an absolute path.
        || commandFile.isAbsolute()
    ) {
        val commandFileExists = try {
            commandFile.exists2()
        } catch (_: IOException) {
            // permissions?
            false
        }

        when {
            !commandFileExists -> {
                msg += "Command[$command] does not seem to exist."
                ioException = ::FileNotFoundException
            }
            access(commandFile.path, X_OK) != 0 -> {
                msg += "Command[$command] does not seem to have executable permissions."
            }
        }
    } else {
        msg += " Bad arguments for '$command'?"
    }

    return ioException(msg)
}
