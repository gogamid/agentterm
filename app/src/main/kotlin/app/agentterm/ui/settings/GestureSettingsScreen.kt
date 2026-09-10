package app.agentterm.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import app.agentterm.App
import app.agentterm.core.config.AppConfig
import app.agentterm.core.config.shortcutById
import app.agentterm.core.gestures.DEFAULT_BINDINGS
import app.agentterm.core.gestures.Gesture
import app.agentterm.core.gestures.GestureAction
import app.agentterm.ui.theme.Accent
import app.agentterm.ui.theme.Bg
import app.agentterm.ui.theme.BgElevated
import app.agentterm.ui.theme.Border
import app.agentterm.ui.theme.Danger
import app.agentterm.ui.theme.Panel
import app.agentterm.ui.theme.TextPrimary
import app.agentterm.ui.theme.TextSecondary

// -- surface groups (mirrors the taxonomy comments in Gestures.kt) --------------
private const val SURFACE_BODY = "Terminal body"
private const val SURFACE_HEADER = "Terminal header"
private const val SURFACE_TOOLBAR = "Toolbar buttons"
private const val SURFACE_DPAD = "D-pad slots"
private const val SURFACE_SPECIAL = "Special"

private val SURFACE_ORDER = listOf(SURFACE_BODY, SURFACE_HEADER, SURFACE_TOOLBAR, SURFACE_DPAD, SURFACE_SPECIAL)

/** Default action label per gesture, taken from the shipped DEFAULT_BINDINGS. */
private val DEFAULT_ACTION_LABELS: Map<Gesture, String> =
    DEFAULT_BINDINGS.associate { it.gesture to it.action.label }

private data class GestureRowMeta(val group: String, val label: String, val defaultLabel: String)

private fun gestureMeta(g: Gesture): GestureRowMeta = GestureRowMeta(
    group = when (g) {
        Gesture.TAP, Gesture.DOUBLE_TAP, Gesture.TRIPLE_TAP,
        Gesture.SWIPE_LEFT, Gesture.SWIPE_RIGHT, Gesture.SWIPE_UP, Gesture.SWIPE_DOWN,
        Gesture.TWO_FINGER_SWIPE_LEFT, Gesture.TWO_FINGER_SWIPE_RIGHT,
        Gesture.TWO_FINGER_SWIPE_UP, Gesture.TWO_FINGER_SWIPE_DOWN,
        Gesture.PINCH_IN, Gesture.PINCH_OUT, Gesture.SCROLL_PAST_BOTTOM -> SURFACE_BODY

        Gesture.HEADER_SOFT_PULL, Gesture.HEADER_HARD_PULL -> SURFACE_HEADER

        Gesture.KB_TAP, Gesture.KB_LONG_PRESS, Gesture.KB_DOUBLE_TAP,
        Gesture.PASTE_TAP, Gesture.PASTE_LONG_PRESS,
        Gesture.ESC_TAP, Gesture.ESC_LONG_PRESS,
        Gesture.TAB_TAP, Gesture.TAB_LONG_PRESS,
        Gesture.CTRL_TAP, Gesture.CTRL_DOUBLE_TAP,
        Gesture.SHORTCUTS_TAP, Gesture.SHORTCUTS_DOUBLE_TAP -> SURFACE_TOOLBAR

        Gesture.DPAD_TL_TAP, Gesture.DPAD_TL_LONG_PRESS,
        Gesture.DPAD_TR_TAP, Gesture.DPAD_TR_LONG_PRESS -> SURFACE_DPAD

        Gesture.HARDWARE_ANY -> SURFACE_SPECIAL
    },
    label = when (g) {
        Gesture.TAP -> "Tap"
        Gesture.DOUBLE_TAP -> "Double tap"
        Gesture.TRIPLE_TAP -> "Triple tap"
        Gesture.SWIPE_LEFT -> "Swipe left"
        Gesture.SWIPE_RIGHT -> "Swipe right"
        Gesture.SWIPE_UP -> "Swipe up"
        Gesture.SWIPE_DOWN -> "Swipe down"
        Gesture.TWO_FINGER_SWIPE_LEFT -> "Two-finger swipe left"
        Gesture.TWO_FINGER_SWIPE_RIGHT -> "Two-finger swipe right"
        Gesture.TWO_FINGER_SWIPE_UP -> "Two-finger swipe up"
        Gesture.TWO_FINGER_SWIPE_DOWN -> "Two-finger swipe down"
        Gesture.PINCH_IN -> "Pinch in"
        Gesture.PINCH_OUT -> "Pinch out"
        Gesture.SCROLL_PAST_BOTTOM -> "Scroll past bottom"
        Gesture.HEADER_SOFT_PULL -> "Header soft pull"
        Gesture.HEADER_HARD_PULL -> "Header hard pull"
        Gesture.KB_TAP -> "Keyboard: tap"
        Gesture.KB_LONG_PRESS -> "Keyboard: long press"
        Gesture.KB_DOUBLE_TAP -> "Keyboard: double tap"
        Gesture.PASTE_TAP -> "Paste button: tap"
        Gesture.PASTE_LONG_PRESS -> "Paste button: long press"
        Gesture.ESC_TAP -> "Esc button: tap"
        Gesture.ESC_LONG_PRESS -> "Esc button: long press"
        Gesture.TAB_TAP -> "Tab button: tap"
        Gesture.TAB_LONG_PRESS -> "Tab button: long press"
        Gesture.CTRL_TAP -> "Ctrl button: tap"
        Gesture.CTRL_DOUBLE_TAP -> "Ctrl button: double tap"
        Gesture.SHORTCUTS_TAP -> "Shortcuts button: tap"
        Gesture.SHORTCUTS_DOUBLE_TAP -> "Shortcuts button: double tap"
        Gesture.DPAD_TL_TAP -> "D-pad top-left: tap"
        Gesture.DPAD_TL_LONG_PRESS -> "D-pad top-left: long press"
        Gesture.DPAD_TR_TAP -> "D-pad top-right: tap"
        Gesture.DPAD_TR_LONG_PRESS -> "D-pad top-right: long press"
        Gesture.HARDWARE_ANY -> "Hardware key"
    },
    defaultLabel = DEFAULT_ACTION_LABELS[g] ?: "No-op",
)

