package app.agentterm.core.terminal

/**
 * VT100/xterm escape-sequence parser. Byte-stream in, screen mutations out.
 * Handles what modern TUI coding agents (opencode, codex, pi, herdr) emit:
 * 16/256/truecolor SGR, alt screen, scroll regions, bracketed paste, mouse SGR,
 * focus events, OSC 0/2/7/52, DECSCUSR, DA/DSR queries, DEC special graphics.
 */
class VtParser(
    private val screen: TerminalScreen,
    private val emitter: Emitter = Emitter.NONE,
) {
    interface Emitter {
        fun onTitle(title: String) {}
        fun onClipboard(text: String) {}
        fun onBell() {}
        fun onCwd(path: String) {}
        fun response(bytes: ByteArray) {}

        companion object {
            val NONE = object : Emitter {}
        }
    }

    private enum class State { GROUND, ESCAPE, CSI, CSI_IGNORE, OSC, OSC_IGNORE, DCS, SOS_APC_PM, UTF8 }
    private var state = State.GROUND

    private val params = ArrayList<Int>(8)
    private var marker = 0 // CSI private marker: '?' '>' '!' '='
    private val intermediates = StringBuilder()
    private var escStep = 0
    private var charsetG = 0
    private val oscData = StringBuilder()
    private var utf8Remaining = 0
    private var utf8Accum = 0
    private var lastChar = ' '

    fun feed(data: ByteArray, len: Int = data.size) {
        if (state == State.UTF8 && utf8Remaining > 0) {
            screen.writeChar('\uFFFD'); utf8Remaining = 0
        }
        for (i in 0 until len) process(data[i].toInt() and 0xFF)
    }

    private fun process(b: Int) {
        when (state) {
            State.GROUND -> ground(b)
            State.ESCAPE -> escape(b)
            State.CSI -> csi(b)
            State.CSI_IGNORE -> if (b in 0x40..0x7E) state = State.GROUND
            State.OSC -> osc(b)
            State.OSC_IGNORE -> if (b == 0x07 || b == 0x5C) { oscDone() }
            State.DCS -> if (b == 0x07 || b == 0x5C) state = State.GROUND
            State.SOS_APC_PM -> if (b == 0x07 || b == 0x5C) state = State.GROUND
            State.UTF8 -> utf8(b)
        }
    }

    private fun ground(b: Int) {
        when {
            b == 0x1B -> { state = State.ESCAPE; escStep = 0 }
            b == 0x0E -> screen.shiftOut()                       // SO -> G1
            b == 0x0F -> screen.shiftIn()                        // SI -> G0
            b < 0x20 -> screen.handleControl(b.toChar())
            b == 0x7F -> { /* DEL ignored */ }
            b < 0x80 -> { screen.writeChar(b.toChar()); lastChar = b.toChar() }
            b in 0x80..0xBF -> screen.writeChar('\uFFFD')        // stray continuation
            b in 0xC2..0xDF -> { utf8Remaining = 1; utf8Accum = b and 0x1F; state = State.UTF8 }
            b in 0xE0..0xEF -> { utf8Remaining = 2; utf8Accum = b and 0x0F; state = State.UTF8 }
            b in 0xF0..0xF4 -> { utf8Remaining = 3; utf8Accum = b and 0x07; state = State.UTF8 }
            else -> screen.writeChar('\uFFFD')
        }
    }

    private fun utf8(b: Int) {
        if (b in 0x80..0xBF) {
            utf8Accum = (utf8Accum shl 6) or (b and 0x3F)
            utf8Remaining--
            if (utf8Remaining == 0) {
                val cp = utf8Accum
                when {
                    cp == 0 -> screen.writeChar(' ')
                    cp in 0xD800..0xDFFF -> screen.writeChar('\uFFFD')
                    cp > 0xFFFF -> {
                        // UTF-16 surrogate pair (e.g. emoji)
                        val v = cp - 0x10000
                        screen.writeChar((0xD800 + (v shr 10)).toChar())
                        screen.writeChar((0xDC00 + (v and 0x3FF)).toChar())
                    }
                    else -> screen.writeChar(cp.toChar())
                }
                state = State.GROUND
            }
        } else { screen.writeChar('\uFFFD'); state = State.GROUND; process(b) }
    }

    private fun escape(b: Int) {
        if (escStep == 2) { charset(b); escStep = 0; return }
        when (b) {
            '['.code -> { startCsi(); state = State.CSI; return }
            ']'.code -> { oscData.clear(); state = State.OSC; return }
            'P'.code -> { state = State.DCS; return }
            '^'.code, '_'.code, 'X'.code -> { state = State.SOS_APC_PM; return }
            '('.code -> { charsetG = 0; escStep = 2; return }
            ')'.code -> { charsetG = 1; escStep = 2; return }
            '*'.code, '+'.code -> { charsetG = 2; escStep = 2; return } // G2/G3 -> ASCII
            '7'.code -> screen.saveCursor()
            '8'.code -> screen.restoreCursor()
            'D'.code -> screen.newline()                         // IND
            'E'.code -> { screen.cr(); screen.newline() }        // NEL
            'M'.code -> screen.cursorUp(1)                       // RI
            'c'.code -> screen.softReset()                       // RIS
            '='.code -> screen.appKeypad = true
            '>'.code -> screen.appKeypad = false
            '\\'.code -> {}                                     // ST
            '#'.code, '%'.code -> { /* line/screen attributes: ignore */ }
            else -> {}
        }
        state = State.GROUND
    }

    private fun charset(b: Int) {
        when (b) {
            '0'.code -> screen.selectCharset(charsetG, true)
            else -> screen.selectCharset(charsetG, false)
        }
        state = State.GROUND
    }

    private fun startCsi() { params.clear(); marker = 0; intermediates.clear() }

    private fun csi(b: Int) {
        when {
            b in 0x30..0x3B -> {
                if (b == 0x3B) params.add(0)
                else if (b == 0x3A) { /* subparams unsupported */ }
                else {
                    if (params.isEmpty()) params.add(0)
                    val li = params.size - 1
                    params[li] = params[li] * 10 + (b - 0x30)
                }
            }
            b in 0x3C..0x3F -> if (params.isEmpty()) marker = b else state = State.CSI_IGNORE
            b in 0x20..0x2F -> if (intermediates.length < 4) intermediates.append(b.toChar()) else state = State.CSI_IGNORE
            b in 0x40..0x7E -> { csiFinal(b.toChar()); state = State.GROUND }
            else -> state = State.GROUND
        }
    }

    private fun p(i: Int, def: Int = 1): Int = if (i < params.size) params[i] else def

    private fun csiFinal(f: Char) {
        val inMark = marker == '?'.code
        when (f) {
            'A' -> screen.cursorUp(p(0))
            'B' -> screen.cursorDown(p(0))
            'C' -> screen.cursorRight(p(0))
            'D' -> screen.cursorLeft(p(0))
            'E' -> screen.cursorNextLine(p(0))
            'F' -> screen.cursorPrevLine(p(0))
            'G', '`' -> screen.cursorColumn(p(0, 1) - 1)
            'H', 'f' -> screen.cursorPosition(p(0), p(1))
            'd' -> screen.cursorRowPos(p(0, 1) - 1)
            'J' -> screen.eraseDisplay(p(0, 0).coerceIn(0, 3))
            'K' -> screen.eraseLine(p(0, 0).coerceIn(0, 2))
            'X' -> screen.eraseChars(p(0))
            '@' -> screen.insertChars(p(0))
            'P' -> screen.deleteChars(p(0))
            'L' -> screen.insertLines(p(0))
            'M' -> screen.deleteLines(p(0))
            'S' -> screen.scrollRegionUp(p(0))
            'T' -> screen.scrollRegionDown(p(0))
            'b' -> screen.repeatLast(p(0).coerceIn(1, 10000))
            'c' -> if (marker == 0) emitter.response("\u001B[?1;2c".toByteArray())
            'n' -> if (marker == 0) when (p(0)) {
                5 -> emitter.response("\u001B[0n".toByteArray())
                6 -> emitter.response("\u001B[${screen.cursorRow + 1};${screen.cursorCol + 1}R".toByteArray())
            }
            'h' -> if (inMark) decset(p(0)) else sm(p(0))
            'l' -> if (inMark) decrst(p(0)) else rm(p(0))
            'm' -> sgr()
            'r' -> if (marker == 0) screen.setScrollRegion(p(0, 1) - 1, p(1, screen.rows) - 1)
            's' -> if (marker == 0) screen.saveCursor()
            'u' -> if (marker == 0) screen.restoreCursor()
            'g' -> if (marker == 0) when (p(0, 0)) { 0 -> screen.clearTab(); 3 -> screen.clearAllTabs() }
            'I' -> screen.tab()                                      // CHT
            'Z' -> screen.cursorLeft(8)                              // CBT approximation
            'q' -> if (marker == 0) { /* DECSCUSR */ }
            '!' -> if (marker == '!'.code && p(0, 0) == 0) screen.softReset() // DECSTR
        }
    }

    private fun sm(m: Int) { if (m == 4) screen.insertEnabled = true }
    private fun rm(m: Int) { if (m == 4) screen.insertEnabled = false }

    private fun decset(m: Int) {
        when (m) {
            1 -> screen.appCursorKeys = true
            6 -> screen.originMode = true
            7 -> screen.wrap = true
            25 -> screen.cursorVisible = true
            47, 1047, 1049 -> screen.enterAltScreen()
            66 -> screen.appKeypad = true
            1000, 1002, 1003 -> screen.mouseMode = m
            1004 -> screen.focusEvents = true
            1006 -> screen.mouseSgr = true
            2004 -> screen.bracketedPaste = true
        }
    }

    private fun decrst(m: Int) {
        when (m) {
            1 -> screen.appCursorKeys = false
            6 -> screen.originMode = false
            7 -> screen.wrap = false
            25 -> screen.cursorVisible = false
            47, 1047, 1049 -> screen.exitAltScreen()
            66 -> screen.appKeypad = false
            1000, 1002, 1003 -> screen.mouseMode = 0
            1004 -> screen.focusEvents = false
            1006 -> screen.mouseSgr = false
            2004 -> screen.bracketedPaste = false
        }
    }

    private fun sgr() {
        if (params.isEmpty()) params.add(0)
        var i = 0
        while (i < params.size) {
            val code = params[i]
            when {
                code == 0 -> screen.attrs.reset()
                code == 1 -> screen.attrs.flags = screen.attrs.flags or Cell.BOLD
                code == 2 -> screen.attrs.flags = screen.attrs.flags or Cell.DIM
                code == 3 -> screen.attrs.flags = screen.attrs.flags or Cell.ITALIC
                code == 4 -> screen.attrs.flags = screen.attrs.flags or Cell.UNDERLINE
                code == 5 -> screen.attrs.flags = screen.attrs.flags or Cell.BLINK
                code == 7 -> screen.attrs.flags = screen.attrs.flags or Cell.INVERSE
                code == 8 -> screen.attrs.flags = screen.attrs.flags or Cell.CONCEAL
                code == 9 -> screen.attrs.flags = screen.attrs.flags or Cell.STRIKE
                code == 21 || code == 22 -> screen.attrs.flags = screen.attrs.flags and (Cell.BOLD or Cell.DIM).inv()
                code == 23 -> screen.attrs.flags = screen.attrs.flags and Cell.ITALIC.inv()
                code == 24 -> screen.attrs.flags = screen.attrs.flags and Cell.UNDERLINE.inv()
                code == 25 -> screen.attrs.flags = screen.attrs.flags and Cell.BLINK.inv()
                code == 27 -> screen.attrs.flags = screen.attrs.flags and Cell.INVERSE.inv()
                code == 28 -> screen.attrs.flags = screen.attrs.flags and Cell.CONCEAL.inv()
                code == 29 -> screen.attrs.flags = screen.attrs.flags and Cell.STRIKE.inv()
                code in 30..37 -> screen.attrs.fg = CellColor.Ansi(code - 30)
                code == 38 -> {
                    val t = params.getOrNull(i + 1) ?: 0
                    if (t == 5) { screen.attrs.fg = ColorResolver.xterm256(params.getOrNull(i + 2) ?: 0); i += 2 }
                    else if (t == 2) {
                        screen.attrs.fg = CellColor.Rgb(
                            params.getOrNull(i + 2)?.coerceIn(0, 255) ?: 0,
                            params.getOrNull(i + 3)?.coerceIn(0, 255) ?: 0,
                            params.getOrNull(i + 4)?.coerceIn(0, 255) ?: 0,
                        ); i += 4
                    }
                }
                code == 39 -> screen.attrs.fg = CellColor.DefaultFg
                code in 40..47 -> screen.attrs.bg = CellColor.Ansi(code - 40)
                code == 48 -> {
                    val t = params.getOrNull(i + 1) ?: 0
                    if (t == 5) { screen.attrs.bg = ColorResolver.xterm256(params.getOrNull(i + 2) ?: 0); i += 2 }
                    else if (t == 2) {
                        screen.attrs.bg = CellColor.Rgb(
                            params.getOrNull(i + 2)?.coerceIn(0, 255) ?: 0,
                            params.getOrNull(i + 3)?.coerceIn(0, 255) ?: 0,
                            params.getOrNull(i + 4)?.coerceIn(0, 255) ?: 0,
                        ); i += 4
                    }
                }
                code == 49 -> screen.attrs.bg = CellColor.DefaultBg
                code in 90..97 -> screen.attrs.fg = CellColor.Ansi(code - 90 + 8)
                code in 100..107 -> screen.attrs.bg = CellColor.Ansi(code - 100 + 8)
            }
            i++
        }
    }

    private fun osc(b: Int) {
        if (b == 0x07) { oscDone(); return }        // BEL terminates OSC
        if (b == 0x1B) { state = State.OSC_IGNORE; return } // expect ESC \ (ST)
        oscData.append(b.toChar())
    }

    private fun oscDone() {
        val s = oscData.toString()
        val idx = s.indexOf(';')
        val code = s.substring(0, if (idx < 0) s.length else idx).toIntOrNull() ?: -1
        var text = if (idx < 0) "" else s.substring(idx + 1)
        if (code == 52) {
            // OSC 52;Pc;PAYLOAD — strip the selection-id field
            val marker = text.indexOf(';')
            if (marker >= 0) text = text.substring(marker + 1)
        }
        when (code) {
            0, 2 -> emitter.onTitle(text)
            7 -> emitter.onCwd(text)
            52 -> emitter.onClipboard(text)
        }
        oscData.clear()
        state = State.GROUND
    }
}
