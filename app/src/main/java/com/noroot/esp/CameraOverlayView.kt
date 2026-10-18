package com.noroot.esp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

class CameraOverlayView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : View(ctx, attrs) {

    private var boxes: List<Box> = emptyList()
    private var srcW: Int = 1
    private var srcH: Int = 1
    @Volatile private var rotationDeg: Int = 0

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 4f; color = Color.rgb(0, 255, 120)
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 255, 120); textSize = 30f; typeface = Typeface.DEFAULT_BOLD
    }
    private val tracer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 80, 80); strokeWidth = 2f
    }
    private val cross = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 255, 255); strokeWidth = 2f
    }

    fun submit(b: List<Box>, w: Int, h: Int, rot: Int) {
        boxes = b; srcW = w.coerceAtLeast(1); srcH = h.coerceAtLeast(1); rotationDeg = rot
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        // crosshair always on
        canvas.drawLine(cx - 24f, cy, cx + 24f, cy, cross)
        canvas.drawLine(cx, cy - 24f, cx, cy + 24f, cross)

        if (boxes.isEmpty()) return

        // Analyzer output coords are in the ANALYSIS image's frame; after
        // rotation=0/90/180/270 these need to be mapped into view coords.
        // Common cases:
        //   rot=90 (natural landscape, portrait sensor): swap and mirror.
        // We assume analyzer gives coords in the UNROTATED sensor space
        // and CameraX hands us rotationDegrees to apply.
        val (viewW, viewH) = width.toFloat() to height.toFloat()
        val (effW, effH) = if (rotationDeg % 180 == 0) srcW.toFloat() to srcH.toFloat()
                           else srcH.toFloat() to srcW.toFloat()
        val sx = viewW / effW
        val sy = viewH / effH
        val scale = minOf(sx, sy)
        val padX = (viewW - effW * scale) / 2f
        val padY = (viewH - effH * scale) / 2f

        for (b in boxes) {
            val l0 = b.rect.left; val t0 = b.rect.top
            val r0 = b.rect.right; val btm0 = b.rect.bottom
            val (nl, nt, nr, nb) = when (rotationDeg) {
                0 -> Quad(l0, t0, r0, btm0)
                90 -> Quad(srcH - btm0, l0, srcH - t0, r0)
                180 -> Quad(srcW - r0, srcH - btm0, srcW - l0, srcH - t0)
                270 -> Quad(t0, srcW - r0, btm0, srcW - l0)
                else -> Quad(l0, t0, r0, btm0)
            }
            val l = padX + nl * scale
            val t = padY + nt * scale
            val r = padX + nr * scale
            val btm = padY + nb * scale
            canvas.drawRect(l, t, r, btm, stroke)
            canvas.drawText(
                "${b.label} %.2f".format(b.score),
                l, (t - 6f).coerceAtLeast(28f), label
            )
            canvas.drawLine(cx, cy, (l + r) / 2f, (t + btm) / 2f, tracer)
        }
    }

    private data class Quad(val a: Float, val b: Float, val c: Float, val d: Float)
}
