package app.agentterm.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import app.agentterm.App
import app.agentterm.sessions.SessionHandle
import app.agentterm.ui.theme.Accent
import app.agentterm.ui.theme.Border
import app.agentterm.ui.theme.Panel
import app.agentterm.ui.theme.TextSecondary

@Composable
fun HomeScreen(
    backToEditor: (String?) -> Unit,
    openTerminal: (String) -> Unit,
    openSettings: () -> Unit,
) {
    val app = App.instance
    val connections by app.connections.items.collectAsState()
    val sessions by app.sessions.sessions.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("AgentTerm", style = MaterialTheme.typography.headlineSmall)
                    Text("terminal for AI agents", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Settings",
                    modifier = Modifier.size(22.dp).clickable { openSettings() },
                    tint = TextSecondary,
                )
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigActionButton(
                    label = "Console",
                    sub = "local shell",
                    accent = Accent,
                    modifier = Modifier.weight(1f),
                ) {
                    val h = app.sessions.startLocal()
                    openTerminal(h.id)
                }
                BigActionButton(
                    label = "SSH host",
                    sub = "connect + add",
                    accent = Color(0xFF7C3AED),
                    modifier = Modifier.weight(1f),
                ) {
                    backToEditor(null)
                }
            }
        }

        if (sessions.isNotEmpty()) {
            item { SectionHeader("Active sessions (${sessions.size})") }
            items(sessions.size) { i ->
                val h = sessions[i]
                ConnectionCard(
                    title = h.title,
                    subtitle = h.kind.name.lowercase(),
                    badge = if (h.kind == SessionHandle.Kind.SSH) "ssh" else "local",
                    badgeColor = if (h.kind == SessionHandle.Kind.SSH) Color(0xFF7C3AED) else Accent,
                ) { openTerminal(h.id) }
            }
        }

        item { SectionHeader(if (connections.isEmpty()) "Saved connections" else "Connections (${connections.size})") }

        if (connections.isEmpty()) {
            item {
                Text(
                    "No SSH hosts saved yet. Add one to pair with the machine your agents run on.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }

        items(connections.size) { i ->
            val c = connections[i]
            ConnectionCard(
                title = c.label,
                subtitle = "${c.host}:${c.port} · ${c.authType.name.lowercase().replaceFirstChar { it.uppercase() }} auth",
                badge = "ssh",
                badgeColor = Color(0xFF7C3AED),
            ) {
                val h = app.sessions.startSsh(c)
                openTerminal(h.id)
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = TextSecondary,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
    )
}

@Composable
private fun BigActionButton(label: String, sub: String, accent: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Panel),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(accent, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.height(4.dp))
            Text(sub, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

@Composable
private fun ConnectionCard(
    title: String,
    subtitle: String,
    badge: String,
    badgeColor: Color,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Panel),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(34.dp).background(badgeColor.copy(alpha = 0.15f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Text(badge, style = MaterialTheme.typography.bodySmall, color = badgeColor)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("›", color = TextSecondary, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
fun BackBar(title: String, onBack: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            modifier = Modifier.size(24.dp).clickable { onBack() },
            tint = TextSecondary,
        )
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}