package io.github.samolego.canta.core.root

import io.github.samolego.canta.core.adb.ShellCommandException
import io.github.samolego.canta.core.adb.ShellResult
import io.github.samolego.canta.util.LogUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One long-lived `su` shell, shared by everything in this process that needs root.
 *
 * Every separate `su -c` is a fresh request to the root manager, and a manager set to ask each
 * time answers each one with its own prompt: a start-up that reads the SDK level, the current user
 * and the helper checksum was three prompts before the first app list appeared, and the install
 * path added one more. One session means one prompt, and every command afterwards reuses it.
 *
 * ## How a command is framed
 *
 * The shell is interactive: a command is written to its stdin, then the exit code and a marker
 * line. Output is read until the marker, so nothing has to parse the command's own output to know
 * where it ended. Three details keep the framing honest:
 *
 *  - the command runs in a **subshell** (`( … )`), so an `exit` inside a ROM script ends that
 *    command and not the whole session, and its variables never leak into the next one;
 *  - stdin is `/dev/null` for the command, so nothing can swallow the marker line;
 *  - the exit code is captured *before* anything is printed, and a newline is printed first, so a
 *    command whose last line had no trailing newline cannot glue itself to the marker.
 *
 * `stderr` is never merged into stdout: answers like `getprop ro.build.version.sdk` are parsed
 * with `toIntOrNull()`, and one `su` banner line would turn them into null. A daemon thread drains
 * stderr into a buffer instead, which is where an error is reported from.
 */
class RootShellSession {

    private companion object {
        const val TAG = "RootShellSession"

        /** Long enough for a slow `pm install`; a prompt nobody answers is cut by the caller. */
        const val DEFAULT_TIMEOUT_MILLIS = 90_000L

        /** Prefix of the line that closes a command's output; the id makes it unique per command. */
        const val MARKER = "__CANTA_ROOT_DONE__"

        /** How much stderr is kept per command; the rest is only in the log. */
        const val STDERR_MAX_CHARS = 8_192
    }

    /** Serializes commands: one shell, one command at a time. */
    private val lock = Mutex()

    @Volatile
    private var process: Process? = null

    @Volatile
    private var reader: BufferedReader? = null

    @Volatile
    private var writer: BufferedWriter? = null

    @Volatile
    private var drain: Thread? = null

    /** stderr of the command currently running; drained on a daemon thread so the pipe never fills. */
    private val stderr = StringBuilder()

    private var sequence = 0L

    /** Drops the session; the next command opens a new `su` (and may be prompted again). */
    fun close() {
        val running = process
        process = null
        reader = null
        writer = null
        drain = null
        runCatching { running?.destroy() }
        synchronized(stderr) { stderr.setLength(0) }
    }

    /**
     * Closes only if [expected] is still the open session. A watchdog fires after its own command
     * is over; without this check it would close a shell that was opened since.
     */
    private fun closeIfSame(expected: Process?) {
        if (expected != null && process === expected) close()
    }

    private fun ensureOpen(): Boolean {
        if (process?.isAlive == true && reader != null && writer != null) return true
        close()
        return runCatching {
            // The binary the gate found, spelled out: a ROM that keeps `su` off PATH would otherwise
            // fail here with "cannot run program su", which reads as a refusal.
            val su = suBinaryPath() ?: "su"
            val started = ProcessBuilder(su).redirectErrorStream(false).start()
            process = started
            reader = started.inputStream.bufferedReader(Charsets.UTF_8)
            writer = started.outputStream.bufferedWriter(Charsets.UTF_8)
            startDrain(started)
            LogUtils.i(TAG, "Opened a root shell through $su")
            true
        }.getOrElse {
            LogUtils.w(TAG, "Could not open a root shell: $it")
            false
        }
    }

    private fun startDrain(started: Process) {
        drain = Thread({
            runCatching {
                started.errorStream.bufferedReader(Charsets.UTF_8).forEachLine { line ->
                    if (line.isBlank()) return@forEachLine
                    synchronized(stderr) {
                        if (stderr.length < STDERR_MAX_CHARS) stderr.append(line).append('\n')
                    }
                    LogUtils.w(TAG, "su: $line")
                }
            }
        }, "canta-root-stderr").apply {
            isDaemon = true
            start()
        }
    }

    /** The stderr collected for this command, and a fresh buffer for the next one. */
    private fun takeStderr(): String = synchronized(stderr) {
        stderr.toString().also { stderr.setLength(0) }
    }

    private fun writeCommand(command: String, marker: String, out: BufferedWriter) {
        out.write("(\n$command\n) </dev/null")
        out.newLine()
        out.write("__canta_code=\$?")
        out.newLine()
        out.write("echo")
        out.newLine()
        out.write("echo \"$marker\$__canta_code\"")
        out.newLine()
        out.flush()
    }

    /** Reads and discards until [marker]; used when the caller is cancelled mid-command. */
    private fun drainTo(input: BufferedReader, marker: String) {
        while (true) {
            val line = input.readLine() ?: return
            if (line.startsWith(marker)) return
        }
    }

