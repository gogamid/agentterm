package app.agentterm.sessions

import android.content.Context
import app.agentterm.core.terminal.TerminalSession
import app.agentterm.ssh.AuthType
import app.agentterm.ssh.SavedConnection
import app.agentterm.ssh.SecureStore
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.userauth.keyprovider.OpenSSHKeyFile
import java.io.File
import java.security.PublicKey
import java.util.concurrent.atomic.AtomicBoolean

class SshConnectException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * SSH terminal session via sshj (Apache-2.0, pure Java). Password and key auth,
 * PTY allocation, then a bidirectional byte pump exactly like a local session.
 */
class SshSession(
    context: Context,
    cols: Int,
    rows: Int,
    private val conn: SavedConnection,
) : TerminalSession(cols, rows) {

    private val appContext = context.applicationContext
    private val secureStore = SecureStore()
    private val closed = AtomicBoolean(false)
    private var client: SSHClient? = null
    private var shellOut: java.io.OutputStream? = null
    val isRunning: Boolean get() = !closed.get()

    /** Runs on a background thread; errors surface via [onError]. */
    fun connect(onError: (String) -> Unit = {}) {
        Thread {
            try {
                val c = SSHClient()
                client = c
                // MVP: accept-all host key verification with visual indicator; TOFU is roadmap.
                c.addHostKeyVerifier(object : HostKeyVerifier {
                    override fun verify(hostname: String?, port: Int, key: PublicKey?): Boolean = true
                    override fun findExistingAlgorithms(hostname: String?, port: Int): List<String> = emptyList()
                })
                c.connect(conn.host, conn.port)
                when (conn.authType) {
                    AuthType.PASSWORD -> {
                        val pass = secureStore.decrypt(conn.secretRef)
                            ?: throw SshConnectException("Saved password unavailable")
                        c.authPassword(conn.username, pass)
                    }
                    AuthType.KEY -> {
                        val keyPem = secureStore.decrypt(conn.secretRef)
                            ?: throw SshConnectException("Saved key unavailable")
                        val keyFile = File(appContext.cacheDir, "key_${conn.id}.pem")
                        keyFile.writeText(keyPem)
                        keyFile.setReadable(true, true)
                        val kp = OpenSSHKeyFile()
                        kp.init(keyFile, null)
                        c.authPublickey(conn.username, kp)
                        keyFile.delete()
                    }
                }

                val session = c.startSession()
                session.allocatePTY("xterm-256color", screen.columns, screen.rows, 0, 0, emptyMap())
                val shell = session.startShell()
                shellOut = shell.outputStream

                // multiplexer preflight probe (non-blocking)
                runCatching { probeMultiplexers(c) }

                val inp = shell.inputStream
                val buf = ByteArray(16384)
                while (!closed.get()) {
                    val n = inp.read(buf)
                    if (n <= 0) break
                    feed(buf.copyOfRange(0, n))
                }
            } catch (e: Exception) {
                if (!closed.get()) onError(e.message ?: "SSH error")
            } finally {
                if (!closed.getAndSet(true)) onClosed()
                runCatching { client?.disconnect() }
            }
        }.apply {
            name = "agentterm-ssh-${conn.id}"
            isDaemon = true
            start()
        }
    }

    /** Quick probe of tmux/herdr/zellij presence + running tmux sessions. */
    val multiplexers = ArrayList<String>()

    private fun probeMultiplexers(c: SSHClient) {
        val sess: Session = c.startSession()
        val cmd = sess.exec(
            "export PATH=\$PATH:/usr/local/bin:/opt/homebrew/bin;" +
                "for x in tmux herdr zellij; do command -v \$x >/dev/null 2>&1 && echo \$x; done;" +
                "command -v tmux >/dev/null 2>&1 && tmux ls -F '#{session_name}' 2>/dev/null | cut -d: -f1 | head -20"
        )
        val out = cmd.inputStream.bufferedReader().readText()
        cmd.close()
        multiplexers.addAll(out.lines().filter { it.isNotBlank() }.distinct())
        sess.close()
    }

    override fun write(bytes: ByteArray) {
        val out = shellOut ?: return
        try { out.write(bytes); out.flush() } catch (_: Exception) {}
    }

    override fun onResize(cols: Int, rows: Int) {
        // MVP: sshj window-change on next write; full PTY resize is roadmap.
    }

    override fun close() {
        if (closed.getAndSet(true)) return
        runCatching { client?.disconnect() }
        onClosed()
    }

    var onClosed: () -> Unit = {}
}