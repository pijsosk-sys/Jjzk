package com.feed.esp

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

// One detected target in screen pixels.
data class Detection(val left: Float, val top: Float, val right: Float, val bottom: Float, val conf: Float)

// On-device detector using the bundled EfficientDet-Lite0 (COCO) model.
// Input:  uint8 [1,320,320,3]
// Outputs (by index): 0 locations[1,25,4] ymin,xmin,ymax,xmax (normalized)
//                     1 classes[1,25]   2 scores[1,25]   3 count[1]
// COCO person class id = 0. Replace assets/model.tflite with a Fortnite-trained
// model for real accuracy; the I/O here matches the EfficientDet-Lite export.
class Detector(ctx: Context) {

    private val inputSize = 320
    private val maxDet = 25
    private val personClass = 0

    private val interpreter: Interpreter
    private var gpu: GpuDelegate? = null

    init {
        val opts = Interpreter.Options()
        try { gpu = GpuDelegate(); opts.addDelegate(gpu) } catch (_: Throwable) {}
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

        val locations = Array(1) { Array(maxDet) { FloatArray(4) } }
        val classes = Array(1) { FloatArray(maxDet) }
        val scores = Array(1) { FloatArray(maxDet) }
        val count = FloatArray(1)

        val outputs = HashMap<Int, Any>()
        outputs[0] = locations
        outputs[1] = classes
        outputs[2] = scores
        outputs[3] = count

        interpreter.runForMultipleInputsOutputs(arrayOf<Any>(input), outputs)

        val sx = frame.width.toFloat()
        val sy = frame.height.toFloat()
        val n = count[0].toInt().coerceIn(0, maxDet)

        val result = ArrayList<Detection>(n)
        for (i in 0 until n) {
            if (scores[0][i] < Config.confidence) continue
            if (classes[0][i].toInt() != personClass) continue
            val b = locations[0][i]            // ymin, xmin, ymax, xmax (0..1)
            result.add(
                Detection(
                    left = b[1] * sx,
                    top = b[0] * sy,
                    right = b[3] * sx,
                    bottom = b[2] * sy,
                    conf = scores[0][i]
                )
            )
        }
        return result
    }

    // uint8 RGB, no normalization — EfficientDet-Lite takes raw bytes.
    private fun toInput(bmp: Bitmap): ByteBuffer {
        val buf = ByteBuffer.allocateDirect(inputSize * inputSize * 3).order(ByteOrder.nativeOrder())
        val px = IntArray(inputSize * inputSize)
        bmp.getPixels(px, 0, inputSize, 0, 0, inputSize, inputSize)
        for (p in px) {
            buf.put((p shr 16 and 0xFF).toByte())
            buf.put((p shr 8 and 0xFF).toByte())
            buf.put((p and 0xFF).toByte())
        }
        buf.rewind()
        return buf
    }

    fun close() { interpreter.close(); gpu?.close() }
}
