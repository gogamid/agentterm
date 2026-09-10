package app.agentterm.core.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

class VtParserTest {

    private fun session(rows: Int = 6, cols: Int = 12) = BufferSession(cols, rows)

    private fun feed(s: BufferSession, vararg parts: String) {
        for (p in parts) s.feed(p.toByteArray())
    }

    @Test fun `plain text lands in the grid`() {
        val s = session()
        feed(s, "hello")
        assertEquals("hello       ", s.screen.line(0).joinToString("") { it.ch.toString() })
        assertEquals(5, s.screen.cursorCol)
    }

    @Test fun `newline scrolls and pushes into scrollback`() {
        val s = session(rows = 3)
        feed(s, "a\r\nb\r\nc\r\nd")
        assertEquals(1, s.screen.scrollbackSize)
        assertEquals("a", s.screen.scrollbackLine(0).joinToString("") { it.ch.toString() }.trimEnd())
        assertEquals("b", s.screen.line(0).joinToString("") { it.ch.toString() }.trimEnd())
        assertEquals("d", s.screen.line(2).joinToString("") { it.ch.toString() }.trimEnd())
    }

    @Test fun `CR does not advance rows`() {
        val s = session()
        feed(s, "abc\rXY")
        assertEquals("XYc", s.screen.line(0).joinToString("") { it.ch.toString() }.trimEnd())
    }

    @Test fun `cursor position CSI H`() {
        val s = session()
        feed(s, "\u001B[2;3HX")
        assertEquals("  X         ", s.screen.line(1).joinToString("") { it.ch.toString() })
        assertEquals(3, s.screen.cursorCol)
        assertEquals(1, s.screen.cursorRow)
    }

    @Test fun `erase display below and above`() {
        val s = session()
        feed(s, "abcdefghijkl", "mno", "\u001B[2;1H", "XYZ\u001B[J")
        // row0 remains, rows >= 1 erased
        assertEquals("abcdefghijkl", s.screen.line(0).joinToString("") { it.ch.toString() })
        assertEquals("", s.screen.line(2).joinToString("") { it.ch.toString() }.trimEnd())
    }

    @Test fun `erase line`() {
        val s = session()
        feed(s, "hello", "\u001B[2K")
        assertEquals("", s.screen.line(0).joinToString("") { it.ch.toString() }.trimEnd())
    }

    @Test fun `SGR colors are applied to cells`() {
        val s = session()
        feed(s, "\u001B[31mR\u001B[42mG\u001B[0mN")
        val r0 = s.screen.line(0)
        assertEquals(CellColor.Ansi(1), r0[0].fg)
        assertEquals(CellColor.Ansi(2), r0[1].bg)
        assertEquals(CellColor.DefaultFg, r0[2].fg)
        assertEquals(CellColor.DefaultBg, r0[2].bg)
    }

    @Test fun `256 color and truecolor`() {
        val s = session()
        feed(s, "\u001B[38;5;123mX")
        assertEquals(CellColor.Rgb(135, 255, 255), s.screen.line(0)[0].fg)
        feed(s, "\u001B[38;2;10;20;30mY")
        assertEquals(CellColor.Rgb(10, 20, 30), s.screen.line(0)[1].fg)
    }

    @Test fun `bold flag`() {
        val s = session()
        feed(s, "\u001B[1mB")
        assertEquals(Cell.BOLD, s.screen.line(0)[0].flags and Cell.BOLD)
        feed(s, "\u001B[0m")
        feed(s, "N")
        assertEquals(0, s.screen.line(0)[1].flags and Cell.BOLD)
    }

    @Test fun `inverse video`() {
        val s = session()
        feed(s, "\u001B[7mX")
        assertEquals(Cell.INVERSE, s.screen.line(0)[0].flags and Cell.INVERSE)
    }

    @Test fun `alt screen enter and exit restores content`() {
        val s = session()
        feed(s, "keep")
        feed(s, "\u001B[?1049h")
        feed(s, "alt")
        assertEquals("alt", s.screen.line(0).joinToString("") { it.ch.toString() }.trimEnd())
        feed(s, "\u001B[?1049l")
        assertEquals("keep", s.screen.line(0).joinToString("") { it.ch.toString() }.trimEnd())
    }

    @Test fun `scroll region restricts scrolling`() {
        val s = session(rows = 5)
        feed(s, "\u001B[2;4r")            // region rows 2..4 (1-based)
        feed(s, "\u001B[4;1H")            // cursor to row 4
        feed(s, "A\r\nB\r\nC")           // CRLF: scrolls inside region only
        assertEquals("A", s.screen.line(1).joinToString("") { it.ch.toString() }.trimEnd())
        assertEquals("B", s.screen.line(2).joinToString("") { it.ch.toString() }.trimEnd())
        assertEquals("C", s.screen.line(3).joinToString("") { it.ch.toString() }.trimEnd())
        assertEquals(0, s.screen.scrollbackSize)
    }

    @Test fun `DEC special graphics box drawing`() {
        val s = session()
        feed(s, "\u001B(0")
        feed(s, "q")           // horizontal line
        feed(s, "\u001B(B")
        feed(s, "x")
        assertEquals("\u2500x", s.screen.line(0).joinToString("") { it.ch.toString() }.take(2))
    }

    @Test fun `bracketed paste sets mode`() {
        val s = session()
        feed(s, "\u001B[?2004h")
        assertEquals(true, s.screen.bracketedPaste)
        feed(s, "\u001B[?2004l")
        assertEquals(false, s.screen.bracketedPaste)
    }

