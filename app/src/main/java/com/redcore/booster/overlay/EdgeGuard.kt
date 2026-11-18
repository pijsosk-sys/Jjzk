package com.redcore.booster.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Edge touch guard: two thin overlay strips along the screen's short edges that swallow touches,
 * so a palm resting on the edge while holding the phone sideways doesn't trigger game controls.
 * Uses only the public overlay permission — the touchscreen itself is not reconfigured.
 */
object EdgeGuard {
    private val views = mutableListOf<View>()
    val active = MutableStateFlow(false)
    private val main = Handler(Looper.getMainLooper())

    @SuppressLint("ClickableViewAccessibility")
    fun show(ctx: Context, widthDp: Int, visible: Boolean = false): Boolean {
        val c = ctx.applicationContext
        if (!Settings.canDrawOverlays(c)) return false
        main.post {
            removeAll(c)
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
                runCatching { wm.addView(v, lp); views += v }
            }
            active.value = views.isNotEmpty()
        }
        return true
    }

    /** Shows the zones tinted red for a few seconds so the user can see the protected area. */
    fun preview(ctx: Context, widthDp: Int, ms: Long = 4000): Boolean {
        val wasActive = active.value
        if (!show(ctx, widthDp, visible = true)) return false
        main.postDelayed({ if (wasActive) show(ctx, widthDp) else hide(ctx) }, ms)
        return true
    }

    fun hide(ctx: Context) { main.post { removeAll(ctx.applicationContext) } }

    private fun removeAll(c: Context) {
        val wm = c.getSystemService(WindowManager::class.java)
        views.forEach { runCatching { wm.removeView(it) } }
        views.clear()
        active.value = false
    }
}
