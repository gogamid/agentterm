package app.agentterm.config

import android.content.Context
import app.agentterm.core.config.AppConfig
import app.agentterm.core.config.SavedShortcut
import app.agentterm.core.config.ConfigCodec
import app.agentterm.core.gestures.Gesture
import app.agentterm.core.gestures.GestureAction
import app.agentterm.core.gestures.GestureBinding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/** Reactive app config backed by SharedPreferences JSON (core ConfigCodec). */
class ConfigRepository(context: Context) {
    private val prefs = context.getSharedPreferences("config", Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(ConfigCodec.decode(prefs.getString("json", null) ?: ""))
    val config: StateFlow<AppConfig> = _config

    private fun update(f: (AppConfig) -> AppConfig) {
        _config.value = f(_config.value)
        prefs.edit().putString("json", ConfigCodec.encode(_config.value)).apply()
    }

    fun bindGesture(g: Gesture, action: GestureAction, shortcutId: String? = null) {
        update { c ->
            val list = c.gestureBindings.filterNot { it.gesture == g } + GestureBinding(g, action, shortcutId)
            c.copy(gestureBindings = list)
        }
    }

    fun resetGestures() {
        update { c -> c.copy(gestureBindings = app.agentterm.core.gestures.DEFAULT_BINDINGS) }
    }

    fun addShortcut(name: String, binding: String, category: String): SavedShortcut {
        val s = SavedShortcut(UUID.randomUUID().toString().substring(0, 8), name, binding, category)
        update { c -> c.copy(shortcuts = c.shortcuts + s) }
        return s
    }

    fun updateShortcut(s: SavedShortcut) {
        update { c -> c.copy(shortcuts = c.shortcuts.map { if (it.id == s.id) s else it }) }
    }

    fun removeShortcut(id: String) {
        update { c -> c.copy(shortcuts = c.shortcuts.filterNot { it.id == id }) }
    }

    fun setFontScale(scale: Float) {
        update { c -> c.copy(fontScale = scale.coerceIn(0.5f, 3f)) }
    }

    fun setToolbarHidden(hidden: Boolean) = update { it.copy(toolbarHidden = hidden) }
    fun setOptionAsMeta(v: Boolean) = update { it.copy(optionAsMeta = v) }
    fun setAutoHideToolbar(v: Boolean) = update { it.copy(autoHideToolbar = v) }
}