    @Test fun `UTF-8 multibyte and emoji`() {
        val s = session()
        feed(s, "caf\u00E9 \uD83D\uDE00")   // café 😀
        val line = s.screen.line(0).joinToString("") { it.ch.toString() }.trimEnd()
        assertEquals("café 😀", line)
    }

    @Test fun `OSC title and clipboard`() {
        var title = ""
        var clip = ""
        val s = BufferSession(10, 3)
        val p = VtParser(s.screen, object : VtParser.Emitter {
            override fun onTitle(t: String) { title = t }
            override fun onClipboard(t: String) { clip = t }
        })
        p.feed("\u001B]0;hello\u0007".toByteArray())
        p.feed("\u001B]52;c;dGVzdA==\u0007".toByteArray())   // OSC 52 "test"
        assertEquals("hello", title)
        assertEquals("dGVzdA==", clip)
    }

    @Test fun `DA and DSR responses`() {
        val responses = StringBuilder()
        val s = BufferSession(10, 3)
        val p = VtParser(s.screen, object : VtParser.Emitter {
            override fun response(b: ByteArray) { responses.append(String(b)) }
        })
        p.feed("\u001B[c".toByteArray())
        assertEquals("\u001B[?1;2c", responses.toString())
        responses.clear()
        p.feed("\u001B[5n".toByteArray())
        assertEquals("\u001B[0n", responses.toString())
        responses.clear()
        p.feed("\u001B[6n".toByteArray())
        assertEquals("\u001B[1;1R", responses.toString())
    }

    @Test fun `wraparound and wrap off`() {
        val s = session(cols = 5)
        feed(s, "abcde", "f")                       // wrap on
        assertEquals("f", s.screen.line(1).joinToString("") { it.ch.toString() }.trimEnd())
        feed(s, "\u001B[?7l")                        // wrap off
        feed(s, "\u001B[1;1H", "XYZ")                 // cursor back to start; 3 chars
        assertEquals("XYZde", s.screen.line(0).joinToString("") { it.ch.toString() }.trimEnd())
        feed(s, "W")                                  // overwrite col 4 (last col when wrap off)
        assertEquals("XYZWe", s.screen.line(0).joinToString("") { it.ch.toString() }.trimEnd())
    }

    @Test fun `insert and delete chars`() {
        val s = session(cols = 6)
        feed(s, "abcdef", "\u001B[1;3H", "\u001B[@", "X")
        assertEquals("abXcde", s.screen.line(0).joinToString("") { it.ch.toString() }.trimEnd())
        feed(s, "\u001B[1;2H", "\u001B[P")
        assertEquals("aXcde ", s.screen.line(0).joinToString("") { it.ch.toString() })
    }

    @Test fun `scroll up SU and down SD within region`() {
        val s = session(cols = 6, rows = 4)
        feed(s, "\u001B[2;3r")         // region rows 2..3 (1-based)
        feed(s, "\u001B[3;1H", "1")     // content at bottom row of region
        feed(s, "\u001B[S")             // SU: content moves up one row
        assertEquals("1", s.screen.line(1).joinToString("") { it.ch.toString() }.trimEnd())
        assertEquals("", s.screen.line(2).joinToString("") { it.ch.toString() }.trimEnd())
        feed(s, "\u001B[T")             // SD: content moves back down
        assertEquals("", s.screen.line(1).joinToString("") { it.ch.toString() }.trimEnd())
        assertEquals("1", s.screen.line(2).joinToString("") { it.ch.toString() }.trimEnd())
    }

    @Test fun `CSI ignored junk does not corrupt screen`() {
        val s = session()
        feed(s, "\u001B[?704;1;13l")   // unknown private mode
        feed(s, "ok")
        assertEquals("ok", s.screen.line(0).joinToString("") { it.ch.toString() }.trimEnd())
    }

    @Test fun `OSC 7 cwd`() {
        var cwd = ""
        val s = BufferSession(10, 3)
        val p = VtParser(s.screen, object : VtParser.Emitter {
            override fun onCwd(c: String) { cwd = c }
        })
        p.feed("\u001B]7;file://host/home/user\u001B\\".toByteArray())
        assertEquals("file://host/home/user", cwd)
    }

    @Test fun `mouse SGR mode and focus events`() {
        val s = session()
        feed(s, "\u001B[?1006h\u001B[?1004h")
        assertEquals(true, s.screen.mouseSgr)
        assertEquals(true, s.screen.focusEvents)
        assertEquals(0, s.screen.mouseMode)
        feed(s, "\u001B[?1003h")
        assertEquals(1003, s.screen.mouseMode)
    }

    @Test fun `save and restore cursor`() {
        val s = session()
        feed(s, "\u001B[3;3H", "\u001B7", "\u001B[1;1H", "X", "\u001B8", "Y")
        assertEquals('X', s.screen.line(0)[0].ch)
        assertEquals('Y', s.screen.line(2)[2].ch)
        assertEquals(2, s.screen.cursorRow)
    }

    @Test fun `reverse index RI`() {
        val s = session(rows = 3)
        feed(s, "a\nb", "\u001BM")   // RI moves cursor up; at top it scrolls down
        assertEquals(0, s.screen.cursorRow)
    }

    @Test fun `charset SI SO shift`() {
        val s = session()
        feed(s, "\u001B(0", "q", "\u001B(B")       // G0 acs -> non-acs
        assertEquals('\u2500', s.screen.line(0)[0].ch)
        feed(s, "q")
        assertEquals('q', s.screen.line(0)[1].ch)
    }
}