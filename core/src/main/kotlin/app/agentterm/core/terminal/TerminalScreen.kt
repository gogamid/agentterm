package app.agentterm.core.terminal

/** Per-cell terminal state. */
class Cell {
    var ch: Char = ' '
    var fg: CellColor = CellColor.DefaultFg
    var bg: CellColor = CellColor.DefaultBg
    var flags: Int = 0

    companion object {
        const val BOLD = 1
        const val DIM = 2
        const val ITALIC = 4
        const val UNDERLINE = 8
        const val BLINK = 16
        const val INVERSE = 32
        const val STRIKE = 64
        const val CONCEAL = 128
    }

    fun reset() { ch = ' '; fg = CellColor.DefaultFg; bg = CellColor.DefaultBg; flags = 0 }
    fun set(a: Attrs, c: Char) { ch = c; fg = a.fg; bg = a.bg; flags = a.flags }
    fun copyFrom(o: Cell) { ch = o.ch; fg = o.fg; bg = o.bg; flags = o.flags }
}

/** Current output attributes applied to subsequently written cells. */
class Attrs {
    var fg: CellColor = CellColor.DefaultFg
    var bg: CellColor = CellColor.DefaultBg
    var flags: Int = 0
    fun reset() { fg = CellColor.DefaultFg; bg = CellColor.DefaultBg; flags = 0 }
    fun copyFrom(o: Attrs) { fg = o.fg; bg = o.bg; flags = o.flags }
}

/**
 * The terminal screen model: a fixed grid plus a scrollback ring.
 * Pure JVM — no Android types. Cursor (row, col) is 0-based, rows from top.
 * Scrollback is addressed through rowAt(globalRow) where
 * globalRow < scrollbackSize means history, else the live grid.
 */
