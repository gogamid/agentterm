package app.agentterm.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import app.agentterm.App
import app.agentterm.core.config.SavedShortcut
import app.agentterm.ui.theme.Accent
import app.agentterm.ui.theme.BgElevated
import app.agentterm.ui.theme.Border
import app.agentterm.ui.theme.Danger
import app.agentterm.ui.theme.TextPrimary
import app.agentterm.ui.theme.TextSecondary

private val AddFg = Color(0xFF052521) // matches Theme.kt onPrimary

/** What the ShortcutBuilder is editing: a fresh shortcut or an existing one. */
private sealed interface BuilderTarget {
    data object New : BuilderTarget
    data class Edit(val shortcut: SavedShortcut) : BuilderTarget
}

@Composable
fun ShortcutsScreen(onBack: () -> Unit) {
    val repo = App.instance.config
    val cfg by repo.config.collectAsState()

    var target by remember { mutableStateOf<BuilderTarget?>(null) }
    var deleteTarget by remember { mutableStateOf<SavedShortcut?>(null) }

    Scaffold(
        topBar = { SettingsTopBar("Shortcuts", onBack) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { target = BuilderTarget.New },
                containerColor = Accent,
                contentColor = AddFg,
            ) {
                Text("+", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
        },
    ) { padding ->
        val grouped = cfg.shortcuts.groupBy { it.category }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        ) {
            if (grouped.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "No shortcuts yet. Tap + to add one.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }
            }
            grouped.forEach { (cat, list) ->
                item(key = "header-$cat") { CategoryHeader(cat) }
                items(list, key = { it.id }) { s ->
                    ShortcutRow(
                        s,
                        onEdit = { target = BuilderTarget.Edit(s) },
                        onDelete = { deleteTarget = s },
                    )
                }
            }
        }
    }

    // -- delete confirmation ----------------------------------------------------
    deleteTarget?.let { t ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete shortcut") },
            text = { Text("Delete \"${t.name}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    repo.removeShortcut(t.id)
                    deleteTarget = null
                }) { Text("Delete", color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }

    // -- builder ----------------------------------------------------------------
    target?.let { t ->
        val initial = (t as? BuilderTarget.Edit)?.shortcut
        ShortcutBuilder(
            initial = initial,
            onSave = { name, binding, category ->
                when (t) {
                    is BuilderTarget.Edit -> repo.updateShortcut(t.shortcut.copy(name = name, binding = binding, category = category))
                    is BuilderTarget.New -> repo.addShortcut(name, binding, category)
                }
                target = null
            },
            onDismiss = { target = null },
        )
    }
}

@Composable
private fun ShortcutRow(s: SavedShortcut, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                s.name,
                color = TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                s.toShortcut().label,
                color = TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = onEdit) { Text("✎", color = TextPrimary, fontSize = 14.sp) }
        TextButton(onClick = onDelete) { Text("⌫", color = Danger, fontSize = 14.sp) }
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