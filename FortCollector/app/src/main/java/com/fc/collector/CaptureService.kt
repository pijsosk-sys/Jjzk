package com.fc.collector

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class CaptureService : Service() {

    companion object {
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        const val EXTRA_SINK = "sink"
        private const val CH = "fc_capture"
        private const val TAG = "FortCollector"
        // sample the HUD this often (ms); 500ms = 2 fps, cheap and enough for HUD
        private const val SAMPLE_MS = 500L
    }

    private var projection: MediaProjection? = null
    private var vdisplay: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var bgThread: HandlerThread? = null
    private var bgHandler: Handler? = null
    private val running = AtomicBoolean(false)
    private var lastSample = 0L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var ocr: HudOcr
    private var sink: SinkClient? = null

    private var w = 0
    private var h = 0
    private var dpi = 0

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ocr = HudOcr()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopEverything(); stopSelf(); return START_NOT_STICKY }
            ACTION_START -> startCapture(intent)
        }
        return START_STICKY
    }

    private fun startCapture(intent: Intent) {
        startForeground(1, buildNotification())

        val code = intent.getIntExtra(EXTRA_CODE, 0)
        @Suppress("DEPRECATION")
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33)
            intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        else intent.getParcelableExtra(EXTRA_DATA)
        val sinkUrl = intent.getStringExtra(EXTRA_SINK) ?: "ws://10.0.2.2:8765"

        if (data == null) { stopSelf(); return }

        sink = SinkClient(sinkUrl).also { it.connectAsync() }

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        w = metrics.widthPixels
        h = metrics.heightPixels
        dpi = metrics.densityDpi

        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mgr.getMediaProjection(code, data)
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopEverything() }
        }, null)

        bgThread = HandlerThread("cap").also { it.start() }
        bgHandler = Handler(bgThread!!.looper)

        reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        vdisplay = projection?.createVirtualDisplay(
            "fc",
            w, h, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader!!.surface, null, bgHandler
        )

        running.set(true)
        reader?.setOnImageAvailableListener({ r -> onFrame(r) }, bgHandler)
        Log.i(TAG, "capture started ${w}x${h}")
    }

    private fun onFrame(r: ImageReader) {
        val image: Image = r.acquireLatestImage() ?: return
        try {
            val now = System.currentTimeMillis()
            if (!running.get() || now - lastSample < SAMPLE_MS) return
            lastSample = now

            val bmp = imageToBitmap(image) ?: return
            scope.launch {
                try {
                    val hud = ocr.extract(bmp)
                    sink?.send(hud.toJson())
                } catch (e: Exception) {
                    Log.w(TAG, "ocr/send fail: ${e.message}")
                } finally {
                    bmp.recycle()
                }
            }
        } finally {
            image.close()
        }
    }

    private fun imageToBitmap(image: Image): Bitmap? {
        val planes = image.planes
        if (planes.isEmpty()) return null
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * w
        val bmp = Bitmap.createBitmap(
            w + rowPadding / pixelStride, h, Bitmap.Config.ARGB_8888
        )
        bmp.copyPixelsFromBuffer(buffer)
        return if (rowPadding == 0) bmp
        else Bitmap.createBitmap(bmp, 0, 0, w, h).also { if (it !== bmp) bmp.recycle() }
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CH, "capture", NotificationManager.IMPORTANCE_MIN)
            )
        }
        return Notification.Builder(this, CH)
            .setContentTitle("FortCollector")
            .setContentText("reading HUD")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()
    }

    private fun stopEverything() {
        running.set(false)
        try { vdisplay?.release() } catch (_: Exception) {}
        try { reader?.close() } catch (_: Exception) {}
        try { projection?.stop() } catch (_: Exception) {}
        try { bgThread?.quitSafely() } catch (_: Exception) {}
        sink?.close()
        vdisplay = null; reader = null; projection = null; bgThread = null; sink = null
    }

    override fun onDestroy() {
        stopEverything()
        scope.cancel()
        ocr.close()
        super.onDestroy()
    }
}
