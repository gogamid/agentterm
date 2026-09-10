package app.agentterm.ui.terminal

import android.view.KeyEvent
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.agentterm.App
import app.agentterm.core.config.bytes
import app.agentterm.core.config.gestureMap
import app.agentterm.core.gestures.Gesture
import app.agentterm.core.gestures.GestureAction
import app.agentterm.sessions.SessionHandle
import app.agentterm.sessions.SshSession
import app.agentterm.ui.theme.Accent
import app.agentterm.ui.theme.Bg
import app.agentterm.ui.theme.Border
import app.agentterm.ui.theme.Danger
import app.agentterm.ui.theme.Panel
import app.agentterm.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun TerminalScreen(
    sessionId: String,
    onBack: () -> Unit,
    openSettings: () -> Unit,
    openGestures: () -> Unit,
) {
    val app = App.instance
    val context = LocalContext.current
    val handle = remember(sessionId) { app.sessions.get(sessionId) }
    val session = handle?.session

    var exited by remember { mutableStateOf(false) }
    LaunchedEffect(handle) { if (handle == null) { onBack() } }
    if (handle == null || session == null) return

    var kbVisible by remember { mutableStateOf(true) }
    var dpadVisible by remember { mutableStateOf(true) }
    var shortcutsPanel by remember { mutableStateOf(false) }
    var pickerVisible by remember { mutableStateOf(false) }
    var agentPalette by remember { mutableStateOf(false) }
    var headerMenu by remember { mutableStateOf(false) }
    var fontScale by remember { mutableStateOf(app.config.config.value.fontScale) }
    val cfg by app.config.config.collectAsState()

    val terminalView = remember { TerminalView(context) }

    LaunchedEffect(handle.id) {
        handle.session.onBufferUpdated = { terminalView.requestInvalidate() }
        terminalView.screen = handle.session.screen
    }

    val controller = remember(handle.id) {
        TerminalActions(
            context = context,
            session = { handle.session },
            config = { app.config.config.value },
            onUi = { a ->
                when (a) {
                    TerminalActions.UiAction.TOGGLE_KEYBOARD -> kbVisible = !kbVisible
                    TerminalActions.UiAction.SHOW_KEYBOARD -> kbVisible = true
                    TerminalActions.UiAction.HIDE_KEYBOARD -> kbVisible = false
                    TerminalActions.UiAction.TOGGLE_DPAD -> dpadVisible = !dpadVisible
                    TerminalActions.UiAction.TOGGLE_SHORTCUTS -> shortcutsPanel = !shortcutsPanel
                    TerminalActions.UiAction.OPEN_PICKER -> pickerVisible = true
                    TerminalActions.UiAction.OPEN_AGENT_PALETTE -> agentPalette = true
                    TerminalActions.UiAction.FONT_UP -> { fontScale = (fontScale + 0.1f).coerceAtMost(2.4f); app.config.setFontScale(fontScale) }
                    TerminalActions.UiAction.FONT_DOWN -> { fontScale = (fontScale - 0.1f).coerceAtLeast(0.6f); app.config.setFontScale(fontScale) }
                    TerminalActions.UiAction.CLOSE_SESSION -> { exited = true; app.sessions.remove(handle.id); onBack() }
                }
            },
        )
    }

    fun dispatch(g: Gesture) = controller.dispatch(app.config.config.value.gestureMap().binding(g))

    val gestureHost = remember(handle.id) { GestureHost(terminalView) { controller.dispatch(app.config.config.value.gestureMap().binding(it)) } }
    terminalView.setOnTouchListener(gestureHost)
    terminalView.fontScale = fontScale
    terminalView.onCharInput = { ch -> session.write(ch.toString()) }
    terminalView.onKeyInput = { ev -> handleHardwareKey(ev, handle, app, onBack, { pickerVisible = true }, { shortcutsPanel = !shortcutsPanel }) }
    terminalView.onGridSizeChange = { c, r -> session.resize(c, r) }

    LaunchedEffect(kbVisible) {
        if (kbVisible) KeyboardUtil.show(terminalView) else KeyboardUtil.hide(terminalView)
    }
    LaunchedEffect(fontScale) { terminalView.fontScale = fontScale }

    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.fillMaxSize()) {
            // Header with pull-down gestures
            TerminalHeader(
                title = handle.title,
                status = if (handle.kind == SessionHandle.Kind.SSH) "ssh" else "local",
                menuOpen = headerMenu,
                onMenuChange = { headerMenu = it },
                onBack = { onBack() },
                onSoftPull = { pickerVisible = true },
                onHardPull = {
                    if (!exited) { exited = true; app.sessions.remove(handle.id); onBack() }
                },
                menuItems = listOf(
                    HeaderMenuItem("Session picker") { pickerVisible = true },
                    HeaderMenuItem("Agent palette") { agentPalette = true },
                    HeaderMenuItem("Shortcuts") { shortcutsPanel = !shortcutsPanel },
                    HeaderMenuItem("Gestures…") { openGestures() },
                    HeaderMenuItem("Settings…") { openSettings() },
                    HeaderMenuItem("Close session") {
                        if (!exited) { exited = true; app.sessions.remove(handle.id); onBack() }
                    },
                ),
            )

            AndroidView(
                factory = { terminalView },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }

        // D-pad overlay (bottom-right, above toolbar)
        if (dpadVisible) {
            DpadOverlay(
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 92.dp, end = 14.dp),
                onArrow = { dir ->
                    val seq = when (dir) {
                        "up" -> if (session.screen.appCursorKeys) "\u001BOA" else "\u001B[A"
                        "down" -> if (session.screen.appCursorKeys) "\u001BOB" else "\u001B[B"
                        "left" -> if (session.screen.appCursorKeys) "\u001BOD" else "\u001B[D"
                        else -> if (session.screen.appCursorKeys) "\u001BOC" else "\u001B[C"
                    }
                    session.write(seq)
                },
                onCornerTap = { which ->
                    if (which == "tl") dispatch(Gesture.DPAD_TL_TAP) else dispatch(Gesture.DPAD_TR_TAP)
                },
            )
        }

        // Toolbar
        TerminalToolbar(
            modifier = Modifier.align(Alignment.BottomCenter),
            ctrlLocked = false,
            onEnter = { session.write("\r") },
            onBackspace = { session.write("\u007F") },
            onEsc = { dispatch(Gesture.ESC_TAP) },
            onTab = { dispatch(Gesture.TAB_TAP) },
            onPaste = { dispatch(Gesture.PASTE_TAP) },
            onKeyboard = { dispatch(Gesture.KB_TAP) },
            onShortcuts = { dispatch(Gesture.SHORTCUTS_TAP) },
            onDpad = { dpadVisible = !dpadVisible },
        )
    }

    if (shortcutsPanel) {
        ShortcutSheet(cfg) { s ->
            session.write(s.bytes(session.screen.appCursorKeys))
            shortcutsPanel = false
        }
    }

    if (pickerVisible) {
        SessionPickerSheet(
            app = app,
            currentId = handle.id,
            multiplexers = (handle.session as? SshSession)?.multiplexers?.toList() ?: emptyList(),
            onSwitch = { id -> app.sessions.setActive(id); pickerVisible = false },
            onAttachItem = { cmd ->
                session.write("$cmd\n")
                pickerVisible = false
            },
            onNewConsole = {
                val h = app.sessions.startLocal()
                pickerVisible = false
                onBack()
                // switch happens from home
            },
            onDismiss = { pickerVisible = false },
        )
    }

    if (agentPalette) {
        AgentPaletteSheet(
            session = session,
            onDismiss = { agentPalette = false },
        )
    }

    if (exited) LaunchedEffect(Unit) { app.sessions.remove(handle.id) }
}

