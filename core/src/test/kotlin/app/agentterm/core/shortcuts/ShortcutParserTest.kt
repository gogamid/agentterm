package app.agentterm.core.shortcuts

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ShortcutParserTest {

    @Test fun `simple ctrl chord`() {
        val steps = ShortcutParser.parse("C-c")
        assertEquals(1, steps.size)
        assertEquals(true, steps[0].mods.ctrl)
        assertEquals("c", steps[0].key)
    }

    @Test fun `long modifier names`() {
        val steps = ShortcutParser.parse("Ctrl+b")
        assertEquals(true, steps[0].mods.ctrl)
    }

    @Test fun `multi-step with different modifiers`() {
        val steps = ShortcutParser.parse("C-b, S-t")
        assertEquals(2, steps.size)
        assertEquals(true, steps[0].mods.ctrl)
        assertEquals(true, steps[1].mods.shift)
        assertEquals("t", steps[1].key)
    }

    @Test fun `space separated steps`() {
        assertEquals(2, ShortcutParser.parse("F12 h").size)
    }

    @Test fun `function keys`() {
        val steps = ShortcutParser.parse("F12, h")
        assertEquals("F12", steps[0].key)
        assertEquals("h", steps[1].key)
    }

    @Test fun `named keys`() {
        assertEquals(KeyChord.Named.ENTER, ShortcutParser.parse("Enter")[0].key)
        assertEquals(KeyChord.Named.BSPACE, ShortcutParser.parse("BSpace")[0].key)
        assertEquals(KeyChord.Named.PAGE_UP, ShortcutParser.parse("PageUp")[0].key)
    }

    @Test fun `dash and plus literals`() {
        assertEquals("-", ShortcutParser.parse("C-dash")[0].key)
        assertEquals("+", ShortcutParser.parse("plus")[0].key)
    }

    @Test fun `text mode sends literal`() {
        val steps = ShortcutParser.parse("text:/clear")
        assertEquals(6, steps.size)
        assertEquals("/", steps[0].key)
    }

    @Test fun `case preserved on bare letters`() {
        val steps = ShortcutParser.parse("T")
        assertEquals("T", steps[0].key)
    }

    @Test fun `rejects garbage`() {
        assertThrows(ShortcutParser.ParseException::class.java) { ShortcutParser.parse("F13") }
        assertThrows(ShortcutParser.ParseException::class.java) { ShortcutParser.parse("Contrl-b") }
        assertThrows(ShortcutParser.ParseException::class.java) { ShortcutParser.parse("") }
    }
}

class KeyEmitterTest {

    private fun emit(s: String, app: Boolean = false): String {
        val steps = ShortcutParser.parse(s)
        return steps.joinToString("") { String(KeyEmitter.emit(it, app)) }
    }

    @Test fun `ctrl c`() = assertEquals("\u0003", emit("C-c"))
    @Test fun `ctrl z`() = assertEquals("\u001A", emit("C-z"))
    @Test fun `ctrl b then capital T`() = assertEquals("\u0002T", emit("C-b, T"))
    @Test fun `prefix then shift t`() = assertEquals("\u0002T", emit("C-b, S-t"))
    @Test fun `f12 then h`() = assertEquals("\u001B[24~h", emit("F12, h"))
    @Test fun `shift tab`() = assertEquals("\u001B[Z", emit("S-Tab"))
    @Test fun `empty tmux prefix then c`() = assertEquals("\u0002c", emit("C-b, c"))
    @Test fun `ctrl dash`() = assertEquals("\u001F", emit("C-dash"))
    @Test fun `plain text`() = assertEquals("git status", emit("text:git status"))
    @Test fun `alt x`() = assertEquals("\u001Bx", emit("M-x"))
    @Test fun `arrows plain vs app cursor`() {
        assertEquals("\u001B[A", emit("Up", app = false))
        assertEquals("\u001BOA", emit("Up", app = true))
    }
    @Test fun `arrows with ctrl`() = assertEquals("\u001B[1;5A", emit("C-Up"))
    @Test fun `enter sends CR`() = assertEquals("\r", emit("Enter"))
    @Test fun `backspace sends DEL`() = assertEquals("\u007F", emit("BSpace"))
    @Test fun `ctrl backspace`() = assertEquals("\u0008", emit("C-BSpace"))
    @Test fun `f1 to f4`() {
        assertEquals("\u001BOP", emit("F1"))
        assertEquals("\u001BOQ", emit("F2"))
        assertEquals("\u001B[1;2P", emit("S-F1"))
    }
    @Test fun `f5`() = assertEquals("\u001B[15~", emit("F5"))
    @Test fun `home end page`() {
        assertEquals("\u001B[H", emit("Home"))
        assertEquals("\u001B[F", emit("End"))
        assertEquals("\u001B[5~", emit("PageUp"))
        assertEquals("\u001B[6;2~", emit("S-PageDown"))
    }
}