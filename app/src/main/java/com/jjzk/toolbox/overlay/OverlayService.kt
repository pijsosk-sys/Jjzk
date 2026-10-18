package com.jjzk.toolbox.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.jjzk.toolbox.MainActivity
import com.jjzk.toolbox.R

/**
 * Draws the floating "handle + side-panel" overlay above whatever app is
 * foreground (Minecraft, usually). Starts a foreground notification so the
 * OS doesn't kill it mid-session, and listens for a stop action from the
 * notification to tear itself down.
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var handle: View? = null
    private var panel: View? = null
    private lateinit var controller: OverlayController

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        controller = OverlayController(this)
        startInForeground()
        showHandle()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        panel?.let { safeRemove(it) }; panel = null
        handle?.let { safeRemove(it) }; handle = null
        super.onDestroy()
    }

    private fun showHandle() {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.overlay_handle, null)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24; y = 240
        }
        attachDrag(view, lp)
        view.setOnClickListener { togglePanel(lp) }
        windowManager.addView(view, lp)
        handle = view
    }

    private fun togglePanel(handleLp: WindowManager.LayoutParams) {
        if (panel != null) {
            safeRemove(panel!!); panel = null; return
        }
        val view = LayoutInflater.from(this).inflate(R.layout.overlay_panel, null)
        controller.bind(view) { stopSelf() }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = handleLp.x + 80
            y = handleLp.y
        }
        windowManager.addView(view, lp)
        panel = view
    }

    @Suppress("ClickableViewAccessibility")
    private fun attachDrag(view: View, lp: WindowManager.LayoutParams) {
        var startX = 0; var startY = 0; var rawX = 0f; var rawY = 0f
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x; startY = lp.y
                    rawX = event.rawX; rawY = event.rawY
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = startX + (event.rawX - rawX).toInt()
                    lp.y = startY + (event.rawY - rawY).toInt()
                    windowManager.updateViewLayout(v, lp)
                    panel?.let {
                        val plp = it.layoutParams as WindowManager.LayoutParams
                        plp.x = lp.x + 80
                        plp.y = lp.y
                        windowManager.updateViewLayout(it, plp)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun safeRemove(v: View) = try { windowManager.removeView(v) } catch (_: Throwable) {}

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun startInForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Toolbox overlay",
                    NotificationManager.IMPORTANCE_LOW)
            )
        }
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Toolbox overlay active")
            .setContentText("Tap the floating handle to open the menu")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentIntent(openApp)
            .addAction(android.R.drawable.ic_delete, "Stop", stop)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    companion object {
        private const val NOTIF_ID = 1001
        private const val CHANNEL_ID = "toolbox-overlay"
        const val ACTION_STOP = "com.jjzk.toolbox.STOP_OVERLAY"

        fun start(ctx: Context) {
            val i = Intent(ctx, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
            else ctx.startService(i)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, OverlayService::class.java))
        }
    }
}
