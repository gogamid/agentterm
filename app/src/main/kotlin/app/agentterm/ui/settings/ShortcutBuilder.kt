package app.agentterm.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.agentterm.core.config.SavedShortcut
import app.agentterm.core.shortcuts.KeyChord
import app.agentterm.core.shortcuts.ShortcutParser
import app.agentterm.ui.theme.Accent
import app.agentterm.ui.theme.Bg
import app.agentterm.ui.theme.BgElevated
import app.agentterm.ui.theme.Border
import app.agentterm.ui.theme.Danger
import app.agentterm.ui.theme.Ok
import app.agentterm.ui.theme.Panel
import app.agentterm.ui.theme.TextPrimary
import app.agentterm.ui.theme.TextSecondary

private val CATEGORIES = listOf("General", "tmux", "herdr", "agents", "shell")

private enum class ModifierChoice(val label: String) {
    NONE("None"), CTRL("Ctrl"), OPT("Opt"), SHIFT("Shift"),
}

private val OnAccent = Color(0xFF052521) // matches Theme.kt onPrimary

private class ParseOutcome(val chords: List<KeyChord>?, val error: String?)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShortcutBuilder(
    initial: SavedShortcut?,
    onSave: (name: String, binding: String, category: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var category by remember { mutableStateOf(initial?.category?.takeIf { it.isNotBlank() } ?: "General") }
    var advanced by remember { mutableStateOf(initial != null) }        // existing bindings are grammar strings
    var modifier by remember { mutableStateOf(ModifierChoice.NONE) }    // simple mode
    var key by remember { mutableStateOf("") }                          // simple mode
    var binding by remember { mutableStateOf(initial?.binding ?: "") }  // advanced mode
    var autoEnter by remember { mutableStateOf(initial?.autoEnter ?: false) }

    // Simple-mode candidate per spec:
    //   modifier None => the key text is sent literally (text:)
    //   Ctrl/Opt/Shift => C- / M- / S- prefix; multi-char key stays raw as text:
    val candidate = buildString {
        val k = key.trim()
        if (k.isEmpty()) return@buildString
        if (k.length > 1 || modifier == ModifierChoice.NONE) {
            append("text:")
            append(k)
        } else {
            when (modifier) {
                ModifierChoice.CTRL -> append("C-")
                ModifierChoice.OPT -> append("M-")
                ModifierChoice.SHIFT -> append("S-")
                ModifierChoice.NONE -> {}
            }
            append(k)
        }
    }

    val outcome: ParseOutcome = try {
        ParseOutcome(ShortcutParser.parse(if (advanced) binding else candidate), null)
    } catch (e: ShortcutParser.ParseException) {
        ParseOutcome(null, e.message ?: "Invalid binding")
    }
    val valid = outcome.chords != null
    val preview = outcome.chords?.joinToString(", ") { it.display }
    val finishedBinding = if (advanced) binding.trim() else candidate
    val canSave = name.isNotBlank() && valid &&
        (if (advanced) binding.isNotBlank() else key.isNotBlank())
    // OutlinedTextFieldDefaults.colors() is @Composable, so build it in composition.
    val greenColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Ok,
        unfocusedBorderColor = Ok,
        cursorColor = Ok,
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = Bg) {
            Column(
                Modifier.fillMaxSize().imePadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // -- dialog top bar -------------------------------------------------
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(BgElevated)
                        .padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("✕", color = TextPrimary, fontSize = 16.sp)
                    }
                    Text(
                        if (initial == null) "New shortcut" else "Edit shortcut",
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Border))

                // -- form -----------------------------------------------------------
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        placeholder = { Text("e.g. git push") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Text("Category", color = TextSecondary, fontSize = 11.sp)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CATEGORIES.forEach { c ->
                            BuilderChip(c, c == category) { category = c }
                        }
                    }

                    // -- mode toggle ------------------------------------------------
                    OutlinedButton(
                        onClick = { advanced = !advanced },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (advanced) "Simple mode…" else "Advanced…")
                    }

                    if (advanced) {
                        // -- advanced mode: raw grammar, live-validated -------------
                        val failed = binding.isNotBlank() && !valid
                        OutlinedTextField(
                            value = binding,
                            onValueChange = { binding = it },
                            label = { Text("Binding (grammar)") },
                            placeholder = { Text("C-b, p   or   text:/clear") },
                            singleLine = true,
                            isError = failed,
                            colors = if (valid && binding.isNotBlank()) greenColors else OutlinedTextFieldDefaults.colors(),
                            modifier = Modifier.fillMaxWidth(),
                            supportingText = {
                                when {
                                    failed -> Text(outcome.error ?: "Invalid binding", color = Danger, fontSize = 11.sp)
                                    binding.isBlank() -> Text("C-b, p  ·  text:/clear  ·  F12, h", color = TextSecondary, fontSize = 11.sp)
                                    else -> Text("OK — $preview", color = Ok, fontSize = 11.sp)
                                }
                            },
                        )

                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { autoEnter = !autoEnter }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = autoEnter, onCheckedChange = { autoEnter = it })
                            Column {
                                Text("Auto-enter", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "Stored flag only — the binding is kept as typed",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    } else {
                        // -- simple mode --------------------------------------------
                        Text("Modifier", color = TextSecondary, fontSize = 11.sp)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ModifierChoice.entries.forEach { m ->
                                BuilderChip(m.label, m == modifier) { modifier = m }
                            }
                        }
                        OutlinedTextField(
                            value = key,
                            onValueChange = { key = it.take(16) },
                            label = { Text("Key") },
                            placeholder = { Text("a · b · Tab · F5") },
                            singleLine = true,
                            isError = key.isNotBlank() && !valid,
                            colors = if (valid && key.isNotBlank()) greenColors else OutlinedTextFieldDefaults.colors(),
                            modifier = Modifier.fillMaxWidth(),
                            supportingText = {
                                when {
                                    key.isBlank() -> Text(
                                        "Type the key to send, or use Advanced for multi-key chords",
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                    )
                                    !valid -> Text(outcome.error ?: "Invalid key", color = Danger, fontSize = 11.sp)
                                    else -> Text("Sends: $preview", color = Ok, fontSize = 11.sp)
                                }
                            },
                        )
                    }

                    Button(
                        onClick = { onSave(name.trim(), finishedBinding, category) },
                        enabled = canSave,
                        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        Text("Save shortcut", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun BuilderChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Accent.copy(alpha = 0.16f) else Panel)
            .border(1.dp, if (selected) Accent else Border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            color = if (selected) Accent else TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}