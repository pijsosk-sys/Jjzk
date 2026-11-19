package com.redcore.booster.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Edge touch guard: two thin overlay strips along the left and right edges (the short edges in landscape)
 * that swallow touches, so a palm resting on the edge doesn't trigger game controls.
 * Uses only the public overlay permission — the touchscreen itself is not reconfigured.
 */
object EdgeGuard {
    private val views = mutableListOf<View>()
    /** True only while at least one strip is really attached. */
    val active = MutableStateFlow(false)
    private val main = Handler(Looper.getMainLooper())

    /**
     * Shows the strips. Call on the main thread; returns how many strips Android accepted (0, 1 or 2).
     * Off the main thread it posts the work and returns -1 (result unknown yet).
     */
    @SuppressLint("ClickableViewAccessibility")
    fun show(ctx: Context, widthDp: Int, visible: Boolean = false): Int {
        val c = ctx.applicationContext
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post { show(c, widthDp, visible) }; return -1 }
        removeAll(c)
        if (!Settings.canDrawOverlays(c)) return 0
        val wm = c.getSystemService(WindowManager::class.java)
        val px = (widthDp * c.resources.displayMetrics.density).toInt().coerceAtLeast(1)
        for (g in listOf(Gravity.START, Gravity.END)) {
            val v = View(c).apply {
                setBackgroundColor(if (visible) 0x66FF1E3C else 0x00000000)
                setOnTouchListener { _, _ -> true } // consume: the game never sees these touches
            }
            val lp = WindowManager.LayoutParams(
                px, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = g or Gravity.TOP
                if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            try { wm.addView(v, lp); views += v } catch (t: Throwable) { Log.w("EdgeGuard", "Edge strip rejected", t) }
        }
        active.value = views.isNotEmpty()
        return views.size
    }

    /** Shows the zones tinted red for a few seconds so the user can see the protected area. */
    fun preview(ctx: Context, widthDp: Int, ms: Long = 4000): Boolean {
        val wasActive = active.value
        val c = ctx.applicationContext
        val shown = if (Looper.myLooper() == Looper.getMainLooper()) show(c, widthDp, visible = true) else { main.post { show(c, widthDp, true) }; 2 }
        if (shown == 0) return false
        main.postDelayed({ if (wasActive) show(c, widthDp) else hide(c) }, ms)
        return true
    }

    fun hide(ctx: Context) {
        val c = ctx.applicationContext
        if (Looper.myLooper() == Looper.getMainLooper()) removeAll(c) else main.post { removeAll(c) }
    }

    /** While the game menu is open, let touches through to it. */
    fun setPassThrough(ctx: Context, through: Boolean) {
        main.post {
            val wm = ctx.applicationContext.getSystemService(WindowManager::class.java)
            views.forEach { v ->
                val lp = v.layoutParams as? WindowManager.LayoutParams ?: return@forEach
                lp.flags = if (through) lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                runCatching { wm.updateViewLayout(v, lp) }
            }
        }
    }

    private fun removeAll(c: Context) {
        val wm = c.getSystemService(WindowManager::class.java)
        views.forEach { runCatching { wm.removeViewImmediate(it) } }
        views.clear()
        active.value = false
    }
}
