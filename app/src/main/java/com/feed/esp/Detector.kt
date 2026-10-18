package com.feed.esp

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

// One detected enemy in screen pixels.
data class Detection(val left: Float, val top: Float, val right: Float, val bottom: Float, val conf: Float)

// On-device YOLO-style detector. Replaces memory reading entirely: it finds
// enemies in the captured pixels. Input 640x640 RGB, output [1][8400][6]:
// cx, cy, w, h, conf, class  (normalized 0..1). Adjust shape to your model.
class Detector(ctx: Context) {

    private val inputSize = 640
    private val interpreter: Interpreter
    private var gpu: GpuDelegate? = null

    init {
        val opts = Interpreter.Options()
        try { gpu = GpuDelegate(); opts.addDelegate(gpu) } catch (_: Throwable) {}
        // Throws if assets/model.tflite is absent — the service catches it and shows a note.
        interpreter = Interpreter(loadModel(ctx), opts)
    }

    private fun loadModel(ctx: Context): ByteBuffer {
        ctx.assets.openFd("model.tflite").use { fd ->
            fd.createInputStream().channel.use { ch ->
                return ch.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
            }
        }
    }

    fun detect(frame: Bitmap): List<Detection> {
        val scaled = Bitmap.createScaledBitmap(frame, inputSize, inputSize, true)
        val input = toInput(scaled)
        scaled.recycle()

        val n = 8400
        val out = Array(1) { Array(n) { FloatArray(6) } }
        interpreter.run(input, out)

        val sx = frame.width.toFloat()
        val sy = frame.height.toFloat()
        val raw = ArrayList<Detection>()
        for (d in out[0]) {
            if (d[4] < Config.confidence) continue
            val cx = d[0] * sx; val cy = d[1] * sy
            val bw = d[2] * sx; val bh = d[3] * sy
            raw.add(Detection(cx - bw / 2, cy - bh / 2, cx + bw / 2, cy + bh / 2, d[4]))
        }
        return nms(raw)
    }

    private fun toInput(bmp: Bitmap): ByteBuffer {
        val buf = ByteBuffer.allocateDirect(inputSize * inputSize * 3 * 4).order(ByteOrder.nativeOrder())
        val px = IntArray(inputSize * inputSize)
        bmp.getPixels(px, 0, inputSize, 0, 0, inputSize, inputSize)
        for (p in px) {
            buf.putFloat((p shr 16 and 0xFF) / 255f)
            buf.putFloat((p shr 8 and 0xFF) / 255f)
            buf.putFloat((p and 0xFF) / 255f)
        }
        buf.rewind()
        return buf
    }

    private fun nms(boxes: List<Detection>, iou: Float = 0.45f): List<Detection> {
        val sorted = boxes.sortedByDescending { it.conf }.toMutableList()
        val keep = ArrayList<Detection>()
        while (sorted.isNotEmpty()) {
            val b = sorted.removeAt(0)
            keep.add(b)
            sorted.removeAll { overlap(b, it) > iou }
        }
        return keep
    }

    private fun overlap(a: Detection, b: Detection): Float {
        val l = maxOf(a.left, b.left); val t = maxOf(a.top, b.top)
        val r = minOf(a.right, b.right); val bo = minOf(a.bottom, b.bottom)
        val inter = maxOf(0f, r - l) * maxOf(0f, bo - t)
        val aa = (a.right - a.left) * (a.bottom - a.top)
        val ab = (b.right - b.left) * (b.bottom - b.top)
        val union = aa + ab - inter
        return if (union <= 0f) 0f else inter / union
    }

    fun close() { interpreter.close(); gpu?.close() }
}
