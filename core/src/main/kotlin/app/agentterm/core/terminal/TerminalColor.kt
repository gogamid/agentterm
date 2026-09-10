package app.agentterm.core.terminal

/**
 * Reference color stored in a terminal cell. Rendering resolves these against a
 * concrete palette (the app theme) — the engine itself stays theme-agnostic.
 */
sealed class CellColor {
    object DefaultFg : CellColor()
    object DefaultBg : CellColor()
    data class Ansi(val index: Int) : CellColor() // 0..255 palette index
    data class Rgb(val r: Int, val g: Int, val b: Int) : CellColor()
}

/** Classic xterm-ish base16 palette (default theme). Overridable in settings. */
val DEFAULT_PALETTE: IntArray = intArrayOf(
    0x1e1e2e, 0xcc0000, 0x4e9a06, 0xc4a000, 0x3465a4, 0x75507b, 0x06989a, 0xd3d7cf, // 0-7
    0x555753, 0xef2929, 0x8ae234, 0xfce94f, 0x729fcf, 0xad7fa8, 0x34e2e2, 0xeeeeec, // 8-15
)

/** Resolves a CellColor to a concrete ARGB given a palette and theme defaults. */
object ColorResolver {
    fun toArgb(
        color: CellColor,
        defaultFgArgb: Int,
        defaultBgArgb: Int,
        palette: IntArray = DEFAULT_PALETTE,
    ): Int = when (color) {
        is CellColor.DefaultFg -> defaultFgArgb
        is CellColor.DefaultBg -> defaultBgArgb
        is CellColor.Rgb -> (0xFF shl 24) or (color.r shl 16) or (color.g shl 8) or color.b
        is CellColor.Ansi -> (0xFF shl 24) or (palette[color.index.coerceIn(0, palette.lastIndex)] and 0xFFFFFF)
    }

    /** xterm 256-color cube index -> RGB. */
    fun xterm256(index: Int): CellColor = when {
        index < 16 -> CellColor.Ansi(index)
        index < 232 -> {
            val i = index - 16
            val r = cube(i / 36); val g = cube((i / 6) % 6); val b = cube(i % 6)
            CellColor.Rgb(r, g, b)
        }
        else -> {
            val v = 8 + (index - 232) * 10
            CellColor.Rgb(v, v, v)
        }
    }

    private fun cube(v: Int): Int = when (v) { 0 -> 0; else -> 55 + v * 40 }
}