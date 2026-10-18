package com.noroot.esp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Environment
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

data class Box(val rect: RectF, val score: Float, val cls: Int)

/**
 * YOLOv5/YOLOv8 TFLite detector.
 * Expects a float32 model at assets/model.tflite with input [1,inputSize,inputSize,3] in 0..1,
 * output either [1,N,5+C] (v5) or [1,4+C,N] (v8). Place a Fortnite-trained model there.
 */
class Detector(ctx: Context) {

    private val inputSize = 320
    private var interpreter: Interpreter? = null
    private var outShape: IntArray = intArrayOf(0)
    private var isV8: Boolean = false
    private var numClasses: Int = 0
    val hasModel: Boolean get() = interpreter != null

    @Volatile var confThreshold: Float = 0.35f
    var iouThreshold: Float = 0.45f

    init {
        try {
            val model = loadModel(ctx)
            if (model == null) {
                Log.w("Detector", "No model.tflite found — overlay will run with 0 boxes. " +
                    "Push one to /sdcard/Android/data/com.noroot.esp/files/model.tflite")
            } else {
                val opts = Interpreter.Options().apply { numThreads = 4 }
                val itp = Interpreter(model, opts)
                outShape = itp.getOutputTensor(0).shape()
                isV8 = outShape.size == 3 && outShape[1] < outShape[2]
                numClasses = if (isV8) outShape[1] - 4 else outShape[2] - 5
                interpreter = itp
            }
        } catch (t: Throwable) {
            Log.e("Detector", "model load failed", t)
            interpreter = null
        }
    }

    private fun loadModel(ctx: Context): MappedByteBuffer? {
        // 1. External file so a model can be dropped without a rebuild.
        val ext = File(ctx.getExternalFilesDir(null), "model.tflite")
        if (ext.exists() && ext.length() > 0) {
            FileInputStream(ext).use { fis ->
                return fis.channel.map(FileChannel.MapMode.READ_ONLY, 0, ext.length())
            }
        }
        // 2. Public downloads convenience location.
        val pub = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "model.tflite"
        )
        if (pub.exists() && pub.length() > 0) {
            FileInputStream(pub).use { fis ->
                return fis.channel.map(FileChannel.MapMode.READ_ONLY, 0, pub.length())
            }
        }
        // 3. Baked-in asset — only present if a model was bundled at build time.
        return try {
            val afd = ctx.assets.openFd("model.tflite")
            FileInputStream(afd.fileDescriptor).use { fis ->
                fis.channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
            }
        } catch (_: Throwable) { null }
    }

    fun detect(src: Bitmap): List<Box> {
        val itp = interpreter ?: return emptyList()
        val resized = Bitmap.createScaledBitmap(src, inputSize, inputSize, true)
        val input = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3).order(ByteOrder.nativeOrder())
        val px = IntArray(inputSize * inputSize)
        resized.getPixels(px, 0, inputSize, 0, 0, inputSize, inputSize)
        for (p in px) {
            input.putFloat(((p shr 16) and 0xFF) / 255f)
            input.putFloat(((p shr 8) and 0xFF) / 255f)
            input.putFloat((p and 0xFF) / 255f)
        }
        if (resized !== src) resized.recycle()
        input.rewind()

        val out = Array(outShape[0]) {
            Array(outShape[1]) { FloatArray(outShape[2]) }
        }
        itp.run(input, out)

        val boxes = ArrayList<Box>()
        val sw = src.width.toFloat()
        val sh = src.height.toFloat()
        if (isV8) {
            val n = outShape[2]
            for (i in 0 until n) {
                var bestC = -1
                var bestS = 0f
                for (c in 0 until numClasses) {
                    val s = out[0][4 + c][i]
                    if (s > bestS) { bestS = s; bestC = c }
                }
                if (bestS < confThreshold) continue
                val cx = out[0][0][i]; val cy = out[0][1][i]
                val w = out[0][2][i];  val h = out[0][3][i]
                boxes.add(Box(xywhToRect(cx, cy, w, h, sw, sh), bestS, bestC))
            }
        } else {
            val n = outShape[1]
            for (i in 0 until n) {
                val obj = out[0][i][4]
                if (obj < confThreshold) continue
                var bestC = -1
                var bestS = 0f
                for (c in 0 until numClasses) {
                    val s = out[0][i][5 + c] * obj
                    if (s > bestS) { bestS = s; bestC = c }
                }
                if (bestS < confThreshold) continue
                val cx = out[0][i][0]; val cy = out[0][i][1]
                val w = out[0][i][2];  val h = out[0][i][3]
                boxes.add(Box(xywhToRect(cx, cy, w, h, sw, sh), bestS, bestC))
            }
        }
        return nms(boxes, iouThreshold)
    }

    private fun xywhToRect(cx: Float, cy: Float, w: Float, h: Float, sw: Float, sh: Float): RectF {
        val x1 = (cx - w / 2f) * sw
        val y1 = (cy - h / 2f) * sh
        val x2 = (cx + w / 2f) * sw
        val y2 = (cy + h / 2f) * sh
        return RectF(x1, y1, x2, y2)
    }

    private fun nms(input: List<Box>, iou: Float): List<Box> {
        val sorted = input.sortedByDescending { it.score }.toMutableList()
        val keep = ArrayList<Box>()
        while (sorted.isNotEmpty()) {
            val a = sorted.removeAt(0)
            keep.add(a)
            val it = sorted.iterator()
            while (it.hasNext()) if (iou(a.rect, it.next().rect) > iou) it.remove()
        }
        return keep
    }

    private fun iou(a: RectF, b: RectF): Float {
        val x1 = maxOf(a.left, b.left); val y1 = maxOf(a.top, b.top)
        val x2 = minOf(a.right, b.right); val y2 = minOf(a.bottom, b.bottom)
        val inter = maxOf(0f, x2 - x1) * maxOf(0f, y2 - y1)
        val ua = (a.right - a.left) * (a.bottom - a.top) + (b.right - b.left) * (b.bottom - b.top) - inter
        return if (ua <= 0f) 0f else inter / ua
    }

    fun close() {
        try { interpreter?.close() } catch (_: Throwable) {}
        interpreter = null
    }
}
