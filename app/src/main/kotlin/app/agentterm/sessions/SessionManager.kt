package app.agentterm.sessions

import android.content.Context
import app.agentterm.core.terminal.TerminalSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

data class SessionHandle(
    val id: String,
    val title: String,
    val kind: Kind,
    val session: TerminalSession,
    val onUpdate: () -> Unit,
) {
    enum class Kind { LOCAL, SSH }
}

/**
 * Owns all live terminal sessions. The UI observes [active] and [sessions].
 * Session output updates are delivered to the owning screen through
 * TerminalView's requestInvalidate hook (set in [onUpdate]).
 */
class SessionManager(private val context: Context) {

    private val _sessions = MutableStateFlow<List<SessionHandle>>(emptyList())
    val sessions: StateFlow<List<SessionHandle>> = _sessions

    private val _activeId = MutableStateFlow<String?>(null)
    val activeId: StateFlow<String?> = _activeId

    fun startLocal(cols: Int = 80, rows: Int = 24, title: String = "console"): SessionHandle {
        val s = LocalSession(context, cols, rows, title)
        val id = UUID.randomUUID().toString().substring(0, 8)
        val h = SessionHandle(id, title, SessionHandle.Kind.LOCAL, s, {})
        s.onClosed = { remove(id) }
        add(h)
        s.start()
        return h
    }

    fun startSsh(conn: app.agentterm.ssh.SavedConnection, cols: Int = 80, rows: Int = 24): SessionHandle {
        val s = SshSession(context, cols, rows, conn)
        val id = UUID.randomUUID().toString().substring(0, 8)
        val h = SessionHandle(id, conn.label, SessionHandle.Kind.SSH, s, {})
        s.onClosed = { remove(id) }
        add(h)
        s.connect { err ->
            android.widget.Toast.makeText(context, "SSH: $err", android.widget.Toast.LENGTH_LONG).show()
            android.util.Log.e("agentterm", "ssh error: $err")
        }
        return h
    }

    private fun add(h: SessionHandle) {
        _sessions.value = _sessions.value + h
        _activeId.value = h.id
    }

    fun remove(id: String) {
        val h = _sessions.value.firstOrNull { it.id == id } ?: return
        h.session.close()
        _sessions.value = _sessions.value.filterNot { it.id == id }
        if (_activeId.value == id) _activeId.value = _sessions.value.lastOrNull()?.id
    }

    fun closeAll() { _sessions.value.forEach { runCatching { it.session.close() } } }

    fun get(id: String?): SessionHandle? = _sessions.value.firstOrNull { it.id == id }
    fun setActive(id: String) { _activeId.value = id }
}