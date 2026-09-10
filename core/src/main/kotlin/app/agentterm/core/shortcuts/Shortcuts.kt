package app.agentterm.core.shortcuts

/**
 * A single logical keystroke: optional modifiers + a key.
 * key is a single char ('b', 'T') or a named key from [Named] / "F1".."F12".
 */
data class KeyChord(val mods: Mods = Mods(), val key: String) {
    data class Mods(var ctrl: Boolean = false, var shift: Boolean = false, var alt: Boolean = false)

    object Named {
        const val TAB = "Tab"
        const val ENTER = "Enter"
        const val ESC = "Esc"
        const val SPACE = "Space"
        const val BSPACE = "BSpace"
        const val UP = "Up"
        const val DOWN = "Down"
        const val LEFT = "Left"
        const val RIGHT = "Right"
        const val HOME = "Home"
        const val END = "End"
        const val PAGE_UP = "PageUp"
        const val PAGE_DOWN = "PageDown"
    }

    val display: String = buildString {
        if (mods.ctrl) append("Ctrl+")
        if (mods.shift) append("Shift+")
        if (mods.alt) append("Alt+")
        append(key)
    }
}

/** Parsed sequence of [KeyChord] steps. */
data class Shortcut(val name: String, val raw: String, val steps: List<KeyChord>, val category: String = "General") {
    val label: String get() = steps.joinToString(", ") { it.display }
}

/**
 * Advanced binding grammar (Moshi-compatible subset):
 * `C-b, S-t`  `Ctrl+b, Shift+t`  `F12, h`  `M-x`  `C-dash`  `text:/clear`
 * Separators are ',' or ' '. Case of a bare letter is preserved.
 */
object ShortcutParser {
    class ParseException(message: String) : Exception(message)

    fun parse(raw: String): List<KeyChord> {
        val s = raw.trim()
        if (s.isEmpty()) throw ParseException("Empty binding")
        if (s.startsWith("text:")) return s.removePrefix("text:").map { KeyChord(KeyChord.Mods(), it.toString()) }
        val steps = tokenize(s).map { parseChord(it) }
        if (steps.isEmpty()) throw ParseException("No keys in binding")
        return steps
    }

    private fun tokenize(s: String): List<String> = s.split(',', ' ').filter { it.isNotBlank() }

    private val prefixMap = listOf(
        "control+" to { m: KeyChord.Mods -> m.ctrl = true },
        "ctrl+" to { m: KeyChord.Mods -> m.ctrl = true },
        "c-" to { m: KeyChord.Mods -> m.ctrl = true },
        "option+" to { m: KeyChord.Mods -> m.alt = true },
        "opt+" to { m: KeyChord.Mods -> m.alt = true },
        "alt+" to { m: KeyChord.Mods -> m.alt = true },
        "m-" to { m: KeyChord.Mods -> m.alt = true },
        "shift+" to { m: KeyChord.Mods -> m.shift = true },
        "s-" to { m: KeyChord.Mods -> m.shift = true },
    )

    private fun parseChord(tok: String): KeyChord {
        var t = tok
        val mods = KeyChord.Mods()
        var progress = true
        while (progress) {
            progress = false
            for ((prefix, apply) in prefixMap) {
                if (t.lowercase().startsWith(prefix)) {
                    apply(mods); t = t.substring(prefix.length); progress = true; break
                }
            }
        }
        if (t.isEmpty()) throw ParseException("Bad token: $tok")

        Regex("^[Ff]([1-9]|1[0-2])$").find(t)?.let { return KeyChord(mods, "F${it.groupValues[1]}") }

        val named = mapOf(
            "tab" to KeyChord.Named.TAB, "enter" to KeyChord.Named.ENTER, "return" to KeyChord.Named.ENTER,
            "esc" to KeyChord.Named.ESC, "escape" to KeyChord.Named.ESC, "space" to KeyChord.Named.SPACE,
            "bspace" to KeyChord.Named.BSPACE, "bpace" to KeyChord.Named.BSPACE, "bs" to KeyChord.Named.BSPACE, "backspace" to KeyChord.Named.BSPACE,
            "up" to KeyChord.Named.UP, "down" to KeyChord.Named.DOWN, "left" to KeyChord.Named.LEFT, "right" to KeyChord.Named.RIGHT,
            "home" to KeyChord.Named.HOME, "end" to KeyChord.Named.END,
            "pageup" to KeyChord.Named.PAGE_UP, "pagedown" to KeyChord.Named.PAGE_DOWN,
            "dash" to "-", "minus" to "-", "plus" to "+",
        )
        named[t.lowercase()]?.let { return KeyChord(mods, it) }
        if (t.length == 1 && t[0] in ' '..'~') return KeyChord(mods, t)
        throw ParseException("Bad token: $tok")
    }
}

