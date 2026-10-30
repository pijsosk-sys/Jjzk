package com.boddy.os

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Boddy's "hands" on the phone: an accessibility service that lets AI agents in Linux see the
 * phone screen and tap, hold, swipe and type in other apps (Minecraft, DroidBridge...).
 * Turned on by the user in Settings → Accessibility; driven through [PhoneBridge].
 *
 * Coordinates use "phone pixels": the screen scaled so its long side is at most [MAX_SIDE],
 * the same size as the screenshots, so the AI can tap what it sees.
 *
 * While the AI is in control a small bubble shows on top of every app; tapping it pauses all
 * AI input until it's tapped again.
 */
class PhoneControl : AccessibilityService() {

    private val main = Handler(Looper.getMainLooper())
    private var bubble: TextView? = null
    @Volatile var paused = false
        private set
    private val hideBubble = Runnable { removeBubble() }

    override fun onServiceConnected() {
        instance = this
        paused = false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        main.post { removeBubble() }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        main.post { removeBubble() }
        super.onDestroy()
    }

    // ---- Screen size and coordinates ----

    private fun realSize(): Pair<Int, Int> {
        val wm = getSystemService(WindowManager::class.java)
        return if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.maximumWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val p = android.graphics.Point()
            @Suppress("DEPRECATION") wm.defaultDisplay.getRealSize(p)
            p.x to p.y
        }
    }

    /** Phone pixels per real pixel. */
    private fun scale(): Float {
        val (w, h) = realSize()
        return minOf(1f, MAX_SIDE.toFloat() / max(w, h))
    }

    fun sizeText(): String {
        val (w, h) = realSize()
        val s = scale()
        return "${(w * s).roundToInt()}x${(h * s).roundToInt()}"
    }

    private fun real(v: Float) = v / scale()

    // ---- Gestures ----

    /** One finger: a path through phone-pixel points, starting [startMs] into the gesture. */
    class Stroke(val points: List<Pair<Float, Float>>, val startMs: Long, val durationMs: Long)

    /** Runs fingers at once and waits until they finish. Returns null on success, or an error. */
    fun gesture(strokes: List<Stroke>): String? {
        if (paused) return PAUSED
        if (strokes.isEmpty()) return null
        val builder = GestureDescription.Builder()
        for (s in strokes) {
            val path = Path()
            val (x0, y0) = s.points.first()
            path.moveTo(real(x0), real(y0))
            if (s.points.size == 1) {
                path.lineTo(real(x0), real(y0))
            } else {
                for ((x, y) in s.points.drop(1)) path.lineTo(real(x), real(y))
            }
            builder.addStroke(GestureDescription.StrokeDescription(path, s.startMs, s.durationMs.coerceIn(1, 59_000)))
        }
        val done = CountDownLatch(1)
        var error: String? = null
        val longest = strokes.maxOf { it.startMs + it.durationMs }
        val sent = try {
            dispatchGesture(builder.build(), object : GestureResultCallback() {
                override fun onCompleted(g: GestureDescription?) = done.countDown()
                override fun onCancelled(g: GestureDescription?) {
                    error = "error: the gesture was interrupted (the user touched the screen?)"
                    done.countDown()
                }
            }, main)
        } catch (e: Exception) {
            return "error: ${e.message}"
        }
        if (!sent) return "error: Android refused the gesture"
        if (!done.await(longest + 3000, TimeUnit.MILLISECONDS)) return "error: the gesture timed out"
        return error
    }

    fun globalAction(name: String): String {
        if (paused) return PAUSED
        val action = when (name) {
            "back" -> GLOBAL_ACTION_BACK
            "home" -> GLOBAL_ACTION_HOME
            "recents" -> GLOBAL_ACTION_RECENTS
            "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
            "split" -> GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN
            else -> return "error: unknown key $name (use back, home, recents, notifications, split)"
        }
        return if (performGlobalAction(action)) "ok" else "error: Android refused $name"
    }

    // ---- Typing ----

    /** Types into the focused text box; falls back to the clipboard. */
    fun type(text: String): String {
        if (paused) return PAUSED
        val focus = try { rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) } catch (_: Exception) { null }
        if (focus != null && focus.isEditable) {
            val old = focus.text?.toString().orEmpty().takeUnless { focus.isShowingHintText }.orEmpty()
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, old + text)
            }
            if (focus.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return "ok: typed"
        }
        val copied = CountDownLatch(1)
        main.post {
            try {
                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Boddy", text))
            } catch (_: Exception) {
            }
            copied.countDown()
        }
        copied.await(2, TimeUnit.SECONDS)
        return "ok: no text box is focused, so the text was copied to the clipboard (long-press a box and Paste)"
    }

    // ---- Seeing ----

    /** Saves a JPEG of the screen (optionally with a labelled grid) to [out]. Returns the reply. */
    fun screenshot(out: File, grid: Boolean): String {
        if (Build.VERSION.SDK_INT < 30) return "error: seeing the screen needs Android 11 or newer"
        val done = CountDownLatch(1)
        var reply = "error: the screenshot timed out"
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
                reply = try {
                    val hw = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                        ?: throw IllegalStateException("empty screenshot")
                    val s = scale()
                    val w = (hw.width * s).roundToInt()
                    val h = (hw.height * s).roundToInt()
                    val small = Bitmap.createScaledBitmap(hw.copy(Bitmap.Config.ARGB_8888, false), w, h, true)
                    result.hardwareBuffer.close()
                    val bmp = if (grid) drawGrid(small) else small
                    out.parentFile?.mkdirs()
                    out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) }
                    "ok: ${w}x$h"
                } catch (e: Exception) {
                    "error: ${e.message}"
                }
                done.countDown()
            }

            override fun onFailure(errorCode: Int) {
                reply = if (errorCode == ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT) {
                    "error: too fast, wait a moment and try again"
                } else {
                    "error: Android couldn't take the screenshot (code $errorCode)"
                }
                done.countDown()
            }
        })
        done.await(5, TimeUnit.SECONDS)
        return reply
    }

    private fun drawGrid(src: Bitmap): Bitmap {
        val bmp = src.copy(Bitmap.Config.ARGB_8888, true)
        val c = Canvas(bmp)
        val line = Paint().apply { color = Color.argb(110, 255, 0, 255); strokeWidth = 1f }
        val label = Paint().apply {
            color = Color.YELLOW; textSize = 14f; isAntiAlias = true
            setShadowLayer(2f, 0f, 0f, Color.BLACK)
        }
        for (x in 100 until bmp.width step 100) {
            c.drawLine(x.toFloat(), 0f, x.toFloat(), bmp.height.toFloat(), line)
            c.drawText("$x", x + 2f, 14f, label)
        }
        for (y in 100 until bmp.height step 100) {
            c.drawLine(0f, y.toFloat(), bmp.width.toFloat(), y.toFloat(), line)
            c.drawText("$y", 2f, y - 2f, label)
        }
        return bmp
    }

    /** Writes the visible buttons and text (with phone-pixel centres) to [out]. */
    fun dumpUi(out: File): String {
        val s = scale()
        val lines = mutableListOf<String>()
        val roots = try {
            windows.mapNotNull { it.root }.ifEmpty { listOfNotNull(rootInActiveWindow) }
        } catch (_: Exception) {
            listOfNotNull(rootInActiveWindow)
        }
        val rect = Rect()
        fun walk(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > 40 || lines.size >= 300) return
            val label = (node.text ?: node.contentDescription)?.toString()?.replace("\n", " ")?.take(80)
            if (node.isVisibleToUser && (!label.isNullOrBlank() || node.isClickable || node.isEditable)) {
                node.getBoundsInScreen(rect)
                val kind = when {
                    node.isEditable -> "textbox"
                    node.isClickable -> "button"
                    else -> "text"
                }
                lines += "$kind \"${label.orEmpty()}\" at ${(rect.exactCenterX() * s).roundToInt()} ${(rect.exactCenterY() * s).roundToInt()}"
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { walk(it, depth + 1) }
        }
        roots.forEach { walk(it, 0) }
        out.parentFile?.mkdirs()
        val app = foregroundApp()
        out.writeText("app: $app\nscreen: ${sizeText()}\n" +
            (if (lines.isEmpty()) "(no readable buttons: this app draws its own screen, e.g. a game; use phone-screen)\n"
            else lines.joinToString("\n", postfix = "\n")))
        return "ok: ${lines.size} items"
    }

    fun foregroundApp(): String = try { rootInActiveWindow?.packageName?.toString() } catch (_: Exception) { null } ?: "unknown"

    // ---- The "AI is in control" bubble ----

    /** Shows the bubble (with what the AI is doing) for a while after each command. */
    fun showActivity(what: String) {
        main.post {
            try {
                val b = bubble ?: createBubble()
                b.text = if (paused) "⏸ AI paused · tap to let it continue" else "🤖 AI: $what  ·  tap to stop"
                main.removeCallbacks(hideBubble)
                if (!paused) main.postDelayed(hideBubble, 20_000)
            } catch (e: Exception) {
                CrashLog.record(this, e, fatal = false)
            }
        }
    }

    private fun createBubble(): TextView {
        val view = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(28, 14, 28, 14)
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 40f
                setColor(Color.argb(210, 108, 92, 231))
            }
            setOnClickListener {
                paused = !paused
                showActivity("continuing")
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 24
        }
        getSystemService(WindowManager::class.java).addView(view, params)
        bubble = view
        return view
    }

    private fun removeBubble() {
        val b = bubble ?: return
        bubble = null
        try { getSystemService(WindowManager::class.java).removeView(b) } catch (_: Exception) {}
    }

    companion object {
        const val MAX_SIDE = 1600
        const val PAUSED = "error: the user tapped Stop. Don't touch the phone until they say to continue"

        @Volatile var instance: PhoneControl? = null
            private set

        /** True if this build declares the helper at all (see the note in AndroidManifest.xml). */
        fun isAvailable(context: Context): Boolean = try {
            context.packageManager.getServiceInfo(ComponentName(context, PhoneControl::class.java), 0)
            true
        } catch (_: Exception) {
            false
        }

        /** True if the user switched the helper on in Accessibility settings. */
        fun isEnabled(context: Context): Boolean {
            val me = ComponentName(context, PhoneControl::class.java).flattenToString()
            val on = android.provider.Settings.Secure.getString(
                context.contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ).orEmpty()
            return on.split(':').any { it.equals(me, ignoreCase = true) }
        }

        fun openSettings(context: Context) {
            try {
                context.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {
                context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
}
