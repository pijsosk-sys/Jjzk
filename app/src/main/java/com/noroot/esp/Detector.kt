package com.noroot.esp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

data class Box(val rect: RectF, val score: Float, val label: String)

/**
 * Raw TFLite YOLOv8 detector. Expects a model exported with
 *   yolo export model=foo.pt format=tflite imgsz=320
 * Input  shape: [1, 320, 320, 3] float32 (0..1 RGB)
 * Output shape: [1, 4+nc, N] float32  (cx, cy, w, h, cls0..clsN-1), coords in input pixels.
 *
 * Drop a model at: /sdcard/Android/data/com.noroot.esp/files/model.tflite
 * (overrides the bundled asset without rebuilding).
 */
class Detector(private val ctx: Context) {

    @Volatile var confThreshold: Float = 0.25f
    @Volatile var iouThreshold: Float = 0.45f

    private var interpreter: Interpreter? = null
    private var inW: Int = 320
    private var inH: Int = 320
    private var outRows: Int = 0        // 4 + nc
    private var outCols: Int = 0        // N anchors
    private var numClasses: Int = 0
    private var transposeOutput: Boolean = true   // [1,4+nc,N] vs [1,N,4+nc]

    private val inputBuf: ByteBuffer by lazy {
        ByteBuffer.allocateDirect(1 * inW * inH * 3 * 4).order(ByteOrder.nativeOrder())
    }
    private var outputBuf: Array<Array<FloatArray>>? = null

    val hasModel: Boolean get() = interpreter != null

    init { rebuild() }

    @Synchronized
    private fun rebuild() {
        try { interpreter?.close() } catch (_: Throwable) {}
        interpreter = null
        try {
            val model = loadModel() ?: run {
                Log.w("Detector", "no model available"); return
            }
            val opts = Interpreter.Options().apply { setNumThreads(4) }
            val itp = Interpreter(model, opts)

            val inShape = itp.getInputTensor(0).shape() // expect [1,H,W,3]
            if (inShape.size != 4 || inShape[3] != 3) {
                Log.e("Detector", "bad input shape: ${inShape.toList()}")
                itp.close(); return
            }
            inH = inShape[1]; inW = inShape[2]

            val outShape = itp.getOutputTensor(0).shape() // [1, 4+nc, N] or [1, N, 4+nc]
            if (outShape.size != 3) {
                Log.e("Detector", "bad output shape: ${outShape.toList()}")
                itp.close(); return
            }
            val d1 = outShape[1]; val d2 = outShape[2]
            // YOLOv8 default: d1 = 4+nc (small), d2 = anchors (large).
            if (d1 <= d2) {
                transposeOutput = true;  outRows = d1; outCols = d2
            } else {
                transposeOutput = false; outRows = d2; outCols = d1
            }
            numClasses = outRows - 4
            if (numClasses <= 0) {
                Log.e("Detector", "bad num classes from shape ${outShape.toList()}")
                itp.close(); return
            }
            outputBuf = Array(1) { Array(d1) { FloatArray(d2) } }
            interpreter = itp
            Log.i("Detector", "loaded. in=${inW}x${inH} out=${outShape.toList()} nc=$numClasses transposed=$transposeOutput")
        } catch (t: Throwable) {
            Log.e("Detector", "init failed", t)
            interpreter = null
        }
    }

    private fun loadModel(): MappedByteBuffer? {
        val ext = File(ctx.getExternalFilesDir(null), "model.tflite")
        if (ext.exists() && ext.length() > 0) return mapFile(ext)
        // Fallback: copy bundled asset to cache (Interpreter needs a MappedByteBuffer).
        val cached = File(ctx.cacheDir, "model.tflite")
        if (!cached.exists() || cached.length() == 0L) {
            try {
                ctx.assets.open("model.tflite").use { input ->
                    cached.outputStream().use { out -> input.copyTo(out) }
                }
            } catch (t: Throwable) {
                Log.w("Detector", "no bundled model.tflite", t); return null
            }
        }
        return mapFile(cached)
    }

