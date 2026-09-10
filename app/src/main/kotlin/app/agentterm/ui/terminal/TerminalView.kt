package app.agentterm.ui.terminal

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.InputType
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import app.agentterm.R
import app.agentterm.core.terminal.Cell
import app.agentterm.core.terminal.CellColor
import app.agentterm.core.terminal.ColorResolver
import app.agentterm.core.terminal.TerminalScreen
import app.agentterm.ui.theme.TerminalDefaultBg
import app.agentterm.ui.theme.TerminalDefaultFg
import kotlin.math.max

/**
 * Canvas terminal renderer. Draws runs of identically-styled cells per line
 * (monospace metrics => tight run grouping), including scrollback. Color
 * resolution uses the JVM-core [ColorResolver] with the app palette so engine
 * logic stays device-independent.
 */
class TerminalView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = try {
            android.graphics.Typeface.create(resources.getFont(R.font.jetbrains_mono), Typeface.NORMAL)
        } catch (e: Exception) { Typeface.create("monospace", Typeface.NORMAL) }
        textSize = 11f * resources.displayMetrics.density
    }

    var fontScale: Float = 1f
        set(value) { field = value; recalcMetrics() }

    var screen: TerminalScreen? = null
        set(value) { field = value; recalcMetrics() }
    var onCharInput: ((Char) -> Unit)? = null
    var onKeyInput: ((KeyEvent) -> Unit)? = null

    private var lineHeight = 20f
    private var charWidth = 8f
    var columns = 40
        private set
    var rows = 20
        private set

    private val bgPaint = Paint().apply { color = TerminalDefaultBg }
    private var lastInvalidate = 0L
    private var dirtyScheduled = false

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
        recalcMetrics()
    }

    private fun recalcMetrics() {
        val size = 12f * resources.displayMetrics.density * fontScale
        textPaint.textSize = size
        val fm = textPaint.fontMetrics
        lineHeight = (fm.descent - fm.ascent) * 1.06f
        charWidth = textPaint.measureText("M")
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recalcMetrics()
        if (charWidth <= 0f) return
        val newCols = max(4, (w / charWidth).toInt())
        val newRows = max(4, (h / lineHeight).toInt())
        if (newCols != columns || newRows != rows) {
            columns = newCols; rows = newRows
            onGridSizeChange?.invoke(columns, rows)
        }
    }

    var onGridSizeChange: ((cols: Int, rows: Int) -> Unit)? = null

    /** Called (from any thread) when the buffer changed. */
    fun requestInvalidate() {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastInvalidate > 16) {
            lastInvalidate = now
            postInvalidateOnAnimation()
        } else if (!dirtyScheduled) {
            dirtyScheduled = true
            post { dirtyScheduled = false; invalidate() }
        }
    }

    override fun onDraw(canvas: Canvas) {
        val s = screen ?: return
        columns = s.columns; rows = s.rows
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val total = s.totalLines()
        val first = max(0, total - rows)
        var y = 0f
        for (gi in first until minOf(first + rows, total)) {
            drawRow(canvas, s.rowAt(gi), y)
            y += lineHeight
        }
    }

    private fun drawRow(canvas: Canvas, cells: Array<Cell>, y: Float) {
        if (cells.isEmpty()) return
        var i = 0
        while (i < cells.size) {
            val cell = cells[i]
            // background run
            val bg = effectiveBg(cell)
            var j = i
            while (j < cells.size && effectiveBg(cells[j]) == bg) j++
            if (bg != TerminalDefaultBg) {
                bgPaint.color = bg
                canvas.drawRect(i * charWidth, y, j * charWidth, y + lineHeight, bgPaint)
            }
            // text run: contiguous cells with identical fg + boldness
            var k = i
            while (k < cells.size && effectiveFg(cells[k]) == effectiveFg(cell) && isBold(cells[k]) == isBold(cell)) k++
            val baseline = y - textPaint.fontMetrics.ascent
            for (t in i until k) {
                val c = cells[t]
                if (c.ch == ' ') continue
                textPaint.color = effectiveFg(c)
                textPaint.isFakeBoldText = c.flags and Cell.BOLD != 0
                textPaint.alpha = if (c.flags and Cell.CONCEAL != 0) 0 else 255
                canvas.drawText(c.ch.toString(), t * charWidth, baseline, textPaint)
            }
            i = k
        }
    }

    private fun isBold(c: Cell) = c.flags and Cell.BOLD != 0

    private fun effectiveFg(c: Cell): Int {
        val normal = ColorResolver.toArgb(c.fg, TerminalDefaultFg, TerminalDefaultBg)
        return if (c.flags and Cell.INVERSE != 0) {
            ColorResolver.toArgb(c.bg, TerminalDefaultBg, TerminalDefaultFg)
        } else normal
    }

    private fun effectiveBg(c: Cell): Int {
        val normal = ColorResolver.toArgb(c.bg, TerminalDefaultBg, TerminalDefaultFg)
        return if (c.flags and Cell.INVERSE != 0) {
            ColorResolver.toArgb(c.fg, TerminalDefaultFg, TerminalDefaultBg)
        } else normal
    }

    // ------------------------------------------------------------- input path
    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_ENTER_ACTION or
            EditorInfo.IME_ACTION_NONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        return object : BaseInputConnection(this, true) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                text?.forEach { onCharInput?.invoke(it) }
                return true
            }
            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                repeat(beforeLength) { onCharInput?.invoke('\u007f') }
                return true
            }
            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_DEL && event.action == KeyEvent.ACTION_DOWN) {
                    onCharInput?.invoke('\u007f')
                    return true
                }
                return super.sendKeyEvent(event)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DEL) { onCharInput?.invoke('\u007f'); return true }
        if (keyCode == KeyEvent.KEYCODE_ENTER) { onCharInput?.invoke('\r'); return true }
        onKeyInput?.invoke(event)
        return true
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean = true
}