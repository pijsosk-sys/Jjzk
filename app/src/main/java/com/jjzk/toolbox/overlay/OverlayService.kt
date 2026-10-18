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
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.jjzk.toolbox.MainActivity
import com.jjzk.toolbox.R

/**
 * Floating overlay: an always-on-top drag-handle plus a lazy-attached
 * panel. Panel is inflated once and cached — toggling it only flips
 * window attachment, so expanding the menu never costs an inflate pass.
 * Dragging the handle no longer re-layouts the panel on every move
 * event; the panel repositions once on ACTION_UP.
 */
class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var controller: OverlayController
    private var handle: View? = null
    private var panel: View? = null
    private var panelAttached = false
    private lateinit var handleLp: WindowManager.LayoutParams
    private lateinit var panelLp: WindowManager.LayoutParams
    private var touchSlop = 10

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        controller = OverlayController(this)
        touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        startInForeground()
        attachHandle()
        buildPanel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        return START_STICKY
    }

    override fun onDestroy() {
        if (panelAttached) safeRemove(panel!!)
        handle?.let { safeRemove(it) }
        panel = null; handle = null; panelAttached = false
        super.onDestroy()
    }

    private fun attachHandle() {
        val view = LayoutInflater.from(this).inflate(R.layout.overlay_handle, null)
        handleLp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24; y = 240
        }
        attachDragWithTap(view, handleLp) { togglePanel() }
        wm.addView(view, handleLp)
        handle = view
    }

    private fun buildPanel() {
        val view = LayoutInflater.from(this).inflate(R.layout.overlay_panel, null)
        // X on the panel hides the panel only; the handle and service stay.
        controller.bind(view) { if (panelAttached) togglePanel() }
        panelLp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100; y = 200
        }
        panel = view
    }

    private fun togglePanel() {
        val p = panel ?: return
        if (panelAttached) {
            safeRemove(p); panelAttached = false
            return
        }
        panelLp.x = handleLp.x + 72
        panelLp.y = (handleLp.y - 40).coerceAtLeast(0)
        wm.addView(p, panelLp)
        panelAttached = true
    }

    @Suppress("ClickableViewAccessibility")
    private fun attachDragWithTap(view: View, lp: WindowManager.LayoutParams, onTap: () -> Unit) {
        var startLpX = 0; var startLpY = 0
        var downX = 0f; var downY = 0f
        var moved = false
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startLpX = lp.x; startLpY = lp.y
                    downX = event.rawX; downY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!moved && (kotlin.math.abs(dx) > touchSlop || kotlin.math.abs(dy) > touchSlop))
                        moved = true
                    if (moved) {
                        lp.x = startLpX + dx.toInt()
                        lp.y = startLpY + dy.toInt()
                        wm.updateViewLayout(v, lp)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        v.performClick(); onTap()
                    } else if (panelAttached) {
                        panelLp.x = lp.x + 72
                        panelLp.y = (lp.y - 40).coerceAtLeast(0)
                        wm.updateViewLayout(panel!!, panelLp)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun safeRemove(v: View) = try { wm.removeView(v) } catch (_: Throwable) {}

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
            .setContentText("Tap the floating handle for the menu")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentIntent(openApp)
            .addAction(android.R.drawable.ic_delete, "Stop", stop)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else
            startForeground(NOTIF_ID, notif)
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
        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, OverlayService::class.java)) }
    }
}