    private fun mapFile(f: File): MappedByteBuffer {
        FileInputStream(f).use { fis ->
            return fis.channel.map(FileChannel.MapMode.READ_ONLY, 0, f.length())
        }
    }

    fun detect(src: Bitmap): List<Box> {
        val itp = interpreter ?: return emptyList()
        val outBuf = outputBuf ?: return emptyList()

        // 1. letterbox-free simple resize to model input.
        val resized = Bitmap.createScaledBitmap(src, inW, inH, true)
        try {
            fillInput(resized)
        } finally {
            if (resized !== src) resized.recycle()
        }

        try {
            itp.run(inputBuf, outBuf)
        } catch (t: Throwable) {
            Log.w("Detector", "inference failed", t); return emptyList()
        }

        val raw = outBuf[0]
        val sx = src.width.toFloat() / inW
        val sy = src.height.toFloat() / inH

        val candidates = ArrayList<Box>()
        val n = outCols
        for (i in 0 until n) {
            val cx: Float; val cy: Float; val w: Float; val h: Float
            var bestScore = 0f; var bestCls = 0
            if (transposeOutput) {
                cx = raw[0][i]; cy = raw[1][i]; w = raw[2][i]; h = raw[3][i]
                for (c in 0 until numClasses) {
                    val s = raw[4 + c][i]
                    if (s > bestScore) { bestScore = s; bestCls = c }
                }
            } else {
                val row = raw[i]
                cx = row[0]; cy = row[1]; w = row[2]; h = row[3]
                for (c in 0 until numClasses) {
                    val s = row[4 + c]
                    if (s > bestScore) { bestScore = s; bestCls = c }
                }
            }
            if (bestScore < confThreshold) continue
            val l = (cx - w / 2f) * sx
            val t = (cy - h / 2f) * sy
            val r = (cx + w / 2f) * sx
            val b = (cy + h / 2f) * sy
            candidates.add(Box(RectF(l, t, r, b), bestScore, "c$bestCls"))
        }
        return nms(candidates, iouThreshold)
    }

    private fun fillInput(bmp: Bitmap) {
        inputBuf.rewind()
        val pixels = IntArray(inW * inH)
        bmp.getPixels(pixels, 0, inW, 0, 0, inW, inH)
        var idx = 0
        val inv = 1f / 255f
        for (y in 0 until inH) {
            for (x in 0 until inW) {
                val p = pixels[idx++]
                inputBuf.putFloat(((p shr 16) and 0xFF) * inv) // R
                inputBuf.putFloat(((p shr 8)  and 0xFF) * inv) // G
                inputBuf.putFloat((p          and 0xFF) * inv) // B
            }
        }
        inputBuf.rewind()
    }

    private fun nms(boxes: ArrayList<Box>, iou: Float): List<Box> {
        if (boxes.isEmpty()) return boxes
        boxes.sortByDescending { it.score }
        val kept = ArrayList<Box>(boxes.size)
        val suppressed = BooleanArray(boxes.size)
        for (i in boxes.indices) {
            if (suppressed[i]) continue
            val a = boxes[i]
            kept.add(a)
            for (j in i + 1 until boxes.size) {
                if (suppressed[j]) continue
                if (iou(a.rect, boxes[j].rect) > iou) suppressed[j] = true
            }
        }
        return kept
    }

    private fun iou(a: RectF, b: RectF): Float {
        val l = maxOf(a.left, b.left)
        val t = maxOf(a.top, b.top)
        val r = minOf(a.right, b.right)
        val btm = minOf(a.bottom, b.bottom)
        val inter = maxOf(0f, r - l) * maxOf(0f, btm - t)
        val ua = (a.right - a.left) * (a.bottom - a.top)
        val ub = (b.right - b.left) * (b.bottom - b.top)
        val union = ua + ub - inter
        return if (union <= 0f) 0f else inter / union
    }

    fun close() {
        try { interpreter?.close() } catch (_: Throwable) {}
        interpreter = null
    }
}
