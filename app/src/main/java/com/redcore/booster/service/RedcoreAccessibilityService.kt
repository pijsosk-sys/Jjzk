package com.redcore.booster.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.util.DisplayMetrics
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.redcore.booster.app
import com.redcore.booster.data.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Optional helper used for two toolkit features:
 *  1. Screenshot button → system screenshot action.
 *  2. Volume-key triggers → during an active REDCORE game session, Vol− taps the L1 point and Vol+ the R1 point.
 * It never reads window content (canRetrieveWindowContent=false) and ignores accessibility events.
 */
class RedcoreAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    @Volatile private var cfg: AppSettings? = null

    override fun onServiceConnected() {
        instance = this
        scope.launch { app.settings.settings.collect { cfg = it } }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    /** Strokes currently held down by a trigger in "Hold" mode, keyed by key code. */
    private val held = HashMap<Int, Pair<GestureDescription.StrokeDescription, Path>>()

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val c = cfg ?: return false
        if (!c.triggers || app.boost.session.value == null) return false
        val (fx, fy) = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> c.l1x to c.l1y
            KeyEvent.KEYCODE_VOLUME_UP -> c.r1x to c.r1y
            else -> return false
        }
        when {
            event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 -> {
                if (c.triggerVibrate) app.boost.effects.haptics.tick()
                if (c.triggerHold) press(event.keyCode, fx, fy) else tap(fx, fy)
            }
            event.action == KeyEvent.ACTION_UP && c.triggerHold -> release(event.keyCode)
        }
        return true // consume so the volume doesn't change while the trigger is used
    }

    private fun point(fx: Float, fy: Float): Path {
        val wm = getSystemService(WindowManager::class.java)
        val (w, h) = if (Build.VERSION.SDK_INT >= 30) wm.currentWindowMetrics.bounds.let { it.width() to it.height() }
        else DisplayMetrics().also { @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(it) }.let { it.widthPixels to it.heightPixels }
        return Path().apply { moveTo(fx * w, fy * h) }
    }

    private fun tap(fx: Float, fy: Float) {
        val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(point(fx, fy), 0, 45)).build()
        dispatchGesture(g, null, null)
    }

    /** Puts a finger down and keeps it there until [release] (Android 8+ continued strokes). */
    private fun press(key: Int, fx: Float, fy: Float) {
        val p = point(fx, fy)
        val stroke = GestureDescription.StrokeDescription(p, 0, 60, true)
        held[key] = stroke to p
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    private fun release(key: Int) {
        val (stroke, p) = held.remove(key) ?: return
        runCatching {
            val end = stroke.continueStroke(p, 0, 30, false)
            dispatchGesture(GestureDescription.Builder().addStroke(end).build(), null, null)
        }
    }

    override fun onDestroy() {
        instance = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        @Volatile var instance: RedcoreAccessibilityService? = null

        fun takeScreenshot(): Boolean {
            val s = instance ?: return false
            if (Build.VERSION.SDK_INT < 28) return false
            return s.performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
        }
    }
}
