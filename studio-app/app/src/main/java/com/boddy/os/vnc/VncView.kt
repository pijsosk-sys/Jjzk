package com.boddy.os.vnc

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import android.text.InputType
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/**
 * Shows the remote desktop and turns touches into mouse input.
 *
 * Touchpad mode (default): drag one finger to move the pointer, tap to click,
 * tap with two fingers to right-click, drag two fingers to scroll, pinch to zoom,
 * and press-and-hold then drag to drag things around. When zoomed in, the view
 * follows the pointer.
 * Touch mode: the pointer jumps to where you touch.
 */
class VncView(context: Context, attrs: AttributeSet?) : View(context, attrs), RfbClient.Listener {

    var client: RfbClient? = null
    var touchpadMode = true
    var onDisconnected: ((Throwable?) -> Unit)? = null

    /** Modifiers toggled from the extra-keys bar; applied to the next key press. */
    val stickyModifiers = mutableSetOf<Int>()
    var onStickyConsumed: (() -> Unit)? = null

    private var bitmap: Bitmap? = null
    private val dirty = Rect()
    private val dest = RectF()
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var scale = 1f
    /** 1 = whole desktop fits the screen; up to [MAX_ZOOM]. */
    private var zoom = 1f
    private var viewLeft = 0f
    private var viewTop = 0f

