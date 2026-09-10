package app.agentterm.core.terminal

/**
 * A terminal session = parser + screen + an I/O channel.
 * Subclasses implement [write] (pty master fd, ssh channel, or a test sink).
 * The engine itself is JVM-only; I/O threading lives in the Android layer.
 */
abstract class TerminalSession(
    initialCols: Int = 80,
    initialRows: Int = 24,
) {
    val screen = TerminalScreen(initialCols, initialRows)
    private val emitter = object : VtParser.Emitter {
        override fun onTitle(title: String) { this@TerminalSession.onTitle(title) }
        override fun onClipboard(text: String) { this@TerminalSession.onClipboard(text) }
        override fun onBell() { this@TerminalSession.onBell() }
        override fun onCwd(path: String) { this@TerminalSession.onCwd(path) }
        override fun response(bytes: ByteArray) { write(bytes) }
    }
    val parser = VtParser(screen, emitter)

    /** Feed host output into the terminal. */
    fun feed(data: ByteArray, len: Int = data.size) {
        parser.feed(data, len)
        onScreenUpdated()
        onBufferUpdated?.invoke()
    }

    /** Called on the I/O thread after every feed; set by the UI layer for invalidation. */
    var onBufferUpdated: (() -> Unit)? = null

    /** Send bytes to the host (pty/ssh). Subclass responsibility. */
    abstract fun write(bytes: ByteArray)

    /** Convenience string write (UTF-8). */
    fun write(s: String) = write(s.toByteArray(Charsets.UTF_8))

    fun resize(cols: Int, rows: Int) {
        val cc = cols.coerceIn(2, 1000)
        val rr = rows.coerceIn(2, 500)
        if (cc != screen.columns || rr != screen.rows) {
            screen.resize(cc, rr)
            onResize(cc, rr)
            onScreenUpdated()
        }
    }

    // -- hooks for subclasses / UI -----------------------------------------
    open fun onTitle(title: String) {}
    open fun onClipboard(text: String) {}
    open fun onBell() {}
    open fun onCwd(path: String) {}
    open fun onScreenUpdated() {}
    open fun onResize(cols: Int, rows: Int) {}
    open fun close() {}
}

/** In-memory session useful for tests and previews. */
class BufferSession(
    cols: Int = 80,
    rows: Int = 24,
    val sink: StringBuilder = StringBuilder(),
) : TerminalSession(cols, rows) {
    override fun write(bytes: ByteArray) { sink.append(String(bytes, Charsets.UTF_8)) }
    /** Read back the visible screen buffer without ANSI for assertions. */
    fun plainRow(r: Int): String = buildString { for (c in screen.line(r)) append(c.ch) }
    fun plainScreen(): List<String> = (0 until screen.rows).map { plainRow(it) }
    fun plainText(): String = plainScreen().joinToString("\n") { it.trimEnd() }
}