package com.boddy.os

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Keeps Linux running in the background: installation and the desktop session. */
class BoddyService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var proot: Proot
    private lateinit var installer: Installer
    private var desktop: Process? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        proot = Proot(this)
        installer = Installer(this, proot)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        when (intent?.action) {
            ACTION_INSTALL -> install()
            ACTION_START -> startDesktop(intent.getIntExtra(EXTRA_WIDTH, 1600), intent.getIntExtra(EXTRA_HEIGHT, 740))
            ACTION_STOP -> stopAll()
        }
        return START_NOT_STICKY
    }

    private fun install() {
        if (Boddy.phase.value == Boddy.Phase.INSTALLING) return
        Boddy.phase.value = Boddy.Phase.INSTALLING
        Boddy.clearLog()
        scope.launch {
            try {
                installer.install()
                Boddy.status.value = "Boddy is installed."
                Boddy.phase.value = Boddy.Phase.READY
            } catch (t: Throwable) {
                Boddy.log("ERROR: ${t.message ?: t}")
                Boddy.status.value = t.message ?: "Installation failed"
                Boddy.phase.value = Boddy.Phase.ERROR
            } finally {
                Boddy.progress.value = -1
                if (desktop == null) stopSelf()
            }
        }
    }

    private fun startDesktop(width: Int, height: Int) {
        if (desktop?.isAlive == true) return
        Boddy.phase.value = Boddy.Phase.RUNNING
        acquireWakeLock()
        scope.launch {
            val p = proot.process("/bin/bash", "/opt/boddy/start-desktop.sh", width.toString(), height.toString()).start()
            desktop = p
            p.inputStream.bufferedReader().forEachLine { Boddy.log(it) }
            p.waitFor()
            if (desktop === p) {
                desktop = null
                Boddy.phase.value = Boddy.Phase.READY
                releaseWakeLock()
                stopSelf()
            }
        }
    }

    private fun stopAll() {
        desktop?.destroy()
        desktop = null
        if (Boddy.phase.value == Boddy.Phase.RUNNING) Boddy.phase.value = Boddy.Phase.READY
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        desktop?.destroy()
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        if (wakeLock != null) return
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Boddy::desktop")
            .apply { acquire() }
    }

    private fun releaseWakeLock() {
        wakeLock?.release()
        wakeLock = null
    }

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Boddy", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, DesktopActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, BoddyService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Boddy is running")
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Shut down", stop).build())
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    companion object {
        private const val CHANNEL = "boddy"
        const val ACTION_INSTALL = "com.boddy.os.INSTALL"
        const val ACTION_START = "com.boddy.os.START"
        const val ACTION_STOP = "com.boddy.os.STOP"
        const val EXTRA_WIDTH = "width"
        const val EXTRA_HEIGHT = "height"

        fun send(context: Context, action: String, configure: Intent.() -> Unit = {}) {
            val intent = Intent(context, BoddyService::class.java).setAction(action).apply(configure)
            context.startForegroundService(intent)
        }

        fun isInstalled(context: Context) = Installer(context, Proot(context)).isInstalled()
    }
}
