package com.noroot.esp

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Build
import android.os.IBinder
import android.view.View
import android.view.WindowManager

class OverlayService : Service() {

    companion object {
        @Volatile private var instance: OverlayService? = null

        fun push(ctx: Context, boxes: List<Box>, srcW: Int, srcH: Int) {
            instance?.update(boxes, srcW, srcH) ?: run {
                ctx.startService(Intent(ctx, OverlayService::class.java))
            }
        }
    }

    private lateinit var wm: WindowManager
    private var view: OverlayView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        view = OverlayView(this)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        )
        try { wm.addView(view, lp) } catch (_: Throwable) {}
        instance = this
    }

    fun update(boxes: List<Box>, srcW: Int, srcH: Int) {
        view?.updateBoxes(boxes, srcW, srcH)
    }

    override fun onDestroy() {
        try { view?.let { wm.removeView(it) } } catch (_: Throwable) {}
        view = null
        if (instance === this) instance = null
        super.onDestroy()
    }

    private class OverlayView(ctx: Context) : View(ctx) {
        private var boxes: List<Box> = emptyList()
        private var srcW = 1; private var srcH = 1
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 3f; color = Color.rgb(0, 255, 120)
        }
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 255, 120); textSize = 28f; typeface = Typeface.DEFAULT_BOLD
        }
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 80, 80); strokeWidth = 2f
        }

        fun updateBoxes(b: List<Box>, w: Int, h: Int) {
            boxes = b; srcW = w; srcH = h
            postInvalidateOnAnimation()
        }

        override fun onDraw(canvas: Canvas) {
            if (boxes.isEmpty()) return
            val sx = width.toFloat() / srcW
            val sy = height.toFloat() / srcH
            val cx = width / 2f; val cy = height / 2f
            for (b in boxes) {
                val l = b.rect.left * sx
                val t = b.rect.top * sy
                val r = b.rect.right * sx
                val btm = b.rect.bottom * sy
                canvas.drawRect(l, t, r, btm, stroke)
                canvas.drawText("%.2f".format(b.score), l, (t - 6f).coerceAtLeast(28f), text)
                canvas.drawLine(cx, cy, (l + r) / 2f, (t + btm) / 2f, line)
            }
        }
    }
}
