package app.agentterm.core.gestures

import kotlinx.serialization.Serializable

/**
 * Gesture taxonomy, mirroring Moshi's four bindable surfaces:
 * terminal body, terminal header, toolbar buttons, and D-pad corner slots.
 * The Android gesture host maps raw MotionEvents into these intents.
 */
@Serializable
enum class Gesture {
    // -- terminal body -------------------------------------------------------
    TAP,
    DOUBLE_TAP,
    TRIPLE_TAP,
    SWIPE_LEFT,          // one finger: switch window/tab (tmux n/p)
    SWIPE_RIGHT,
    SWIPE_UP,            // one finger vertical: window/tab level (unused by default)
    SWIPE_DOWN,
    TWO_FINGER_SWIPE_LEFT,   // panes
    TWO_FINGER_SWIPE_RIGHT,
    TWO_FINGER_SWIPE_UP,     // tmux session / herdr workspace
    TWO_FINGER_SWIPE_DOWN,
    PINCH_IN,            // font size down (or zoom pane)
    PINCH_OUT,           // font size up
    SCROLL_PAST_BOTTOM,  // scroll beyond bottom: dismiss keyboard
    // -- terminal header -----------------------------------------------------
    HEADER_SOFT_PULL,    // open session switcher
    HEADER_HARD_PULL,    // minimize/close session
    // -- toolbar buttons (tap/long-press/double-tap) --------------------------
    KB_TAP,
    KB_LONG_PRESS,
    KB_DOUBLE_TAP,
    PASTE_TAP,
    PASTE_LONG_PRESS,
    ESC_TAP,
    ESC_LONG_PRESS,
    TAB_TAP,
    TAB_LONG_PRESS,
    CTRL_TAP,
    CTRL_DOUBLE_TAP,
    SHORTCUTS_TAP,
    SHORTCUTS_DOUBLE_TAP,
    // -- D-pad corner slots ---------------------------------------------------
    DPAD_TL_TAP,
    DPAD_TL_LONG_PRESS,
    DPAD_TR_TAP,
    DPAD_TR_LONG_PRESS,
    // -- special --------------------------------------------------------------
    HARDWARE_ANY,
}

/** Everything a gesture can be bound to. */
@Serializable
enum class GestureAction(val label: String) {
    NOOP("No-op"),
    PASTE("Paste"),
    SHOW_KEYBOARD("Show keyboard"),
    HIDE_KEYBOARD("Hide keyboard"),
    TOGGLE_KEYBOARD("Toggle keyboard"),
    SEND_ESCAPE("Send Escape"),
    SEND_TAB("Send Tab"),
    SEND_ENTER("Send Enter"),
    INTERRUPT("Interrupt (Ctrl+C)"),
    BACKSPACE("Backspace"),
    DELETE_LINE("Delete current line"),
    TOGGLE_DPAD("Toggle D-pad"),
    TOGGLE_SHORTCUTS("Toggle shortcuts panel"),
    FONT_UP("Font size up"),
    FONT_DOWN("Font size down"),
    ZOOM_PANE("Zoom tmux pane (prefix+z)"),
    WINDOW_PREV("Switch window/tab previous"),
    WINDOW_NEXT("Switch window/tab next"),
    PANE_PREV("Switch pane previous"),
    PANE_NEXT("Switch pane next"),
    SESSION_PREV("Switch session/workspace previous"),
    SESSION_NEXT("Switch session/workspace next"),
    OPEN_PICKER("Open session switcher"),
    MINIMIZE_SESSION("Close session"),
    SEND_SHORTCUT("Send custom shortcut…"),
    TOGGLE_CTRL_LOCK("Lock Ctrl for next keystrokes"),
    AGENT_PALETTE("Open agent palette"),
}

/** A binding: a gesture maps to an action (or a stored shortcut by id). */
@Serializable
data class GestureBinding(
    val gesture: Gesture,
    val action: GestureAction = GestureAction.NOOP,
    val shortcutId: String? = null,
)

val DEFAULT_BINDINGS: List<GestureBinding> = listOf(
    // Body — mirrors Moshi defaults
    GestureBinding(Gesture.TAP),
    GestureBinding(Gesture.DOUBLE_TAP, GestureAction.PASTE),
    GestureBinding(Gesture.TRIPLE_TAP),
    GestureBinding(Gesture.SWIPE_LEFT, GestureAction.WINDOW_PREV),
    GestureBinding(Gesture.SWIPE_RIGHT, GestureAction.WINDOW_NEXT),
    GestureBinding(Gesture.SWIPE_UP),
    GestureBinding(Gesture.SWIPE_DOWN),
    GestureBinding(Gesture.TWO_FINGER_SWIPE_LEFT, GestureAction.PANE_PREV),
    GestureBinding(Gesture.TWO_FINGER_SWIPE_RIGHT, GestureAction.PANE_NEXT),
    GestureBinding(Gesture.TWO_FINGER_SWIPE_UP, GestureAction.SESSION_NEXT),
    GestureBinding(Gesture.TWO_FINGER_SWIPE_DOWN, GestureAction.SESSION_PREV),
    GestureBinding(Gesture.PINCH_IN, GestureAction.FONT_DOWN),
    GestureBinding(Gesture.PINCH_OUT, GestureAction.FONT_UP),
    GestureBinding(Gesture.SCROLL_PAST_BOTTOM, GestureAction.HIDE_KEYBOARD),
    // Header
    GestureBinding(Gesture.HEADER_SOFT_PULL, GestureAction.OPEN_PICKER),
    GestureBinding(Gesture.HEADER_HARD_PULL, GestureAction.MINIMIZE_SESSION),
    // Toolbar
    GestureBinding(Gesture.KB_TAP, GestureAction.TOGGLE_KEYBOARD),
    GestureBinding(Gesture.KB_LONG_PRESS, GestureAction.TOGGLE_SHORTCUTS),
    GestureBinding(Gesture.PASTE_TAP, GestureAction.PASTE),
    GestureBinding(Gesture.ESC_TAP, GestureAction.SEND_ESCAPE),
    GestureBinding(Gesture.TAB_TAP, GestureAction.SEND_TAB),
    GestureBinding(Gesture.CTRL_TAP, GestureAction.TOGGLE_CTRL_LOCK),
    GestureBinding(Gesture.SHORTCUTS_TAP, GestureAction.TOGGLE_SHORTCUTS),
    // D-pad slots
    GestureBinding(Gesture.DPAD_TL_TAP, GestureAction.INTERRUPT),
    GestureBinding(Gesture.DPAD_TR_TAP, GestureAction.BACKSPACE),
)

/** Mutable per-user map. Serialized to JSON by AppConfig. */
class GestureMap(bindings: List<GestureBinding> = DEFAULT_BINDINGS) {
    private val map = HashMap<Gesture, GestureBinding>()
    init { bindings.forEach { map[it.gesture] = it } }

    fun binding(g: Gesture): GestureBinding = map[g] ?: GestureBinding(g)
    fun bind(b: GestureBinding) { map[b.gesture] = b }
    fun all(): List<GestureBinding> = map.values.toList()
    fun reset() { map.clear(); DEFAULT_BINDINGS.forEach { map[it.gesture] = it } }
    fun toList(): List<GestureBinding> = Gesture.entries.mapNotNull { map[it] }
}