    /** Runs [command] and answers its exit code plus everything it printed (stdout only). */
    suspend fun run(command: String, timeoutMs: Long = DEFAULT_TIMEOUT_MILLIS): ShellResult =
        withContext(Dispatchers.IO) {
            // 类型参数必须写出来：下面那个 `try` 的正常路径全是 `return@withLock`，编译器只把它
            // 当成 Unit，和 `catch` 里的 ShellResult 一合并就推成了 Any，返回处于是报
            // "Return type mismatch: expected 'ShellResult', actual 'Any'"。
            lock.withLock<ShellResult> {
                if (!ensureOpen()) return@withLock ShellResult(-1, "", "root 没有授予 Canta")
                val started = process
                val out = writer
                val input = reader
                if (started == null || out == null || input == null) {
                    return@withLock ShellResult(-1, "", "root 会话已经关闭")
                }
                val marker = "$MARKER${++sequence}_"
                takeStderr()
                val timedOut = AtomicBoolean(false)
                // The read below blocks, so a timeout has to close the pipe rather than wrap the
                // read: a watchdog ends it, and the caller hears "no answer" instead of hanging.
                val watchdog = launch(Dispatchers.IO) {
                    delay(timeoutMs)
                    timedOut.set(true)
                    closeIfSame(started)
                }
                try {
                    writeCommand(command, marker, out)
                    val collected = StringBuilder()
                    while (true) {
                        val line = input.readLine()
                        if (line == null) {
                            val error = takeStderr()
                            close()
                            val reason = when {
                                timedOut.get() -> "root 授权超时或被拒绝"
                                error.isNotBlank() -> error
                                else -> "root 会话断开了"
                            }
                            return@withLock ShellResult(-1, collected.toString(), reason)
                        }
                        if (line.startsWith(marker)) {
                            val code = line.removePrefix(marker).trim()
                                .takeWhile(Char::isDigit).toIntOrNull() ?: -1
                            return@withLock ShellResult(code, collected.toString(), takeStderr())
                        }
                        collected.append(line).append('\n')
                    }
                    // 上面那个循环只从 `return@withLock` 或异常里出来，走不到这儿。
                    // 留着这一行是因为它的类型是 Nothing：有它，`try` 才有「正常结束」的类型，
                    // 和 catch 里的 ShellResult 合起来才是 ShellResult 而不是 Any。
                    @Suppress("UNREACHABLE_CODE")
                    error("root 会话异常结束")
                } catch (e: CancellationException) {
                    // Cancelling a caller is not a reason to lose the shell: the next command would
                    // cost another authorization prompt. The rest of this command is read off first.
                    runCatching { drainTo(input, marker) }
                    throw e
                } catch (e: Exception) {
                    close()
                    ShellResult(-1, "", e.toString())
                } finally {
                    watchdog.cancel()
                }
            }
        }

    /**
     * Runs [command] and emits its output line by line as it arrives, so the caller can show a
     * list filling while the helper is still working.
     *
     * A non-zero exit code is thrown as [ShellCommandException], the same contract the other
     * transports follow: the caller treats a thrown command as "this one failed".
     */
    fun lines(command: String): Flow<String> = flow {
        lock.withLock {
            if (!ensureOpen()) throw ShellCommandException(command, -1, "root 没有授予 Canta")
            val started = process
            val out = writer
            val input = reader
            if (started == null || out == null || input == null) {
                throw ShellCommandException(command, -1, "root 会话已经关闭")
            }
            val marker = "$MARKER${++sequence}_"
            takeStderr()
            val timedOut = AtomicBoolean(false)
            // Same watchdog as [run]: a helper that stops answering must not hold this lock, and
            // with it every other root command in the process, forever.
            //
            // 这里必须给 stack 一个自己的 scope：flow builder 的 lambda 接收者是 FlowCollector，
            // 不是 CoroutineScope，直接写 `launch` 会解析到已经 ERROR 级弃用的那个顶层函数——
            // 编译时就当错误（CI 上正是这么红的）。守门狗本来也要脱离 flow 的生命周期单独跑，
            // 有个自己的 scope 才是它该有的样子；`finally` 里连 scope 一起取消。
            val watchdogScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            val watchdog = watchdogScope.launch {
                delay(DEFAULT_TIMEOUT_MILLIS)
                timedOut.set(true)
                closeIfSame(started)
            }
            try {
                writeCommand(command, marker, out)
                while (true) {
                    val line = input.readLine() ?: run {
                        val error = takeStderr()
                        close()
                        val reason = when {
                            timedOut.get() -> "root 授权超时或被拒绝"
                            error.isNotBlank() -> error
                            else -> "root 会话断开了"
                        }
                        throw ShellCommandException(command, -1, reason)
                    }
                    if (line.startsWith(marker)) {
                        val code = line.removePrefix(marker).trim()
                            .takeWhile(Char::isDigit).toIntOrNull() ?: -1
                        if (code != 0) throw ShellCommandException(command, code, takeStderr())
                        break
                    }
                    emit(line)
                }
            } catch (e: CancellationException) {
                runCatching { drainTo(input, marker) }
                throw e
            } catch (e: ShellCommandException) {
                throw e
            } catch (e: Exception) {
                close()
                throw e
            } finally {
                // 取消 scope 就把守门狗那颗协程一起收了；它自己的 Job 不用再单独 cancel。
                watchdogScope.cancel()
                watchdog.cancel()
            }
        }
    }.buffer(Channel.BUFFERED).flowOn(Dispatchers.IO)
}

/**
 * The process-wide root shell.
 *
 * One instance, not one per caller: two `su` shells would be two prompts, which is the report this
 * class exists to answer.
 */
object RootShell {
    val session: RootShellSession by lazy { RootShellSession() }
}