class TerminalScreen(
    var columns: Int,
    var rows: Int,
    val maxScrollback: Int = 8000,
) {
    internal var grid: Array<Array<Cell>> = newGrid(rows, columns)
    private var altGrid: Array<Array<Cell>>? = null
    private val scrollback = ArrayDeque<Array<Cell>>()

    var cursorCol = 0
    var cursorRow = 0
    var scrollTop = 0
    var scrollBottom = rows - 1

    val attrs = Attrs()
    private var wrapPending = false

    // Modes
    var wrap = true
    var insertEnabled = false
    var originMode = false
    var cursorVisible = true
    var appCursorKeys = false
    var appKeypad = false
    var reverseVideo = false
    var bracketedPaste = false
    var mouseMode = 0      // 0=off, 1000=click, 1002=drag, 1003=any
    var mouseSgr = false
    var focusEvents = false
    var altScreenActive = false

    private var savedCol = 0
    private var savedRow = 0
    private val savedAttrs = Attrs()
    private var savedCursorVisible = true

    private var lastChar = ' '

    // ---------------------------------------------------------------- helpers
    private fun newGrid(r: Int, c: Int): Array<Array<Cell>> = Array(r) { Array(c) { Cell() } }
    private fun newRow(c: Int): Array<Cell> = Array(c) { Cell() }
    fun cell(row: Int, col: Int): Cell = grid[row][col]
    fun line(row: Int): Array<Cell> = grid[row]
    val scrollbackSize: Int get() = scrollback.size
    fun scrollbackLine(i: Int): Array<Cell> = scrollback.elementAt(i)
    fun totalLines(): Int = scrollback.size + rows

    // ------------------------------------------------------------ cursor ops
    fun cr() { cursorCol = 0; wrapPending = false }
    fun newline() {
        wrapPending = false
        if (cursorRow < scrollBottom) cursorRow++
        else if (scrollTop == 0 && scrollBottom == rows - 1) scrollWholeScreen(1)
        else scrollRegionUp(1)
    }
    fun lineFeed() = newline()
    fun backspace() { if (cursorCol > 0) cursorCol--; wrapPending = false }
    fun tab() {
        wrapPending = false
        cursorCol = minOf(((cursorCol / 8) + 1) * 8, columns - 1)
    }
    fun setTab() { tabStops.add(cursorCol) }
    fun clearTab() { tabStops.remove(cursorCol) }
    fun clearAllTabs() { tabStops.clear() }

    fun cursorUp(n: Int) { cursorRow = maxOf(scrollTop, cursorRow - maxOf(n, 1)) }
    fun cursorDown(n: Int) { cursorRow = minOf(scrollBottom, cursorRow + maxOf(n, 1)) }
    fun cursorLeft(n: Int) { cursorCol = maxOf(0, cursorCol - maxOf(n, 1)); wrapPending = false }
    fun cursorRight(n: Int) { cursorCol = minOf(columns - 1, cursorCol + maxOf(n, 1)); wrapPending = false }
    fun cursorNextLine(n: Int) { cursorCol = 0; cursorDown(n) }
    fun cursorPrevLine(n: Int) { cursorCol = 0; cursorUp(n) }
    fun cursorColumn(c: Int) { cursorCol = (if (originMode) scrollTop + c else c).coerceIn(0, columns - 1); wrapPending = false }
    fun cursorRowPos(r: Int) { cursorRow = (if (originMode) scrollTop + r else r).coerceIn(scrollTop, scrollBottom); wrapPending = false }
    fun cursorPosition(row1: Int, col1: Int) { cursorRowPos(maxOf(0, row1 - 1)); cursorColumn(maxOf(0, col1 - 1)) }

    fun saveCursor() { savedCol = cursorCol; savedRow = cursorRow; savedAttrs.copyFrom(attrs) }
    fun restoreCursor() { cursorCol = savedCol; cursorRow = savedRow; attrs.copyFrom(savedAttrs); wrapPending = false }

    fun setScrollRegion(top: Int, bottom: Int) {
        scrollTop = top.coerceIn(0, rows - 1)
        scrollBottom = bottom.coerceIn(scrollTop, rows - 1)
        cursorRow = scrollTop; cursorCol = 0
    }
    fun resetScrollRegion() { scrollTop = 0; scrollBottom = rows - 1 }

    // -------------------------------------------------------------- char write
    fun handleControl(c: Char) {
        when (c) {
            '\n', '\u000B', '\u000C' -> newline()
            '\r' -> cr()
            '\b' -> backspace()
            '\t' -> tab()
        }
    }

    fun writeChar(c: Char) {
        if (wrapPending) {
            wrapPending = false
            if (wrap) { newline(); cursorCol = 0 } else cursorCol = columns - 1
        }
        if (cursorRow < scrollTop || cursorRow > scrollBottom || cursorCol !in 0 until columns) return
        if (insertEnabled) insertSpacesAtCursor(1)
        grid[cursorRow][cursorCol].set(attrs, if (isAcs) acsTransform(c) else c)
        lastChar = c
        if (cursorCol + 1 < columns) cursorCol++ else wrapPending = true
    }

    fun writeText(s: String) { for (c in s) writeChar(c) }
    fun repeatLast(n: Int) { repeat(n) { writeChar(lastChar) } }

    // ----------------------------------------------------------------- erases
    fun eraseDisplay(mode: Int) {
        when (mode) {
            0 -> { eraseRowFrom(cursorRow, cursorCol); for (r in cursorRow + 1..scrollBottom) eraseRow(r) }
            1 -> { eraseRowTo(cursorRow, cursorCol); for (r in scrollTop until cursorRow) eraseRow(r) }
            2 -> for (r in 0 until rows) eraseRow(r)
            3 -> scrollback.clear()
        }
    }

    fun eraseLine(mode: Int) {
        when (mode) {
            0 -> eraseRowFrom(cursorRow, cursorCol)
            1 -> eraseRowTo(cursorRow, cursorCol)
            2 -> eraseRow(cursorRow)
        }
    }

    fun eraseChars(n: Int) {
        val end = minOf(columns - 1, cursorCol + maxOf(0, n) - 1)
        for (c in cursorCol..end) grid[cursorRow][c].set(attrs, ' ')
    }

    fun insertChars(n: Int) = insertSpacesAtCursor(maxOf(0, n))

    private fun insertSpacesAtCursor(n: Int) {
        val row = grid[cursorRow]
        val amt = minOf(n, columns - cursorCol)
        for (c in (columns - 1) downTo (cursorCol + amt)) row[c].copyFrom(row[c - amt])
        for (c in cursorCol until minOf(columns, cursorCol + amt)) row[c].set(attrs, ' ')
    }

    fun deleteChars(n: Int) {
        val row = grid[cursorRow]
        val amt = minOf(n, columns - cursorCol)
        for (c in cursorCol until (columns - amt)) row[c].copyFrom(row[c + amt])
        for (c in (columns - amt) until columns) row[c].set(attrs, ' ')
    }

    private fun eraseRow(r: Int) { for (c in grid[r]) c.set(attrs, ' ') }
    private fun eraseRowFrom(r: Int, colFrom: Int) { for (c in colFrom until columns) grid[r][c].set(attrs, ' ') }
    private fun eraseRowTo(r: Int, colTo: Int) { for (c in 0..colTo) grid[r][c].set(attrs, ' ') }

    // -------------------------------------------------------------- scroll ops
    /**
     * Scroll the full screen by n, pushing lines into scrollback.
     * Every pushed row leaves the grid, so no aliasing occurs; the bottom is a
     * fresh blank row.
     */
    fun scrollWholeScreen(n: Int) {
        var count = maxOf(0, n).coerceAtMost(rows)
        repeat(count) {
            if (scrollback.size >= maxScrollback) scrollback.removeFirst()
            scrollback.addLast(grid[0])
            for (r in 0 until rows - 1) grid[r] = grid[r + 1]
            grid[rows - 1] = newRow(columns)
        }
    }

    fun scrollRegionUp(n: Int) {
        if (scrollTop == 0 && scrollBottom == rows - 1) { scrollWholeScreen(n); return }
        val count = maxOf(0, n).coerceAtMost(scrollBottom - scrollTop + 1)
        for (r in scrollTop until scrollBottom - count + 1) swapLines(r, r + count)
        for (r in (scrollBottom - count + 1)..scrollBottom) eraseRowRegion(r)
    }

    fun scrollRegionDown(n: Int) {
        val count = maxOf(0, n).coerceAtMost(scrollBottom - scrollTop + 1)
        for (r in scrollBottom downTo scrollTop + count) swapLines(r, r - count)
        for (r in scrollTop until scrollTop + count) eraseRowRegion(r)
    }

    private fun eraseRowRegion(r: Int) { for (c in grid[r]) c.set(attrs, ' ') }
    private fun swapLines(a: Int, b: Int) { val t = grid[a]; grid[a] = grid[b]; grid[b] = t }

    fun insertLines(n: Int) {
        if (cursorRow < scrollTop || cursorRow > scrollBottom) return
        val count = maxOf(0, n).coerceAtMost(scrollBottom - cursorRow + 1)
        for (r in scrollBottom downTo cursorRow + count) swapLines(r, r - count)
        for (r in cursorRow until cursorRow + count) eraseRowRegion(r)
    }

    fun deleteLines(n: Int) {
        if (cursorRow < scrollTop || cursorRow > scrollBottom) return
        val count = maxOf(0, n).coerceAtMost(scrollBottom - cursorRow + 1)
        for (r in cursorRow until scrollBottom - count + 1) swapLines(r, r + count)
        for (r in (scrollBottom - count + 1)..scrollBottom) eraseRowRegion(r)
    }

    // ----------------------------------------------------------------- screen
    fun clearAll() {
        for (r in 0 until rows) for (c in grid[r]) c.reset()
        cursorCol = 0; cursorRow = 0; wrapPending = false
    }

    fun enterAltScreen() {
        if (altScreenActive) return
        saveCursor()
        altGrid = grid
        grid = newGrid(rows, columns)
        altScreenActive = true
        cursorCol = 0; cursorRow = 0
    }

    fun exitAltScreen() {
        if (!altScreenActive) return
        grid = altGrid ?: newGrid(rows, columns)
        altGrid = null
        altScreenActive = false
        restoreCursor()
    }

    // ---------------------------------------------------------------- charset
    var g0Acs = false
    var g1Acs = false
    private var activeG = 0 // 0=G0, 1=G1
    val isAcs: Boolean get() = if (activeG == 0) g0Acs else g1Acs

    fun selectCharset(g: Int, acs: Boolean) { if (g == 0) g0Acs = acs else g1Acs = acs }
    fun shiftIn() { activeG = 0 }
    fun shiftOut() { activeG = 1 }

    private fun acsTransform(c: Char): Char = when (c) {
        'j' -> '\u2518'; 'k' -> '\u2510'; 'l' -> '\u250C'; 'm' -> '\u2514'
        'n' -> '\u253C'; 'q' -> '\u2500'; 't' -> '\u251C'; 'u' -> '\u2524'
        'v' -> '\u2534'; 'w' -> '\u252C'; 'x' -> '\u2502'
        'a' -> '\u2592'; 'f' -> '\u00B0'; 'g' -> '\u03B1'; 'h' -> '\u03C0'
        '`' -> '\u25C6'; '0' -> '\u25AE'
        else -> c
    }

    // ---------------------------------------------------------------- resize
    fun resize(newCols: Int, newRows: Int) {
        if (newCols == columns && newRows == rows) return
        val g = newGrid(newRows, newCols)
        val copyRows = minOf(rows, newRows)
        val copyCols = minOf(columns, newCols)
        for (r in 0 until copyRows) for (c in 0 until copyCols) g[r][c].copyFrom(grid[r][c])
        grid = g
        columns = newCols; rows = newRows
        cursorCol = cursorCol.coerceIn(0, columns - 1)
        cursorRow = cursorRow.coerceIn(0, rows - 1)
        if (cursorRow > scrollBottom) cursorRow = scrollBottom
        scrollBottom = rows - 1
        wrapPending = false
        tabStops.clear(); var t = 8; while (t < columns) { tabStops.add(t); t += 8 }
    }

    /** Row lookup for rendering with scrollback. globalRow >= scrollbackSize => live grid. */
    fun rowAt(globalRow: Int): Array<Cell> =
        if (globalRow < scrollback.size) scrollback.elementAt(globalRow) else grid[globalRow - scrollback.size]

    fun softReset() {
        attrs.reset()
        wrap = true; insertEnabled = false; originMode = false
        cursorVisible = true
        cursorCol = 0; cursorRow = 0
        resetScrollRegion()
        wrapPending = false
    }

    private val tabStops = HashSet<Int>()
    init { for (t in 8 until columns step 8) tabStops.add(t) }
}