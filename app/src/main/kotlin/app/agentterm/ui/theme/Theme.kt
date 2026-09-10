package app.agentterm.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Accent = Color(0xFF2DD4BF)          // teal, terminal-cursor vibe
val AccentDim = Color(0xFF14B8A6)
val Bg = Color(0xFF0E0F13)
val BgElevated = Color(0xFF151820)
val Panel = Color(0xFF1B1F29)
val Border = Color(0xFF2A2F3B)
val TextPrimary = Color(0xFFE6E8EE)
val TextSecondary = Color(0xFF8B93A5)
val Danger = Color(0xFFEF4444)
val Ok = Color(0xFF22C55E)

val TerminalDefaultFg: Int = 0xFFE6E8EE.toInt()
val TerminalDefaultBg: Int = 0xFF0E0F13.toInt()

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF052521),
    secondary = AccentDim,
    background = Bg,
    onBackground = TextPrimary,
    surface = BgElevated,
    onSurface = TextPrimary,
    surfaceVariant = Panel,
    onSurfaceVariant = TextSecondary,
    outline = Border,
    error = Danger,
)

private val BaseTypography = Typography(
    bodyLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 20.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 24.sp, fontWeight = FontWeight.Bold),
)

@Composable
fun AgentTermTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = BaseTypography,
        content = content,
    )
}