// ---------------------------------------------------------------------------

private fun handleHardwareKey(
    ev: KeyEvent,
    handle: SessionHandle,
    app: App,
    onBack: () -> Unit,
    openPicker: () -> Unit,
    toggleShortcuts: () -> Unit,
) {
    if (ev.action != KeyEvent.ACTION_DOWN) return
    val cmd = (ev.metaState and (KeyEvent.META_CTRL_ON or KeyEvent.META_META_ON)) != 0
    if (!cmd) {
        handle.session.write(ev.unicodeChar?.let { it.toString() } ?: return)
        return
    }
    when (ev.keyCode) {
        KeyEvent.KEYCODE_K -> toggleShortcuts()
        KeyEvent.KEYCODE_V -> {
            val ctx = app.applicationContext
            val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val t = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString() ?: return
            handle.session.write(if (handle.session.screen.bracketedPaste) "\u001B[200~$t\u001B[201~" else t)
        }
        KeyEvent.KEYCODE_O -> openPicker()
        KeyEvent.KEYCODE_W -> onBack()
        KeyEvent.KEYCODE_1 -> switchSession(handle, app, 0)
        KeyEvent.KEYCODE_2 -> switchSession(handle, app, 1)
        KeyEvent.KEYCODE_3 -> switchSession(handle, app, 2)
        KeyEvent.KEYCODE_4 -> switchSession(handle, app, 3)
        KeyEvent.KEYCODE_5 -> switchSession(handle, app, 4)
        KeyEvent.KEYCODE_6 -> switchSession(handle, app, 5)
        KeyEvent.KEYCODE_7 -> switchSession(handle, app, 6)
        KeyEvent.KEYCODE_8 -> switchSession(handle, app, 7)
        KeyEvent.KEYCODE_9 -> switchSession(handle, app, 8)
    }
}

