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

        fun push(ctx: Context, boxes: List<Box>, srcW: Int, srcH: Int, peak: Float = 0f) {
            instance?.update(boxes, srcW, srcH, peak) ?: run {
                ctx.startService(Intent(ctx, OverlayService::class.java))
            }
        }
    }

    private lateinit var wm: WindowManager
    private var view: OverlayView? = null
    private val tickHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            view?.postInvalidateOnAnimation()
            tickHandler.postDelayed(this, 500)
        }
    }

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
        tickHandler.post(tick)
    }

    fun update(boxes: List<Box>, srcW: Int, srcH: Int, peak: Float) {
        view?.updateBoxes(boxes, srcW, srcH, peak)
    }

    override fun onDestroy() {
        tickHandler.removeCallbacks(tick)
        try { view?.let { wm.removeView(it) } } catch (_: Throwable) {}
        view = null
        if (instance === this) instance = null
        super.onDestroy()
    }

    private class OverlayView(ctx: Context) : View(ctx) {
        private var boxes: List<Box> = emptyList()
        private var srcW = 1; private var srcH = 1
        private var peak = 0f
        private var frames = 0L
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 3f; color = Color.rgb(0, 255, 120)
        }
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 255, 120); textSize = 28f; typeface = Typeface.DEFAULT_BOLD
        }
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 80, 80); strokeWidth = 2f
        }
        private val hud = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 220, 60); textSize = 32f; typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(2f, 0f, 0f, Color.BLACK)
        }
        private val hudBg = Paint().apply { color = Color.argb(140, 0, 0, 0) }
        private val cross = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 255, 255); strokeWidth = 2f
        }

        fun updateBoxes(b: List<Box>, w: Int, h: Int, p: Float) {
            boxes = b; srcW = w; srcH = h; peak = p; frames++
            postInvalidateOnAnimation()
        }

        override fun onDraw(canvas: Canvas) {
            val sx = width.toFloat() / srcW
            val sy = height.toFloat() / srcH
            val cx = width / 2f; val cy = height / 2f

            // crosshair always on — proves overlay is drawing
            canvas.drawLine(cx - 20f, cy, cx + 20f, cy, cross)
            canvas.drawLine(cx, cy - 20f, cx, cy + 20f, cross)

            // HUD: pipeline liveness. Two counters answer "where is it dead?"
            //   rx = ImageReader frames arrived from MediaProjection
            //   px = frames fully processed and pushed to overlay
            //   f  = overlay invalidations (this view's own count)
            val rx = ProjectionService.framesReceived
            val px = ProjectionService.framesPushed
            val err = ProjectionService.lastError
            val st = ProjectionService.setupStatus
            val hudText = "ESP rx=$rx px=$px box=${boxes.size} peak=%.2f".format(peak)
            canvas.drawRect(0f, 0f, 820f, 54f, hudBg)
            canvas.drawText(hudText, 12f, 38f, hud)
            canvas.drawRect(0f, 54f, 820f, 94f, hudBg)
            canvas.drawText("cap=$st  f=$frames", 12f, 86f, hud)
            if (err.isNotEmpty()) {
                canvas.drawRect(0f, 94f, 820f, 134f, hudBg)
                canvas.drawText("err=$err", 12f, 126f, hud)
            }

            for (b in boxes) {
                val l = b.rect.left * sx
                val t = b.rect.top * sy
                val r = b.rect.right * sx
                val btm = b.rect.bottom * sy
                canvas.drawRect(l, t, r, btm, stroke)
                canvas.drawText(
                    "${b.label} %.2f".format(b.score),
                    l, (t - 6f).coerceAtLeast(28f), text
                )
                canvas.drawLine(cx, cy, (l + r) / 2f, (t + btm) / 2f, line)
            }
        }
    }
}
