package com.feed.esp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

// Draws the detector's screen-space boxes, optional tracers and distance labels.
// Reads style live from Config so UI changes apply without a restart.
class EspView(context: Context) : View(context) {

    @Volatile private var dets: List<Detection> = emptyList()
    @Volatile private var message: String? = null

    private val box = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 3f; isAntiAlias = true }
    private val line = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 2f; isAntiAlias = true }
    private val label = Paint().apply { textSize = 26f; isAntiAlias = true }
    private val warn = Paint().apply { color = Color.YELLOW; textSize = 34f; isAntiAlias = true }

    private val loop = object : Runnable {
        override fun run() { invalidate(); postDelayed(this, 16) }
    }

    fun start() = post(loop)
    fun stop() = removeCallbacks(loop)
    fun submit(d: List<Detection>) { dets = d }
    fun setMessage(m: String?) { message = m }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        message?.let { canvas.drawText(it, 40f, 120f, warn); return }

        val c = Config.boxColor
        box.color = c; line.color = c; label.color = c

        val cx = width / 2f
        val bottom = height.toFloat()

        for (d in dets) {
            canvas.drawRect(d.left, d.top, d.right, d.bottom, box)

            if (Config.tracers) canvas.drawLine(cx, bottom, (d.left + d.right) / 2f, d.bottom, line)

            if (Config.distanceLabels) {
                // Proxy distance from box height: taller box = closer. Calibrate k per device.
                val k = 52000f
                val meters = (k / (d.bottom - d.top + 1f)).toInt()
                canvas.drawText("${meters}m", d.left, d.top - 6f, label)
            }
        }
    }
}
