package com.noroot.esp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectorResult
import java.io.File
import java.io.FileOutputStream

data class Box(val rect: RectF, val score: Float, val label: String)

/**
 * MediaPipe Tasks ObjectDetector. Default model: EfficientDet-Lite0 (bundled),
 * COCO classes — "person" matches most on-screen characters. Users can drop
 * a Fortnite-trained .tflite at the external path to override.
 */
class Detector(private val ctx: Context) {

    private var detector: ObjectDetector? = null
    @Volatile var confThreshold: Float = 0.35f
        set(value) { field = value; rebuild() }

    val hasModel: Boolean get() = detector != null

    init { rebuild() }

    @Synchronized
    private fun rebuild() {
        try { detector?.close() } catch (_: Throwable) {}
        detector = null
        try {
            val modelPath = resolveModelPath()
            val base = BaseOptions.builder().setModelAssetPath(modelPath).build()
            val opts = ObjectDetectorOptionsBuilder.build(base, confThreshold)
            detector = ObjectDetector.createFromOptions(ctx, opts)
            Log.i("Detector", "loaded model: $modelPath thr=$confThreshold")
        } catch (t: Throwable) {
            Log.e("Detector", "detector init failed", t)
            detector = null
        }
    }

    // MediaPipe wants a file path. Prefer /sdcard/.../files/model.tflite (user
    // can swap without rebuilding). Fallback: copy bundled asset to cache.
    private fun resolveModelPath(): String {
        val ext = File(ctx.getExternalFilesDir(null), "model.tflite")
        if (ext.exists() && ext.length() > 0) return ext.absolutePath
        val cached = File(ctx.cacheDir, "model.tflite")
        if (!cached.exists() || cached.length() == 0L) {
            ctx.assets.open("model.tflite").use { input ->
                FileOutputStream(cached).use { out -> input.copyTo(out) }
            }
        }
        return cached.absolutePath
    }

    fun detect(src: Bitmap): List<Box> {
        val d = detector ?: return emptyList()
        val img = BitmapImageBuilder(src).build()
        val res: ObjectDetectorResult = try { d.detect(img) }
        catch (t: Throwable) { Log.w("Detector", "detect failed", t); return emptyList() }

        val out = ArrayList<Box>()
        for (det in res.detections()) {
            val bb = det.boundingBox()
            val top = det.categories().firstOrNull() ?: continue
            if (top.score() < confThreshold) continue
            out.add(Box(
                RectF(bb.left, bb.top, bb.right, bb.bottom),
                top.score(),
                top.categoryName()
            ))
        }
        return out
    }

    fun close() {
        try { detector?.close() } catch (_: Throwable) {}
        detector = null
    }
}

// Isolated so API surface changes between MediaPipe versions touch one place.
private object ObjectDetectorOptionsBuilder {
    fun build(base: BaseOptions, thr: Float):
        com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector.ObjectDetectorOptions {
        return com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
            .ObjectDetectorOptions.builder()
            .setBaseOptions(base)
            .setRunningMode(RunningMode.IMAGE)
            .setMaxResults(20)
            .setScoreThreshold(thr)
            .build()
    }
}
