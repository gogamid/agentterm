package app.agentterm.core.config

import app.agentterm.core.gestures.GestureBinding
import app.agentterm.core.gestures.GestureMap
import app.agentterm.core.shortcuts.KeyEmitter
import app.agentterm.core.shortcuts.KeyChord
import app.agentterm.core.shortcuts.Shortcut
import app.agentterm.core.shortcuts.ShortcutParser
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** All user configuration, serialized to one JSON document (SharedPreferences-backed on Android). */
@Serializable
data class AppConfig(
    val gestureBindings: List<GestureBinding> = app.agentterm.core.gestures.DEFAULT_BINDINGS,
    val shortcuts: List<SavedShortcut> = defaultShortcuts,
    val fontScale: Float = 1f,
    val fontFamily: String = "mono",
    val toolbarHidden: Boolean = false,
    val optionAsMeta: Boolean = false,
    val autoHideToolbar: Boolean = true,
)

@Serializable
data class SavedShortcut(
    val id: String,
    val name: String,
    val binding: String,          // advanced grammar string, e.g. "C-b, T" or "text:/clear"
    val category: String = "General",
    val autoEnter: Boolean = false,
) {
    fun toShortcut(): Shortcut {
        val steps = try { ShortcutParser.parse(binding) } catch (e: Exception) { listOf(KeyChord(KeyChord.Mods(), binding)) }
        return Shortcut(name, binding, steps, category)
    }
}

val defaultShortcuts = listOf(
    SavedShortcut("tmux-prev", "tmux: previous window", "C-b, p", "tmux"),
    SavedShortcut("tmux-next", "tmux: next window", "C-b, n", "tmux"),
    SavedShortcut("tmux-new", "tmux: new window", "C-b, c", "tmux"),
    SavedShortcut("tmux-zoom", "tmux: zoom pane", "C-b, z", "tmux"),
    SavedShortcut("tmux-pane-prev", "tmux: previous pane", "C-b, ;", "tmux"),
    SavedShortcut("tmux-pane-next", "tmux: next pane", "C-b, o", "tmux"),
    SavedShortcut("tmux-session-prev", "tmux: previous session", "C-b, (", "tmux"),
    SavedShortcut("tmux-session-next", "tmux: next session", "C-b, )", "tmux"),
    SavedShortcut("codex", "codex: start agent", "text:codex", "agents"),
    SavedShortcut("opencode", "opencode: start agent", "text:opencode", "agents"),
    SavedShortcut("pi", "pi: start agent", "text:pi", "agents"),
    SavedShortcut("herdr-at", "herdr: attach", "text:herdr attach", "agents"),
    SavedShortcut("clear-cmd", "clear", "text:clear\n", "shell"),
    SavedShortcut("git-status", "git status", "text:git status\n", "shell"),
    SavedShortcut("interrupt", "Interrupt", "C-c", "General"),
)

/** Config store helper: JSON load/save with tolerant parsing. */
object ConfigCodec {
    val json: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(cfg: AppConfig): String = json.encodeToString(AppConfig.serializer(), cfg)

    fun decode(raw: String): AppConfig = try {
        json.decodeFromString(AppConfig.serializer(), raw)
    } catch (e: Exception) {
        AppConfig()
    }
}

fun AppConfig.gestureMap(): GestureMap = GestureMap(gestureBindings)

fun AppConfig.shortcutById(id: String?): SavedShortcut? = shortcuts.firstOrNull { it.id == id }

/** Convenience: resolve a SavedShortcut to raw bytes to send. */
fun SavedShortcut.bytes(appCursorKeys: Boolean): ByteArray {
    val sb = java.io.ByteArrayOutputStream()
    for (chord in toShortcut().steps) sb.write(KeyEmitter.emit(chord, appCursorKeys))
    return sb.toByteArray()
}