private fun switchSession(handle: SessionHandle, app: App, index: Int) {
    val sessions = app.sessions.sessions.value
    if (index < sessions.size) app.sessions.setActive(sessions[index].id)
    else app.sessions.setActive(handle.id)
}

// ---------------------------------------------------------------------------

data class HeaderMenuItem(val label: String, val action: () -> Unit)

@Composable
fun TerminalHeader(
    title: String,
    status: String,
    menuOpen: Boolean,
    onMenuChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onSoftPull: () -> Unit,
    onHardPull: () -> Unit,
    menuItems: List<HeaderMenuItem>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Panel)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // soft/hard pull-down gestures live on the title area (mirrors Moshi's header)
        Column(
            Modifier
                .weight(1f)
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onVerticalDrag = { _, dy -> total += dy },
                        onDragEnd = {
                            if (total > 300f) onHardPull()
                            else if (total > 80f) onSoftPull()
                        },
                        onDragCancel = {},
                    )
                },
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(status, style = MaterialTheme.typography.bodySmall, color = if (status == "ssh") Color(0xFF7C3AED) else Accent)
        }
        Text("\u25BC", color = TextSecondary, fontSize = 14.sp, modifier = Modifier.clickable { onSoftPull() }.padding(6.dp))
        Text("\u22EF", color = TextSecondary, fontSize = 18.sp, modifier = Modifier.clickable { onMenuChange(!menuOpen) }.padding(6.dp))
        Box {
            DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenuChange(false) }) {
                menuItems.forEach { mi ->
                    DropdownMenuItem(text = { Text(mi.label) }, onClick = { onMenuChange(false); mi.action() })
                }
            }
        }
    }
}

@Composable
private fun TerminalToolbar(
    modifier: Modifier = Modifier,
    ctrlLocked: Boolean,
    onEnter: () -> Unit,
    onBackspace: () -> Unit,
    onEsc: () -> Unit,
    onTab: () -> Unit,
    onPaste: () -> Unit,
    onKeyboard: () -> Unit,
    onShortcuts: () -> Unit,
    onDpad: () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Panel)
            .border(1.dp, Border)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolbarButton("Esc") { onEsc() }
        ToolbarButton("Tab") { onTab() }
        ToolbarButton("↵") { onEnter() }
        ToolbarButton("⌫") { onBackspace() }
        ToolbarButton("Paste") { onPaste() }
        ToolbarButton("⌨") { onKeyboard() }
        ToolbarButton("⚡") { onShortcuts() }
        ToolbarButton("◉", selected = true) { onDpad() }
    }
}

