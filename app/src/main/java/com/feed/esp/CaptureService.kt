package com.feed.esp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
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
import android.view.Gravity
import android.view.WindowManager

// Mirrors the screen into Bitmaps, runs the detector, feeds screen-space boxes
// to the overlay. Entirely user space — no root, no process memory access.
class CaptureService : Service() {

    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var vdisplay: VirtualDisplay? = null
    private lateinit var wm: WindowManager
    private lateinit var view: EspView
    private var detector: Detector? = null
    private lateinit var bgThread: HandlerThread
    private lateinit var bg: Handler

    private var w = 0
    private var h = 0

    override fun onCreate() {
        super.onCreate()
        startAsForeground()

        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val m = DisplayMetrics()
        @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(m)
        w = m.widthPixels; h = m.heightPixels

        view = EspView(this)
        addOverlay()

        detector = try { Detector(this) } catch (e: Throwable) {
            view.setMessage("No model.tflite in assets — add it to see boxes")
            null
        }

        bgThread = HandlerThread("cap").also { it.start() }
        bg = Handler(bgThread.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent ?: return START_NOT_STICKY
        val code = intent.getIntExtra("code", 0)
        val data = intent.getParcelableExtra<Intent>("data") ?: return START_NOT_STICKY

        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mpm.getMediaProjection(code, data)

        reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2).apply {
            setOnImageAvailableListener({ onFrame() }, bg)
        }

        vdisplay = projection?.createVirtualDisplay(
            "cap", w, h, resources.displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader!!.surface, null, bg
        )
        return START_STICKY
    }

    private fun onFrame() {
        val r = reader ?: return
        val image: Image = r.acquireLatestImage() ?: return
        try {
            val d = detector
            if (d == null || !Config.espEnabled) { view.submit(emptyList()); return }
            val bmp = toBitmap(image)
            view.submit(d.detect(bmp))
            bmp.recycle()
        } catch (_: Throwable) {
            // drop the frame; next one retries
        } finally {
            image.close()
        }
    }

    private fun toBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val pixelStride = plane.pixelStride
        val rowPadding = plane.rowStride - pixelStride * w
        val padded = Bitmap.createBitmap(w + rowPadding / pixelStride, h, Bitmap.Config.ARGB_8888)
        padded.copyPixelsFromBuffer(plane.buffer)
        return if (rowPadding == 0) padded else Bitmap.createBitmap(padded, 0, 0, w, h)
    }

    private fun addOverlay() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        wm.addView(view, params)
        view.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::view.isInitialized) { view.stop(); if (::wm.isInitialized) runCatching { wm.removeView(view) } }
        vdisplay?.release()
        reader?.close()
        projection?.stop()
        detector?.close()
        if (::bgThread.isInitialized) bgThread.quitSafely()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAsForeground() {
        val id = "cap"
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(id, "Capture", NotificationManager.IMPORTANCE_LOW))
        }
        val n: Notification = Notification.Builder(this, id)
            .setContentTitle("ESP running")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(1, n)
        }
    }
}
