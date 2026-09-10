package app.agentterm.sessions

import android.content.Context
import android.system.ErrnoException
import android.system.Os
import app.agentterm.core.terminal.TerminalSession
import app.agentterm.pty.PtyBridge
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Local shell session backed by a real PTY (bundled native bridge).
 * Runs /system/bin/sh with toybox on PATH — no Termux, no root.
 */
class LocalSession(
    context: Context,
    cols: Int,
    rows: Int,
    val name: String = "console",
) : TerminalSession(cols, rows) {

    @Volatile private var fd = -1
    private val closed = AtomicBoolean(false)
    val isRunning: Boolean get() = !closed.get()

    private val homeDir: String = context.filesDir.absolutePath
    private val shellPath: String =
        if (java.io.File("/system/bin/sh").exists()) "/system/bin/sh" else "sh"

    fun start() {
        val envp = arrayOf(
            "TERM=xterm-256color",
            "HOME=$homeDir",
            "PWD=$homeDir",
            "SHELL=$shellPath",
            "USER=u0_a${android.os.Process.myUid()}",
            "LOGNAME=shell",
            "PATH=/system/bin:/vendor/bin:/product/bin:/apex/com.android.runtime/bin:$homeDir/bin",
            "ANDROID_DATA=/data",
            "ANDROID_ROOT=/system",
            "ANDROID_ASSETS=/system/app",
            "TMPDIR=$homeDir/tmp",
            "EXTERNAL_STORAGE=/sdcard",
        )
        fd = PtyBridge.nativeOpen(shellPath, arrayOf(shellPath), envp, screen.columns, screen.rows)
        if (fd < 0) { closed.set(true); onClosed(); return }
        Thread { readLoop() }.apply {
            name = "agentterm-pty-read"
            isDaemon = true
            start()
        }
    }

    private fun readLoop() {
        val buf = ByteArray(16384)
        while (!closed.get()) {
            try {
                val n = Os.read(fd, buf, 0, buf.size)
                if (n <= 0) break
                feed(buf.copyOfRange(0, n))
            } catch (e: ErrnoException) {
                break
            } catch (e: Exception) {
                break
            }
        }
        if (!closed.getAndSet(true)) {
            feed("\r\n[process exited]\r\n".toByteArray())
            onClosed()
        }
    }

    override fun write(bytes: ByteArray) {
        if (closed.get() || fd < 0) return
        try { Os.write(fd, bytes, 0, bytes.size) } catch (_: Exception) {}
    }

    override fun onResize(cols: Int, rows: Int) {
        if (fd >= 0 && !closed.get()) PtyBridge.nativeSetSize(fd, cols, rows)
    }

    override fun close() {
        if (closed.getAndSet(true)) return
        try { if (fd >= 0) PtyBridge.nativeClose(fd) } catch (_: Exception) {}
        fd = -1
        onClosed()
    }

    var onClosed: () -> Unit = {}
}