private fun currentActionLabel(cfg: AppConfig, g: Gesture): String {
    val b = cfg.gestureBindings.firstOrNull { it.gesture == g } ?: return GestureAction.NOOP.label
    if (b.action != GestureAction.SEND_SHORTCUT) return b.action.label
    val sc = cfg.shortcutById(b.shortcutId)
    return if (sc != null) "${GestureAction.SEND_SHORTCUT.label} (${sc.name})" else "Send custom shortcut… (none)"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestureSettingsScreen(onBack: () -> Unit) {
    val repo = App.instance.config
    val cfg by repo.config.collectAsState()

    var actionSheetFor by remember { mutableStateOf<Gesture?>(null) }   // gesture awaiting an action choice
    var shortcutSheetFor by remember { mutableStateOf<Gesture?>(null) } // gesture awaiting a shortcut pick
    var showResetDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Bg)) {
        SettingsTopBar("Gesture settings", onBack)
        val grouped = Gesture.entries.groupBy { gestureMeta(it).group }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        ) {
            for (name in SURFACE_ORDER) {
                val list = grouped[name] ?: continue
                item(key = "header-$name") { CategoryHeader(name) }
                items(list, key = { it.name }) { g ->
                    val meta = gestureMeta(g)
                    GestureRow(
                        label = meta.label,
                        current = currentActionLabel(cfg, g),
                        default = meta.defaultLabel,
                        onClick = { actionSheetFor = g },
                    )
                }
            }
            item(key = "reset-all") {
                Button(
                    onClick = { showResetDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Danger),
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                ) {
                    Text("Reset all gestures", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // -- action picker ----------------------------------------------------------
    actionSheetFor?.let { g ->
        val currentBinding = cfg.gestureBindings.firstOrNull { it.gesture == g }
        ModalBottomSheet(
            onDismissRequest = { actionSheetFor = null },
            containerColor = Panel,
            dragHandle = {
                Box(
                    Modifier
                        .padding(top = 8.dp)
                        .width(36.dp).height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Border),
                )
            },
        ) {
            Text(
                gestureMeta(g).label,
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            LazyColumn(
                Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                items(GestureAction.entries, key = { it.name }) { a ->
                    val selected = currentBinding?.action == a
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (a == GestureAction.SEND_SHORTCUT) {
                                    actionSheetFor = null
                                    shortcutSheetFor = g
                                } else {
                                    repo.bindGesture(g, a)
                                    actionSheetFor = null
                                }
                            }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (selected) "●" else "○",
                            color = if (selected) Accent else TextSecondary,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(end = 10.dp),
                        )
                        Column {
                            Text(
                                a.label,
                                color = if (selected) Accent else TextPrimary,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            )
                            if (a == GestureAction.SEND_SHORTCUT) {
                                val sc = cfg.shortcutById(currentBinding?.shortcutId)
                                Text(
                                    if (sc != null) "Currently: ${sc.name}" else "Pick from saved shortcuts",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // -- shortcut picker (for SEND_SHORTCUT) ------------------------------------
    shortcutSheetFor?.let { g ->
        ModalBottomSheet(
            onDismissRequest = { shortcutSheetFor = null },
            containerColor = Panel,
            dragHandle = {
                Box(
                    Modifier
                        .padding(top = 8.dp)
                        .width(36.dp).height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Border),
                )
            },
        ) {
            Text(
                "Send shortcut",
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            if (cfg.shortcuts.isEmpty()) {
                Text(
                    "No saved shortcuts yet — add some in the Shortcuts screen.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                )
            }
            val boundShortcutId = cfg.gestureBindings.firstOrNull { it.gesture == g }?.shortcutId
            LazyColumn(
                Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                items(cfg.shortcuts, key = { it.id }) { s ->
                    val selected = boundShortcutId == s.id
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                repo.bindGesture(g, GestureAction.SEND_SHORTCUT, s.id)
                                shortcutSheetFor = null
                            }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (selected) "●" else "○",
                            color = if (selected) Accent else TextSecondary,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(end = 10.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                s.name,
                                color = if (selected) Accent else TextPrimary,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            )
                            Text(s.binding, color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }

    // -- reset confirmation -----------------------------------------------------
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset all gestures") },
            text = { Text("Restore every gesture binding to the default mapping?") },
            confirmButton = {
                TextButton(onClick = {
                    repo.resetGestures()
                    showResetDialog = false
                }) { Text("Reset", color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun GestureRow(label: String, current: String, default: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                color = TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Accent)) { append(current) }
                    append("    ")
                    withStyle(SpanStyle(color = TextSecondary)) { append("default: $default") }
                },
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text("›", color = Accent, fontSize = 16.sp)
    }
}

@Composable
private fun CategoryHeader(text: String) {
    Text(
        text.uppercase(),
        color = Accent,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 18.dp, bottom = 4.dp),
    )
}

/** Shared top-bar pattern: back arrow + title, monospace, dark. */
@Composable
private fun SettingsTopBar(title: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(BgElevated)
                .padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text("←", color = TextPrimary, fontSize = 20.sp, fontFamily = FontFamily.Monospace)
            }
            Text(
                title,
                color = TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Border))
    }
}