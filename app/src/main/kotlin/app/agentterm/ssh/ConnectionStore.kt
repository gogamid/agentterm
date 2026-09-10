package app.agentterm.ssh

import android.content.Context
import app.agentterm.core.config.ConfigCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

enum class AuthType { PASSWORD, KEY }

@Serializable
data class SavedConnection(
    val id: String,
    val name: String,
    val host: String,
    val port: Int = 22,
    val username: String,
    val authType: AuthType = AuthType.PASSWORD,
    val secretRef: String = "",          // SecureStore blob id (password or key)
    val keyType: String = "",            // e.g. "ssh-ed25519"
    val keyFingerprint: String = "",     // exposed fingerprint text
    val keyComment: String = "",
    val createdAt: Long = 0,
) {
    val label: String get() = if (name.isNotBlank()) name else "$username@$host"
}

@Serializable
data class ConnectionList(val items: List<SavedConnection> = emptyList())

/** Persists non-secret connection metadata as JSON; secrets stay in [SecureStore]. */
class ConnectionStore(context: Context) {
    private val prefs = context.getSharedPreferences("connections", Context.MODE_PRIVATE)
    private val json = ConfigCodec.json

    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<SavedConnection>> = _items

    private fun load(): List<SavedConnection> {
        val raw = prefs.getString("list", null) ?: return emptyList()
        return try { json.decodeFromString(ConnectionList.serializer(), raw).items }
        catch (e: Exception) { emptyList() }
    }

    private fun persist() {
        prefs.edit().putString("list", json.encodeToString(ConnectionList.serializer(), ConnectionList(_items.value))).apply()
    }

    fun upsert(c: SavedConnection) {
        val list = _items.value.toMutableList()
        val i = list.indexOfFirst { it.id == c.id }
        if (i >= 0) list[i] = c else list.add(c)
        _items.value = list
        persist()
    }

    fun delete(id: String) {
        _items.value = _items.value.filterNot { it.id == id }
        persist()
    }

    fun get(id: String): SavedConnection? = _items.value.firstOrNull { it.id == id }
    fun newId(): String = "c${System.currentTimeMillis()}"
}