package app.agentterm.core.gestures

import app.agentterm.core.config.AppConfig
import app.agentterm.core.config.ConfigCodec
import app.agentterm.core.config.gestureMap
import app.agentterm.core.config.shortcutById
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class GestureMapTest {

    @Test fun `defaults mirror moshi`() {
        val m = GestureMap()
        assertEquals(GestureAction.PASTE, m.binding(Gesture.DOUBLE_TAP).action)
        assertEquals(GestureAction.WINDOW_PREV, m.binding(Gesture.SWIPE_LEFT).action)
        assertEquals(GestureAction.WINDOW_NEXT, m.binding(Gesture.SWIPE_RIGHT).action)
        assertEquals(GestureAction.PANE_PREV, m.binding(Gesture.TWO_FINGER_SWIPE_LEFT).action)
        assertEquals(GestureAction.PANE_NEXT, m.binding(Gesture.TWO_FINGER_SWIPE_RIGHT).action)
        assertEquals(GestureAction.SESSION_NEXT, m.binding(Gesture.TWO_FINGER_SWIPE_UP).action)
        assertEquals(GestureAction.SESSION_PREV, m.binding(Gesture.TWO_FINGER_SWIPE_DOWN).action)
        assertEquals(GestureAction.OPEN_PICKER, m.binding(Gesture.HEADER_SOFT_PULL).action)
        assertEquals(GestureAction.MINIMIZE_SESSION, m.binding(Gesture.HEADER_HARD_PULL).action)
        assertEquals(GestureAction.INTERRUPT, m.binding(Gesture.DPAD_TL_TAP).action)
        assertEquals(GestureAction.BACKSPACE, m.binding(Gesture.DPAD_TR_TAP).action)
        assertEquals(GestureAction.NOOP, m.binding(Gesture.TAP).action)
    }

    @Test fun `rebind and reset`() {
        val m = GestureMap()
        m.bind(GestureBinding(Gesture.TAP, GestureAction.SEND_ESCAPE))
        assertEquals(GestureAction.SEND_ESCAPE, m.binding(Gesture.TAP).action)
        m.reset()
        assertEquals(GestureAction.NOOP, m.binding(Gesture.TAP).action)
    }

    @Test fun `config json roundtrip preserves custom bindings`() {
        val cfg = AppConfig(
            gestureBindings = listOf(
                GestureBinding(Gesture.TAP, GestureAction.SEND_ESCAPE),
                GestureBinding(Gesture.DOUBLE_TAP, GestureAction.SEND_SHORTCUT, shortcutId = "xyz"),
            ),
            shortcuts = emptyList(),
        )
        val raw = ConfigCodec.encode(cfg)
        val back = ConfigCodec.decode(raw)
        assertEquals(GestureAction.SEND_ESCAPE, back.gestureMap().binding(Gesture.TAP).action)
        assertEquals("xyz", back.gestureMap().binding(Gesture.DOUBLE_TAP).shortcutId)
    }

    @Test fun `corrupt json falls back to defaults`() {
        val cfg = ConfigCodec.decode("{not json")
        assertNotNull(cfg.gestureMap().binding(Gesture.DOUBLE_TAP))
        assertEquals(GestureAction.PASTE, cfg.gestureMap().binding(Gesture.DOUBLE_TAP).action)
    }

    @Test fun `shortcut by id`() {
        val cfg = AppConfig()
        assertEquals("tmux: next window", cfg.shortcutById("tmux-next")?.name)
        assertEquals(null, cfg.shortcutById("nope"))
    }
}