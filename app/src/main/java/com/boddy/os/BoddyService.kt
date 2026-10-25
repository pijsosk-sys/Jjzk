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
import java.io.File

/** Keeps Linux running in the background: installation and the desktop session. */
class BoddyService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var proot: Proot
    private lateinit var installer: Installer
    private lateinit var bridge: PhoneBridge
    private var desktop: Process? = null
    private val audio = AudioStream()
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        proot = Proot(this)
        installer = Installer(this, proot)
        bridge = PhoneBridge(File(proot.rootfs, "opt/boddy/.bridge-token"), PhoneActions(this))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        when (intent?.action) {
            ACTION_INSTALL -> install()
            ACTION_START -> startDesktop(intent.getIntExtra(EXTRA_WIDTH, 1600), intent.getIntExtra(EXTRA_HEIGHT, 740))
            ACTION_RESTART -> {
                stopDesktop()
                startDesktop(intent.getIntExtra(EXTRA_WIDTH, 1300), intent.getIntExtra(EXTRA_HEIGHT, 600))
            }
            ACTION_RUN -> intent.getStringExtra(EXTRA_APP)?.let { runInDesktop(it) }
            ACTION_STOP -> stopAll()
        }
        return START_NOT_STICKY
    }

    private fun install() {
        if (Boddy.phase.value == Boddy.Phase.INSTALLING) return
        Boddy.phase.value = Boddy.Phase.INSTALLING
        Boddy.clearLog()
        acquireWakeLock()
        scope.launch {
            try {
                installer.install()
                Boddy.status.value = "Boddy is installed."
                Boddy.phase.value = Boddy.Phase.READY
            } catch (t: Throwable) {
                val reason = when (t) {
                    is java.io.FileNotFoundException -> "Download not found: ${t.message}"
                    is java.net.UnknownHostException -> "No internet connection"
                    else -> t.message ?: t.toString()
                }
                Boddy.log("ERROR: $reason")
                Boddy.log(android.util.Log.getStackTraceString(t))
                Boddy.status.value = "$reason. Tap Retry install."
                Boddy.phase.value = Boddy.Phase.ERROR
            } finally {
                Boddy.progress.value = -1
                if (desktop == null) {
                    releaseWakeLock()
                    stopSelf()
                }
            }
        }
    }

    private fun startDesktop(width: Int, height: Int) {
        if (desktop?.isAlive == true) return
        Boddy.phase.value = Boddy.Phase.RUNNING
        acquireWakeLock()
        bridge.start()
        audio.start()
        scope.launch {
            try {
                installer.copyScripts()
                PhoneApps.writeForLinux(this@BoddyService, proot.rootfs)
                Settings(this@BoddyService).agentMode?.let { AgentConfig.apply(proot.rootfs, it) }
            } catch (e: Exception) {
                Boddy.log("Couldn't write phone info: ${e.message}")
            }
            val p = proot.process("/bin/bash", "/opt/boddy/start-desktop.sh", width.toString(), height.toString()).start()
            desktop = p
            p.inputStream.bufferedReader().forEachLine { Boddy.log(it) }
            p.waitFor()
            if (desktop === p) {
                desktop = null
                bridge.stop()
                audio.stop()
                Boddy.phase.value = Boddy.Phase.READY
                releaseWakeLock()
                stopSelf()
            }
        }
    }

    /** Opens one of the Boddy apps (by launcher id) in the running desktop. */
    private fun runInDesktop(app: String) {
        if (!app.matches(Regex("[a-z-]+"))) return
        scope.launch {
            proot.run("/bin/bash", "/opt/boddy/run-in-desktop.sh", app) { Boddy.log(it) }
        }
    }

    /** Stops the desktop without stopping the service (e.g. to restart at a new size). */
    private fun stopDesktop() {
        desktop?.let {
            desktop = null
            it.destroy()
            it.waitFor()
        }
    }

    private fun stopAll() {
        stopDesktop()
        bridge.stop()
        audio.stop()
        if (Boddy.phase.value == Boddy.Phase.RUNNING) Boddy.phase.value = Boddy.Phase.READY
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        desktop?.destroy()
        bridge.stop()
        audio.stop()
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        if (wakeLock != null) return
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Boddy::running")
            .apply { acquire() }
    }

    private fun releaseWakeLock() {
        wakeLock?.release()
        wakeLock = null
    }

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Boddy", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
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
        const val ACTION_RUN = "com.boddy.os.RUN"
        const val ACTION_RESTART = "com.boddy.os.RESTART"
        const val ACTION_STOP = "com.boddy.os.STOP"
        const val EXTRA_WIDTH = "width"
        const val EXTRA_HEIGHT = "height"
        const val EXTRA_APP = "app"

        fun send(context: Context, action: String, configure: Intent.() -> Unit = {}) {
            val intent = Intent(context, BoddyService::class.java).setAction(action).apply(configure)
            context.startForegroundService(intent)
        }

        fun isInstalled(context: Context) = Installer(context, Proot(context)).isInstalled()
    }
}