@Composable
private fun ToolbarButton(label: String, selected: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Accent.copy(alpha = 0.15f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = if (selected) Accent else MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
    }
}

@Composable
private fun DpadOverlay(
    modifier: Modifier = Modifier,
    onArrow: (String) -> Unit,
    onCornerTap: (String) -> Unit,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CornerSlot("×", Danger) { onCornerTap("tl") }
            DpadButton("▲") { onArrow("up") }
            CornerSlot("⌫", TextSecondary) { onCornerTap("tr") }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DpadButton("◀") { onArrow("left") }
            Spacer(Modifier.size(34.dp))
            DpadButton("▶") { onArrow("right") }
        }
        DpadButton("▼") { onArrow("down") }
    }
}

@Composable
private fun DpadButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .background(Color(0xE61B1F29), CircleShape)
            .border(1.dp, Border, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp) }
}

@Composable
private fun CornerSlot(label: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(26.dp)
            .background(color.copy(alpha = 0.12f), CircleShape)
            .border(1.dp, color.copy(alpha = 0.5f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = color, fontSize = 10.sp) }
}

// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShortcutSheet(cfg: app.agentterm.core.config.AppConfig, onSend: (app.agentterm.core.config.SavedShortcut) -> Unit) {
    ModalBottomSheet(onDismissRequest = {}, containerColor = Panel) {
        Column(
            Modifier.fillMaxWidth().padding(bottom = 18.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Shortcuts", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 18.dp))
            Text("tap a shortcut to send it", style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.padding(horizontal = 18.dp))
            Spacer(Modifier.height(6.dp))
            cfg.shortcuts.groupBy { it.category }.forEach { (cat, list) ->
                Text(cat, style = MaterialTheme.typography.labelMedium, color = TextSecondary, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp))
                list.forEach { sc ->
                    Card(
                        Modifier.fillMaxWidth().padding(horizontal = 10.dp).clickable { onSend(sc) },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = app.agentterm.ui.theme.BgElevated),
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(sc.name, style = MaterialTheme.typography.bodyMedium)
                                Text(sc.binding, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                            Text("send ›", color = Accent, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionPickerSheet(
    app: App,
    currentId: String,
    multiplexers: List<String>,
    onSwitch: (String) -> Unit,
    onAttachItem: (String) -> Unit,
    onNewConsole: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sessions by app.sessions.sessions.collectAsState()
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Panel) {
        Column(Modifier.fillMaxWidth().padding(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Sessions", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 18.dp))
            Spacer(Modifier.height(4.dp))
            sessions.forEach { h ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (h.id == currentId) Accent.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable { onSwitch(h.id) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(h.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(if (h.kind == SessionHandle.Kind.SSH) "ssh" else "local", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (multiplexers.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("Detected on host", style = MaterialTheme.typography.labelMedium, color = TextSecondary, modifier = Modifier.padding(horizontal = 18.dp))
                Text(
                    "tmux: " + multiplexers.filter { it != "tmux" }.joinToString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
                if (multiplexers.contains("tmux")) {
                    TextButton(onClick = { onAttachItem("tmux attach -t default") }, modifier = Modifier.padding(horizontal = 10.dp)) {
                        Text("Attach tmux session")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onNewConsole, modifier = Modifier.padding(horizontal = 10.dp)) {
                Text("＋ New console")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgentPaletteSheet(
    session: app.agentterm.core.terminal.TerminalSession,
    onDismiss: () -> Unit,
) {
    val agents = mapOf(
        "codex" to "codex",
        "opencode" to "opencode",
        "pi" to "pi",
        "claude" to "claude",
        "herdr attach" to "herdr attach",
    )
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Panel) {
        Column(Modifier.fillMaxWidth().padding(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Agent palette", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 18.dp))
            Text("launch or attach on the host", style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.padding(horizontal = 18.dp))
            Spacer(Modifier.height(4.dp))
            agents.forEach { (label, cmd) ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Accent.copy(alpha = 0.08f))
                        .clickable { session.write("$cmd\n"); onDismiss() },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text("run ›", color = Accent, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
