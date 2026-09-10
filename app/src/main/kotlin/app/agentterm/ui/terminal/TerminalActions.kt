package app.agentterm.ui.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import app.agentterm.core.config.AppConfig
import app.agentterm.core.gestures.GestureAction
import app.agentterm.core.gestures.GestureBinding
import app.agentterm.core.config.bytes
import app.agentterm.core.terminal.TerminalSession

/** Dispatches a resolved gesture binding onto the current session/UI. */
class TerminalActions(
    private val context: Context,
    private val session: () -> TerminalSession?,
    private val config: () -> AppConfig,
    private val onUi: (UiAction) -> Unit,
) {
    enum class UiAction {
        TOGGLE_KEYBOARD, SHOW_KEYBOARD, HIDE_KEYBOARD,
        TOGGLE_DPAD, TOGGLE_SHORTCUTS, OPEN_PICKER, OPEN_AGENT_PALETTE,
        FONT_UP, FONT_DOWN, CLOSE_SESSION,
    }

    fun dispatch(b: GestureBinding) {
        val s = session() ?: return
        when (b.action) {
            GestureAction.NOOP -> {}
            GestureAction.PASTE -> paste(s)
            GestureAction.SEND_ESCAPE -> s.write("\u001B")
            GestureAction.SEND_TAB -> s.write("\t")
            GestureAction.SEND_ENTER -> s.write("\r")
            GestureAction.INTERRUPT -> s.write("\u0003")
            GestureAction.BACKSPACE -> s.write("\u007F")
            GestureAction.DELETE_LINE -> s.write("\u0015") // ^U: kill line
            GestureAction.SHOW_KEYBOARD -> onUi(UiAction.SHOW_KEYBOARD)
            GestureAction.HIDE_KEYBOARD -> onUi(UiAction.HIDE_KEYBOARD)
            GestureAction.TOGGLE_KEYBOARD -> onUi(UiAction.TOGGLE_KEYBOARD)
            GestureAction.TOGGLE_DPAD -> onUi(UiAction.TOGGLE_DPAD)
            GestureAction.TOGGLE_SHORTCUTS -> onUi(UiAction.TOGGLE_SHORTCUTS)
            GestureAction.FONT_UP -> onUi(UiAction.FONT_UP)
            GestureAction.FONT_DOWN -> onUi(UiAction.FONT_DOWN)
            GestureAction.ZOOM_PANE -> sendShortcut(s, "tmux-zoom")
            GestureAction.WINDOW_PREV -> sendShortcut(s, "tmux-prev")
            GestureAction.WINDOW_NEXT -> sendShortcut(s, "tmux-next")
            GestureAction.PANE_PREV -> sendShortcut(s, "tmux-pane-prev")
            GestureAction.PANE_NEXT -> sendShortcut(s, "tmux-pane-next")
            GestureAction.SESSION_PREV -> sendShortcut(s, "tmux-session-prev")
            GestureAction.SESSION_NEXT -> sendShortcut(s, "tmux-session-next")
            GestureAction.OPEN_PICKER -> onUi(UiAction.OPEN_PICKER)
            GestureAction.MINIMIZE_SESSION -> onUi(UiAction.CLOSE_SESSION)
            GestureAction.SEND_SHORTCUT -> config().shortcutById(b.shortcutId)?.let { s.write(it.bytes(s.screen.appCursorKeys)) }
            GestureAction.TOGGLE_CTRL_LOCK -> { /* handled by UI state */ }
            GestureAction.AGENT_PALETTE -> onUi(UiAction.OPEN_AGENT_PALETTE)
        }
    }

    fun sendRaw(bytes: ByteArray) { session()?.write(bytes) }

    private fun sendShortcut(s: TerminalSession, id: String) {
        config().shortcutById(id)?.let { s.write(it.bytes(s.screen.appCursorKeys)) }
    }

    private fun paste(s: TerminalSession) {
        val ctx = context.applicationContext
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString() ?: return
        if (text.isBlank()) { Toast.makeText(ctx, "Clipboard is empty", Toast.LENGTH_SHORT).show(); return }
        if (s.screen.bracketedPaste) s.write("\u001B[200~$text\u001B[201~") else s.write(text)
    }
}

/** Helpers to show/hide the soft keyboard for a raw View (TerminalView). */
object KeyboardUtil {
    fun show(view: View) {
        view.requestFocus()
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    fun hide(view: View) {
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    fun toggle(view: View) = if (isVisible(view)) hide(view) else show(view)
    fun isVisible(view: View): Boolean {
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        return imm.isAcceptingText
    }
}