/**
 * Emits terminal byte sequences for chord steps (xterm-style key encoding).
 */
object KeyEmitter {
    fun emit(step: KeyChord, appCursorKeys: Boolean): ByteArray {
        val k = step.key
        val ctrl = step.mods.ctrl
        val alt = step.mods.alt
        val shift = step.mods.shift
        val sb = StringBuilder()

        fun str(s: String) = s.toByteArray(Charsets.UTF_8)

        when {
            k.length == 1 && k[0] in ' '..'~' && k != "-" && k != "+" -> {
                val c = k[0]
                when {
                    ctrl -> sb.append(ctrlCode(c))
                    alt -> { sb.append('\u001B'); sb.append(if (shift) c.uppercaseChar() else c) }
                    else -> sb.append(if (shift) c.uppercaseChar() else c)
                }
            }
            k == "-" || k == "+" -> { if (alt) sb.append('\u001B'); if (ctrl && k == "-") sb.append(0x1F.toChar()) else sb.append(k) }
            k == KeyChord.Named.TAB -> if (shift) sb.append("\u001B[Z") else sb.append('\t')
            k == KeyChord.Named.ENTER -> sb.append('\r')
            k == KeyChord.Named.ESC -> sb.append('\u001B')
            k == KeyChord.Named.SPACE -> if (ctrl) sb.append(0.toChar()) else { if (alt) sb.append('\u001B'); sb.append(' ') }
            k == KeyChord.Named.BSPACE -> sb.append(
                when { ctrl && alt -> "\u001B\u007F"; ctrl -> "\u0008"; else -> "\u007F" }
            )
            k in listOf(KeyChord.Named.UP, KeyChord.Named.DOWN, KeyChord.Named.LEFT, KeyChord.Named.RIGHT) -> {
                val letter = when (k) {
                    KeyChord.Named.UP -> "A"; KeyChord.Named.DOWN -> "B"
                    KeyChord.Named.RIGHT -> "C"; else -> "D"
                }
                val code = if (shift) "1;2" else if (alt) "1;3" else if (ctrl) "1;5" else "1"
                if (code == "1") {
                    sb.append("\u001B")
                    sb.append(if (appCursorKeys) 'O' else '[')
                    sb.append(letter)
                } else sb.append("\u001B[$code$letter")
            }
            k == KeyChord.Named.HOME -> sb.append(if (shift) "\u001B[1;2H" else "\u001B[H")
            k == KeyChord.Named.END -> sb.append(if (shift) "\u001B[1;2F" else "\u001B[F")
            k == KeyChord.Named.PAGE_UP -> sb.append("\u001B[5${modSuffix(step)}~")
            k == KeyChord.Named.PAGE_DOWN -> sb.append("\u001B[6${modSuffix(step)}~")
            k.startsWith("F") -> funcKey(k, step, sb)
            else -> { if (alt) sb.append('\u001B'); sb.append(k) }
        }
        return str(sb.toString())
    }

    private fun funcKey(k: String, step: KeyChord, sb: StringBuilder) {
        val n = k.removePrefix("F").toIntOrNull() ?: return
        val mod = if (step.mods.shift) "2" else if (step.mods.alt) "3" else if (step.mods.ctrl) "5" else null
        if (n <= 4) {
            val p = "PQRS"[n - 1]
            if (mod == null) sb.append("\u001BO").append(p)
            else sb.append("\u001B[1;$mod").append(p)
        } else {
            val base = when (n) { 5 -> 15; 6 -> 17; 7 -> 18; 8 -> 19; 9 -> 20; 10 -> 21; 11 -> 23; else -> 24 }
            sb.append("\u001B[$base")
            if (mod != null) sb.append(";$mod")
            sb.append("~")
        }
    }

    private fun modSuffix(step: KeyChord): String =
        if (step.mods.shift) ";2" else if (step.mods.alt) ";3" else if (step.mods.ctrl) ";5" else ""

    private fun ctrlCode(c: Char): Char = when (c.lowercaseChar()) {
        in 'a'..'z' -> (c.lowercaseChar().code - 'a'.code + 1).toChar()
        ' ', '@', '2' -> 0.toChar()
        '[', '3' -> 0x1B.toChar()
        '\\', '4' -> 0x1C.toChar()
        ']', '5' -> 0x1D.toChar()
        '^', '6' -> 0x1E.toChar()
        '_', '7', '-' -> 0x1F.toChar()
        '(' -> 0x1B.toChar()
        '?' -> 0x7F.toChar()
        else -> c
    }
}