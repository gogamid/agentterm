package app.agentterm.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import app.agentterm.App
import app.agentterm.ui.theme.Accent
import app.agentterm.ui.theme.Bg
import app.agentterm.ui.theme.BgElevated
import app.agentterm.ui.theme.Border
import app.agentterm.ui.theme.TextPrimary
import app.agentterm.ui.theme.TextSecondary
import java.util.Locale

private const val FONT_MIN = 0.6f
private const val FONT_MAX = 2.4f
private const val FONT_STEP = 0.1f
private const val FONT_STEPS = ((FONT_MAX - FONT_MIN) / FONT_STEP - 1).toInt()  // 17 intermediate points

@Composable
fun SettingsScreen(onBack: () -> Unit, onGestures: () -> Unit, onShortcuts: () -> Unit) {
    val repo = App.instance.config
    val cfg by repo.config.collectAsState()

    Column(Modifier.fillMaxSize().background(Bg)) {
        SettingsTopBar("Settings", onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            SectionHeader("Appearance")
            FontScaleRow(cfg.fontScale) { repo.setFontScale(it) }
            ToggleRow("Auto-hide toolbar", "Hide the toolbar while typing", cfg.autoHideToolbar) { repo.setAutoHideToolbar(it) }
            ToggleRow("Option-as-Meta", "Treat the Option key as Meta", cfg.optionAsMeta) { repo.setOptionAsMeta(it) }

            SectionHeader("Input")
            MenuRow("Gesture settings", "Bind taps, swipes and button presses", onGestures)
            MenuRow("Shortcuts", "Stored commands, sent in one tap", onShortcuts)

            SectionHeader("About")
            Text(
                "AgentTerm  ${versionLabel}",
                color = TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "original implementation, not Termux, not affiliated with Moshi",
                color = TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
        }
    }
}

private val versionLabel: String = "0.1.0-mvp"

@Composable
private fun FontScaleRow(scale: Float, onChange: (Float) -> Unit) {
    val v = scale.coerceIn(FONT_MIN, FONT_MAX)
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            "Font scale  ${String.format(Locale.ROOT, "%.1f", v)}×",
            color = TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
        Slider(
            value = v,
            onValueChange = onChange,
            valueRange = FONT_MIN..FONT_MAX,
            steps = FONT_STEPS,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        )
        Text(
            "0.6× – 2.4×",
            color = TextSecondary,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, color = TextSecondary, fontSize = 11.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun MenuRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextSecondary, fontSize = 11.sp)
        }
        Text("›", color = Accent, fontSize = 18.sp)
    }
}

@Composable
private fun SectionHeader(text: String) {
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