package com.noroot.esp

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import kotlin.system.measureTimeMillis

class CameraActivity : AppCompatActivity() {

    private lateinit var preview: PreviewView
    private lateinit var overlay: CameraOverlayView
    private lateinit var tvStatus: TextView
    private val exec = Executors.newSingleThreadExecutor()
    private var detector: Detector? = null
    @Volatile private var frames: Long = 0
    @Volatile private var lastMs: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        preview = findViewById(R.id.preview)
        overlay = findViewById(R.id.overlay)
        tvStatus = findViewById(R.id.tvCamStatus)
        tvStatus.text = "requesting camera"

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1)
        }
    }

    override fun onRequestPermissionsResult(req: Int, perms: Array<out String>, grants: IntArray) {
        super.onRequestPermissionsResult(req, perms, grants)
        if (req == 1 && grants.isNotEmpty() && grants[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            tvStatus.text = "camera permission denied"
            finish()
        }
    }

    private fun startCamera() {
        detector = Detector(this).also { it.confThreshold = 0.10f }
        tvStatus.text = if (detector?.hasModel == true) "model loaded, binding" else "no model, binding"

        val fut = ProcessCameraProvider.getInstance(this)
        fut.addListener({
            val provider = fut.get()
            try {
                provider.unbindAll()

                val previewUse = Preview.Builder().build().also {
                    it.setSurfaceProvider(preview.surfaceProvider)
                }

                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(640, 480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                analysis.setAnalyzer(exec) { ip -> analyze(ip) }

                provider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, previewUse, analysis
                )
                tvStatus.text = "bound"
            } catch (t: Throwable) {
                Log.e("CameraActivity", "bind failed", t)
                tvStatus.text = "bind failed: ${t.message}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyze(ip: ImageProxy) {
        val det = detector
        if (det == null) { ip.close(); return }
        val took = measureTimeMillis {
            try {
                val bmp = imageProxyToBitmap(ip) ?: return@measureTimeMillis
                val rot = ip.imageInfo.rotationDegrees
                val boxes = det.detect(bmp)
                frames++
                runOnUiThread {
                    overlay.submit(boxes, bmp.width, bmp.height, rot)
                    tvStatus.text =
                        "f=$frames  ms=$lastMs  box=${boxes.size}  peak=%.2f  rot=$rot"
                }
                bmp.recycle()
            } catch (t: Throwable) {
                Log.w("CameraActivity", "analyze fail", t)
            }
        }
        lastMs = took
        ip.close()
    }

    // YUV_420_888 -> ARGB Bitmap via a tight NV21-style conversion.
    private fun imageProxyToBitmap(ip: ImageProxy): Bitmap? {
        val w = ip.width; val h = ip.height
        val planes = ip.planes
        val yBuf: ByteBuffer = planes[0].buffer
        val uBuf: ByteBuffer = planes[1].buffer
        val vBuf: ByteBuffer = planes[2].buffer
        val ySize = yBuf.remaining()
        val uSize = uBuf.remaining()
        val vSize = vBuf.remaining()
        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuf.get(nv21, 0, ySize)
        vBuf.get(nv21, ySize, vSize)
        uBuf.get(nv21, ySize + vSize, uSize)
        val yuv = android.graphics.YuvImage(
            nv21, android.graphics.ImageFormat.NV21, w, h, null
        )
        val out = java.io.ByteArrayOutputStream()
        if (!yuv.compressToJpeg(android.graphics.Rect(0, 0, w, h), 85, out)) return null
        val bytes = out.toByteArray()
        return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    override fun onDestroy() {
        detector?.close()
        exec.shutdown()
        super.onDestroy()
    }
}
