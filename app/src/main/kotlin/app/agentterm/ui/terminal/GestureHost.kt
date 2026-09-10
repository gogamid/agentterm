package app.agentterm.ui.terminal

import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import app.agentterm.core.gestures.Gesture
import kotlin.math.abs

/**
 * Maps raw MotionEvents on the terminal body into [Gesture] intents:
 * single/double/triple taps, one-finger swipes (with scroll-past-bottom),
 * two-finger swipes, and pinch. The screen's controller then consults the
 * user's gesture bindings — this host knows nothing about actions.
 */
class GestureHost(
    private val view: View,
    private val onGesture: (Gesture) -> Unit,
) : View.OnTouchListener {

    private var totalScale = 1f
    private var pinching = false
    private var twoFinger = false
    private var prevCX = 0f
    private var prevCY = 0f
    private var centroidDx = 0f
    private var centroidDy = 0f
    private var lastDblTap = 0L

    private val gd = GestureDetector(view.context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            val now = System.currentTimeMillis()
            onGesture(if (now - lastDblTap < 280) Gesture.TRIPLE_TAP else Gesture.TAP)
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            lastDblTap = System.currentTimeMillis()
            onGesture(Gesture.DOUBLE_TAP)
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            if (e1 == null) return false
            val dx = e2.x - e1.x
            val dy = e2.y - e1.y
            val h = view.height
            if (abs(dy) > abs(dx)) {
                if (dy > 0 && e1.y > h * 0.62f && abs(vy) > 500f) {
                    onGesture(Gesture.SCROLL_PAST_BOTTOM)
                } else if (dy > 200f) onGesture(Gesture.SWIPE_DOWN)
                else if (dy < -200f) onGesture(Gesture.SWIPE_UP)
            } else {
                if (dx < -200f) onGesture(Gesture.SWIPE_LEFT) else if (dx > 200f) onGesture(Gesture.SWIPE_RIGHT)
            }
            return true
        }
    })

    private val sd = ScaleGestureDetector(view.context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            totalScale = 1f
            pinching = true
            twoFinger = true
            return true
        }
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            totalScale *= detector.scaleFactor
            return true
        }
    })

    override fun onTouch(v: View, e: MotionEvent): Boolean {
        gd.onTouchEvent(e)
        sd.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                twoFinger = true
                centroidDx = 0f; centroidDy = 0f
                prevCX = (e.getX(0) + e.getX(1)) / 2f
                prevCY = (e.getY(0) + e.getY(1)) / 2f
            }
            MotionEvent.ACTION_MOVE -> {
                if (twoFinger && e.pointerCount >= 2) {
                    val cx = (e.getX(0) + e.getX(1)) / 2f
                    val cy = (e.getY(0) + e.getY(1)) / 2f
                    centroidDx += cx - prevCX
                    centroidDy += cy - prevCY
                    prevCX = cx; prevCY = cy
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                when {
                    twoFinger && pinching && totalScale > 1.12f -> onGesture(Gesture.PINCH_OUT)
                    twoFinger && pinching && totalScale < 0.9f -> onGesture(Gesture.PINCH_IN)
                    twoFinger && abs(centroidDx) > abs(centroidDy) && abs(centroidDx) > 80f ->
                        onGesture(if (centroidDx < 0) Gesture.TWO_FINGER_SWIPE_LEFT else Gesture.TWO_FINGER_SWIPE_RIGHT)
                    twoFinger && abs(centroidDy) > 80f ->
                        onGesture(if (centroidDy < 0) Gesture.TWO_FINGER_SWIPE_UP else Gesture.TWO_FINGER_SWIPE_DOWN)
                }
                twoFinger = false; pinching = false; totalScale = 1f
                centroidDx = 0f; centroidDy = 0f
            }
        }
        return true
    }
}