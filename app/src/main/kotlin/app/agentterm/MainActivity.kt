package app.agentterm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.agentterm.ui.home.ConnectionEditor
import app.agentterm.ui.home.HomeScreen
import app.agentterm.ui.settings.GestureSettingsScreen
import app.agentterm.ui.settings.SettingsScreen
import app.agentterm.ui.settings.ShortcutsScreen
import app.agentterm.ui.terminal.TerminalScreen
import app.agentterm.ui.theme.AgentTermTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AgentTermTheme {
                AppRoot()
            }
        }
    }
}

sealed class Screen {
    data object Home : Screen()
    data class Terminal(val sessionId: String) : Screen()
    data object Settings : Screen()
    data object Gestures : Screen()
    data object Shortcuts : Screen()
    data class EditConnection(val id: String?) : Screen()
}

/** rememberSaveable needs a Saver for non-Bundle types or it throws at registration. */
val ScreenSaver: Saver<Screen, String> = Saver(
    save = { screen ->
        when (screen) {
            Screen.Home -> "home"
            Screen.Settings -> "settings"
            Screen.Gestures -> "gestures"
            Screen.Shortcuts -> "shortcuts"
            is Screen.Terminal -> "terminal:${screen.sessionId}"
            is Screen.EditConnection -> "edit:${screen.id ?: ""}"
        }
    },
    restore = { raw ->
        when {
            raw == "home" -> Screen.Home
            raw == "settings" -> Screen.Settings
            raw == "gestures" -> Screen.Gestures
            raw == "shortcuts" -> Screen.Shortcuts
            raw.startsWith("terminal:") -> Screen.Terminal(raw.removePrefix("terminal:"))
            raw.startsWith("edit:") -> Screen.EditConnection(raw.removePrefix("edit:").ifEmpty { null })
            else -> Screen.Home
        }
    },
)

@Composable
fun AppRoot() {
    var screen by rememberSaveable(stateSaver = ScreenSaver) { mutableStateOf<Screen>(Screen.Home) }

    fun go(s: Screen) { screen = s }

    when (val s = screen) {
        is Screen.Home -> HomeScreen(
            backToEditor = { id -> go(Screen.EditConnection(id)) },
            openTerminal = { id -> go(Screen.Terminal(id)) },
            openSettings = { go(Screen.Settings) },
        )
        is Screen.Terminal -> TerminalScreen(
            sessionId = s.sessionId,
            onBack = { go(Screen.Home) },
            openSettings = { go(Screen.Settings) },
            openGestures = { go(Screen.Gestures) },
        )
        is Screen.Settings -> SettingsScreen(onBack = { go(Screen.Home) }, onGestures = { go(Screen.Gestures) }, onShortcuts = { go(Screen.Shortcuts) })
        is Screen.Gestures -> GestureSettingsScreen(onBack = { go(Screen.Settings) })
        is Screen.Shortcuts -> ShortcutsScreen(onBack = { go(Screen.Settings) })
        is Screen.EditConnection -> ConnectionEditor(
            connectionId = s.id,
            onDone = { go(Screen.Home) },
        )
    }
}