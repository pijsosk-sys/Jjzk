package com.noroot.esp

import android.app.*
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import kotlinx.coroutines.*
import java.nio.ByteBuffer

class ProjectionService : Service() {

    companion object {
        const val ACTION_START = "START"
        const val ACTION_CONF = "com.noroot.esp.CONF"
        const val EXTRA_RESULT_CODE = "rc"
        const val EXTRA_DATA = "data"
        const val EXTRA_CONF = "conf"
        const val CHANNEL = "esp_capture"
        const val NOTIF_ID = 7001
        private const val TAG = "ProjectionService"

        @Volatile var framesReceived: Long = 0
        @Volatile var framesPushed: Long = 0
        @Volatile var lastError: String = ""
        @Volatile var setupStatus: String = "idle"
    }

    private var projection: MediaProjection? = null
    private var vdisplay: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var detector: Detector? = null
    private var scope: CoroutineScope? = null
    private var captureThread: HandlerThread? = null
    private var confThresh: Float = 0.35f
    private var width = 0
    private var height = 0
    private var density = 1
    @Volatile private var busy = false

    private val confReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            confThresh = i?.getFloatExtra("c", confThresh) ?: confThresh
            detector?.confThreshold = confThresh
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(confReceiver, IntentFilter(ACTION_CONF), RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(confReceiver, IntentFilter(ACTION_CONF))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_START) return START_NOT_STICKY

        val notif = NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("ESP running")
            .setContentText("Screen capture active")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, notif)
        }

        val rc = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val data: Intent = intent.getParcelableExtra(EXTRA_DATA) ?: run { stopSelf(); return START_NOT_STICKY }
        confThresh = intent.getFloatExtra(EXTRA_CONF, 0.35f)

        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mpm.getMediaProjection(rc, data)
        if (projection == null) { stopSelf(); return START_NOT_STICKY }

        projection!!.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { shutdown() }
        }, Handler(Looper.getMainLooper()))

        startOverlay()
        setupCapture()
        return START_STICKY
    }

    private fun startOverlay() {
        startService(Intent(this, OverlayService::class.java))
    }

    private fun setupCapture() {
        setupStatus = "setup"
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val m = DisplayMetrics()
        @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(m)
        val fullW = m.widthPixels; val fullH = m.heightPixels
        density = m.densityDpi
        // Scale capture to ~720 on longest side; faster, same aspect.
        val target = 720
        val scale = target.toFloat() / maxOf(fullW, fullH)
        width = (fullW * scale).toInt().coerceAtLeast(64)
        height = (fullH * scale).toInt().coerceAtLeast(64)
        setupStatus = "sz=${width}x${height}"

        reader = try {
            ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        } catch (t: Throwable) { lastError = "reader:${t.javaClass.simpleName}"; null }
        if (reader == null) { setupStatus = "no-reader"; return }

        vdisplay = try {
            projection!!.createVirtualDisplay(
                "esp-vd", width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR or
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                reader!!.surface, null, null
            )
        } catch (t: Throwable) { lastError = "vd:${t.javaClass.simpleName}"; null }
        if (vdisplay == null) { setupStatus = "no-vd"; return }
        setupStatus = "live ${width}x${height}"

        detector = Detector(this).also { it.confThreshold = confThresh }

        captureThread = HandlerThread("esp-capture").apply { start() }
        val captureHandler = Handler(captureThread!!.looper)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        framesReceived = 0; framesPushed = 0; lastError = ""

        reader!!.setOnImageAvailableListener({ r ->
            val img = try { r.acquireLatestImage() }
                catch (t: Throwable) { lastError = "acq:${t.javaClass.simpleName}"; null }
                ?: return@setOnImageAvailableListener
            framesReceived++
            if (busy) { img.close(); return@setOnImageAvailableListener }
            busy = true
            val bmp = try { toBitmap(img) }
                catch (t: Throwable) { lastError = "bmp:${t.javaClass.simpleName}"; null }
                finally { img.close() }
            if (bmp == null) { busy = false; return@setOnImageAvailableListener }
            scope!!.launch {
                try {
                    val boxes = detector!!.detect(bmp)
                    framesPushed++
                    OverlayService.push(this@ProjectionService, boxes, width, height,
                        detector!!.lastPeakScore)
                    // Aim at the box whose center is nearest the screen's center.
                    if (AimService.aimEnabled && boxes.isNotEmpty()) {
                        val cx = width / 2f; val cy = height / 2f
                        var best = boxes[0]; var bestD2 = Float.MAX_VALUE
                        for (b in boxes) {
                            val mx = (b.rect.left + b.rect.right) / 2f
                            val my = (b.rect.top + b.rect.bottom) / 2f
                            val d2 = (mx - cx) * (mx - cx) + (my - cy) * (my - cy)
                            if (d2 < bestD2) { bestD2 = d2; best = b }
                        }
                        val tx = (best.rect.left + best.rect.right) / 2f
                        // Aim slightly above center (head shot bias)
                        val ty = best.rect.top + (best.rect.bottom - best.rect.top) * 0.25f
                        AimService.aimAt(tx, ty, cx, cy)
                    }
                } catch (t: Throwable) {
                    lastError = "det:${t.javaClass.simpleName}"
                    Log.w(TAG, "detect", t)
                } finally { bmp.recycle(); busy = false }
            }
        }, captureHandler)
    }

    private fun toBitmap(image: Image): Bitmap {
        val planes = image.planes
        val buf: ByteBuffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width
        val bmp = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height, Bitmap.Config.ARGB_8888
        )
        bmp.copyPixelsFromBuffer(buf)
        return if (rowPadding == 0) bmp
        else Bitmap.createBitmap(bmp, 0, 0, image.width, image.height).also { bmp.recycle() }
    }

    private fun shutdown() {
        try { vdisplay?.release() } catch (_: Throwable) {}
        try { reader?.close() } catch (_: Throwable) {}
        try { projection?.stop() } catch (_: Throwable) {}
        try { detector?.close() } catch (_: Throwable) {}
        try { captureThread?.quitSafely() } catch (_: Throwable) {}
        scope?.cancel()
        vdisplay = null; reader = null; projection = null; detector = null
        scope = null; captureThread = null
    }

    override fun onDestroy() {
        try { unregisterReceiver(confReceiver) } catch (_: Throwable) {}
        shutdown()
        stopService(Intent(this, OverlayService::class.java))
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, "ESP capture", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
    }
}

// Minimal shim so we don't pull the whole AppCompat notif dep surface unnecessarily.
private object NotificationCompat {
    class Builder(private val ctx: Context, private val channel: String) {
        private val n = Notification.Builder(ctx, channel)
        fun setContentTitle(s: CharSequence) = apply { n.setContentTitle(s) }
        fun setContentText(s: CharSequence) = apply { n.setContentText(s) }
        fun setSmallIcon(id: Int) = apply { n.setSmallIcon(id) }
        fun setOngoing(b: Boolean) = apply { n.setOngoing(b) }
        fun build(): Notification = n.build()
    }
}