    private var cursorX = 0f
    private var cursorY = 0f
    private var buttons = 0
    private val cursorPath = Path()
    private val cursorFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val cursorStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 2f
    }

    // Gesture tracking
    private var downTime = 0L
    private var lastX = 0f
    private var lastY = 0f
    private var travel = 0f
    private var maxPointers = 0
    private var dragging = false
    private var scrollAccum = 0f
    private var twoFingerMode = TwoFinger.UNDECIDED
    private var startSpan = 0f
    private var startZoom = 1f
    private val longPress = Runnable {
        if (travel < tapSlop && maxPointers == 1) {
            dragging = true
            setButtons(buttons or RfbClient.BUTTON_LEFT)
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }
    private val tapSlop = 12 * resources.displayMetrics.density
    private val scrollStep = 30 * resources.displayMetrics.density
    private val pinchSlop = 40 * resources.displayMetrics.density

    // Text composed by the soft keyboard but not yet committed (already typed remotely).
    private var composing = ""

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    // ---- Rendering --------------------------------------------------------------------

    override fun onResize(width: Int, height: Int) {
        cursorX = width / 2f
        cursorY = height / 2f
        post {
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            synchronized(dirty) { dirty.set(0, 0, width, height) }
            invalidate()
        }
    }

    override fun onUpdate(x: Int, y: Int, w: Int, h: Int) {
        synchronized(dirty) { dirty.union(x, y, x + w, y + h) }
        postInvalidateOnAnimation()
    }

    override fun onClipboard(text: String) {
        post {
            val cm = context.getSystemService(ClipboardManager::class.java)
            cm.setPrimaryClip(ClipData.newPlainText("Boddy", text))
        }
    }

    override fun onDisconnected(error: Throwable?) {
        post { onDisconnected?.invoke(error) }
    }

    override fun onDraw(canvas: Canvas) {
        val c = client ?: return
        val bmp = bitmap ?: return
        synchronized(c.lock) {
            if (c.width == bmp.width && c.height == bmp.height) {
                synchronized(dirty) {
                    dirty.intersect(0, 0, bmp.width, bmp.height)
                    if (!dirty.isEmpty) {
                        try {
                            bmp.setPixels(c.pixels, dirty.top * c.width + dirty.left, c.width,
                                dirty.left, dirty.top, dirty.width(), dirty.height())
                        } catch (_: RuntimeException) {
                            // The desktop was resized mid-frame; the next update redraws it.
                        }
                    }
                    dirty.setEmpty()
                }
            }
        }
        scale = min(width.toFloat() / bmp.width, height.toFloat() / bmp.height) * zoom
        val dw = bmp.width * scale
        val dh = bmp.height * scale
        viewLeft = follow(viewLeft, dw, width.toFloat(), cursorX * scale)
        viewTop = follow(viewTop, dh, height.toFloat(), cursorY * scale)
        dest.set(viewLeft, viewTop, viewLeft + dw, viewTop + dh)
        canvas.drawBitmap(bmp, null, dest, bitmapPaint)
        drawCursor(canvas)
    }

    /**
     * Where the desktop's edge goes on one axis: centred if it fits, otherwise scrolled
     * just enough to keep the pointer away from the screen edge.
     */
    private fun follow(current: Float, content: Float, viewport: Float, pointer: Float): Float {
        if (content <= viewport) return (viewport - content) / 2
        val margin = viewport * 0.2f
        var offset = current
        val onScreen = offset + pointer
        if (onScreen < margin) offset += margin - onScreen
        if (onScreen > viewport - margin) offset -= onScreen - (viewport - margin)
        return offset.coerceIn(viewport - content, 0f)
    }

    private fun span(e: MotionEvent): Float =
        if (e.pointerCount < 2) 0f else hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1))

    private fun drawCursor(canvas: Canvas) {
        val px = dest.left + cursorX * scale
        val py = dest.top + cursorY * scale
        val s = 1.4f * resources.displayMetrics.density
        cursorPath.reset()
        cursorPath.moveTo(px, py)
        cursorPath.lineTo(px, py + 17 * s)
        cursorPath.lineTo(px + 4.5f * s, py + 13 * s)
        cursorPath.lineTo(px + 8f * s, py + 20 * s)
        cursorPath.lineTo(px + 10.5f * s, py + 19 * s)
        cursorPath.lineTo(px + 7f * s, py + 12 * s)
        cursorPath.lineTo(px + 12f * s, py + 12 * s)
        cursorPath.close()
        canvas.drawPath(cursorPath, cursorFill)
        canvas.drawPath(cursorPath, cursorStroke)
    }

    // ---- Mouse ------------------------------------------------------------------------

    private fun sendPointer() {
        client?.sendPointer(cursorX.toInt(), cursorY.toInt(), buttons)
        invalidate()
    }

    private fun setButtons(mask: Int) {
        buttons = mask
        sendPointer()
    }

    private fun click(button: Int) {
        setButtons(buttons or button)
        setButtons(buttons and button.inv())
    }

    private fun moveTo(viewX: Float, viewY: Float) {
        val c = client ?: return
        cursorX = ((viewX - dest.left) / scale).coerceIn(0f, (c.width - 1).toFloat())
        cursorY = ((viewY - dest.top) / scale).coerceIn(0f, (c.height - 1).toFloat())
    }

    private fun moveBy(dx: Float, dy: Float) {
        val c = client ?: return
        // A little acceleration makes crossing the screen with a thumb practical.
        val speed = hypot(dx, dy)
        val accel = 1.2f + min(speed / 25f, 1.8f)
        cursorX = (cursorX + dx * accel / scale).coerceIn(0f, (c.width - 1).toFloat())
        cursorY = (cursorY + dy * accel / scale).coerceIn(0f, (c.height - 1).toFloat())
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (client == null || bitmap == null) return true
        val x = avg(e) { e.getX(it) }
        val y = avg(e) { e.getY(it) }

        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                requestFocus()
                downTime = SystemClock.uptimeMillis()
                lastX = x; lastY = y
                travel = 0f
                maxPointers = 1
                dragging = false
                scrollAccum = 0f
                if (touchpadMode) {
                    postDelayed(longPress, 450)
                } else {
                    moveTo(x, y)
                    setButtons(buttons or RfbClient.BUTTON_LEFT)
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                maxPointers = maxOf(maxPointers, e.pointerCount)
                removeCallbacks(longPress)
                if (e.pointerCount == 2) {
                    twoFingerMode = TwoFinger.UNDECIDED
                    startSpan = span(e)
                    startZoom = zoom
                }
                if (!touchpadMode && buttons and RfbClient.BUTTON_LEFT != 0) {
                    setButtons(buttons and RfbClient.BUTTON_LEFT.inv())
                }
                lastX = x; lastY = y
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // Re-anchor on the remaining fingers so the pointer doesn't jump.
                lastX = avgExcluding(e, e.actionIndex) { e.getX(it) }
                lastY = avgExcluding(e, e.actionIndex) { e.getY(it) }
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = x - lastX
                val dy = y - lastY
                lastX = x; lastY = y
                travel += abs(dx) + abs(dy)
                if (travel > tapSlop) removeCallbacks(longPress)

                if (e.pointerCount >= 2) {
                    // Decide once per gesture whether two fingers are pinching or scrolling.
                    val spanChange = span(e) - startSpan
                    if (twoFingerMode == TwoFinger.UNDECIDED) {
                        scrollAccum += dy
                        if (abs(spanChange) > pinchSlop) twoFingerMode = TwoFinger.PINCH
                        else if (abs(scrollAccum) > tapSlop) twoFingerMode = TwoFinger.SCROLL
                    } else if (twoFingerMode == TwoFinger.SCROLL) {
                        scrollAccum += dy
                    }
                    if (twoFingerMode == TwoFinger.PINCH) {
                        zoom = (startZoom * span(e) / startSpan.coerceAtLeast(1f)).coerceIn(1f, MAX_ZOOM)
                        invalidate()
                    }
                    while (twoFingerMode == TwoFinger.SCROLL && abs(scrollAccum) >= scrollStep) {
                        // Natural scrolling: fingers up moves content up.
                        click(if (scrollAccum < 0) RfbClient.WHEEL_DOWN else RfbClient.WHEEL_UP)
                        scrollAccum -= scrollStep * Math.signum(scrollAccum)
                    }
                } else if (touchpadMode) {
                    moveBy(dx, dy)
                    sendPointer()
                } else if (maxPointers == 1) {
                    moveTo(x, y)
                    sendPointer()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPress)
                val isTap = e.actionMasked == MotionEvent.ACTION_UP && travel < tapSlop &&
                    SystemClock.uptimeMillis() - downTime < 300
                when {
                    maxPointers >= 2 -> if (isTap && twoFingerMode == TwoFinger.UNDECIDED) click(RfbClient.BUTTON_RIGHT)
                    touchpadMode && dragging -> setButtons(buttons and RfbClient.BUTTON_LEFT.inv())
                    touchpadMode && isTap -> click(RfbClient.BUTTON_LEFT)
                    !touchpadMode -> setButtons(buttons and RfbClient.BUTTON_LEFT.inv())
                }
                dragging = false
            }
        }
        return true
    }

    private inline fun avg(e: MotionEvent, f: (Int) -> Float): Float {
        var sum = 0f
        for (i in 0 until e.pointerCount) sum += f(i)
        return sum / e.pointerCount
    }

    private inline fun avgExcluding(e: MotionEvent, skip: Int, f: (Int) -> Float): Float {
        if (e.pointerCount <= 1) return f(0)
        var sum = 0f
        for (i in 0 until e.pointerCount) if (i != skip) sum += f(i)
        return sum / (e.pointerCount - 1)
    }

    // ---- Keyboard ---------------------------------------------------------------------

    fun toggleKeyboard() {
        requestFocus()
        val imm = context.getSystemService(InputMethodManager::class.java)
        val shown = ViewCompat.getRootWindowInsets(this)?.isVisible(WindowInsetsCompat.Type.ime()) == true
        if (shown) {
            imm.hideSoftInputFromWindow(windowToken, 0)
        } else {
            imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    /** Presses and releases a key, wrapped in any sticky modifiers. */
    fun tapKey(keysym: Int) {
        val c = client ?: return
        val mods = stickyModifiers.toList()
        mods.forEach { c.sendKey(it, true) }
        c.sendKey(keysym, true)
        c.sendKey(keysym, false)
        mods.asReversed().forEach { c.sendKey(it, false) }
        if (mods.isNotEmpty()) {
            stickyModifiers.clear()
            onStickyConsumed?.invoke()
        }
    }

    /** Types the phone's clipboard into Linux (works with any characters, unlike VNC clipboard). */
    fun pasteFromPhone(): Boolean {
        val cm = context.getSystemService(ClipboardManager::class.java)
        val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context) ?: return false
        if (text.isEmpty()) return false
        typeText(text)
        return true
    }

    private fun typeText(text: CharSequence) {
        text.codePoints().forEach { tapKey(KeySyms.forChar(it)) }
    }

    private fun backspaces(n: Int) = repeat(n) { tapKey(KeySyms.BACKSPACE) }

    override fun onCheckIsTextEditor() = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        composing = ""
        return object : BaseInputConnection(this, false) {
            override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
                backspaces(composing.length)
                composing = ""
                typeText(text)
                return true
            }

            override fun setComposingText(text: CharSequence, newCursorPosition: Int): Boolean {
                val common = composing.commonPrefixWith(text)
                backspaces(composing.length - common.length)
                typeText(text.subSequence(common.length, text.length))
                composing = text.toString()
                return true
            }

            override fun finishComposingText(): Boolean {
                composing = ""
                return true
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                backspaces(beforeLength)
                repeat(afterLength) { tapKey(KeySyms.DELETE) }
                return true
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN) handleKey(event)
                return true
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
            keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) return super.onKeyDown(keyCode, event)
        return handleKey(event) || super.onKeyDown(keyCode, event)
    }

    private fun handleKey(event: KeyEvent): Boolean {
        val c = client ?: return false
        KeySyms.forKeyCode(event.keyCode)?.let { sym ->
            // Hardware keyboards: forward real modifier state.
            val mods = buildList {
                if (event.isCtrlPressed && sym != KeySyms.CONTROL_L) add(KeySyms.CONTROL_L)
                if (event.isAltPressed && sym != KeySyms.ALT_L) add(KeySyms.ALT_L)
                if (event.isShiftPressed && sym != KeySyms.SHIFT_L) add(KeySyms.SHIFT_L)
            }
            mods.forEach { c.sendKey(it, true) }
            tapKey(sym)
            mods.asReversed().forEach { c.sendKey(it, false) }
            return true
        }
        val unicode = event.getUnicodeChar(event.metaState and KeyEvent.META_CTRL_MASK.inv())
        if (unicode != 0) {
            if (event.isCtrlPressed) c.sendKey(KeySyms.CONTROL_L, true)
            tapKey(KeySyms.forChar(unicode))
            if (event.isCtrlPressed) c.sendKey(KeySyms.CONTROL_L, false)
            return true
        }
        return false
    }

    private enum class TwoFinger { UNDECIDED, SCROLL, PINCH }

    companion object {
        private const val MAX_ZOOM = 4f
    }
}
