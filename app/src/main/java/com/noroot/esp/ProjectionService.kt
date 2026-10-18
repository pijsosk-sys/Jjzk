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
    }

    private var projection: MediaProjection? = null
    private var vdisplay: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var detector: Detector? = null
    private var scope: CoroutineScope? = null
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
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val m = DisplayMetrics()
        @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(m)
        width = m.widthPixels
        height = m.heightPixels
        density = m.densityDpi

        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        vdisplay = projection!!.createVirtualDisplay(
            "esp-vd", width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader!!.surface, null, null
        )

        detector = Detector(this).also { it.confThreshold = confThresh }

        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        reader!!.setOnImageAvailableListener({ r ->
            val img = try { r.acquireLatestImage() } catch (_: Throwable) { null } ?: return@setOnImageAvailableListener
            if (busy) { img.close(); return@setOnImageAvailableListener }
            busy = true
            val bmp = try { toBitmap(img) } catch (t: Throwable) { Log.w(TAG, "toBitmap", t); null } finally { img.close() }
            if (bmp == null) { busy = false; return@setOnImageAvailableListener }
            scope!!.launch {
                try {
                    val boxes = detector!!.detect(bmp)
                    OverlayService.push(this@ProjectionService, boxes, width, height)
                } catch (t: Throwable) { Log.w(TAG, "detect", t) }
                finally { bmp.recycle(); busy = false }
            }
        }, Handler(Looper.getMainLooper()))
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
        scope?.cancel()
        vdisplay = null; reader = null; projection = null; detector = null; scope = null
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
