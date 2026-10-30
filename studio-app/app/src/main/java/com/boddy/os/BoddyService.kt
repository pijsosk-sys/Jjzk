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
import android.net.wifi.WifiManager
import android.os.PowerManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

/** Keeps Linux running in the background: installation and the desktop session. */
class BoddyService : Service() {

    /** Errors in background jobs are logged instead of crashing the whole app. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, t ->
        Boddy.log("Background error: ${t.message ?: t}")
        CrashLog.record(this@BoddyService, t, fatal = false)
    })
    private lateinit var proot: Proot
    private lateinit var installer: Installer
    private lateinit var bridge: PhoneBridge
    private var desktop: Process? = null
    private val audio = AudioStream()
    @Volatile private var starting = false
    /** When the watchdog last restarted a desktop that died on its own. */
    private val recentRestarts = mutableListOf<Long>()
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        proot = Proot(this)
        installer = Installer(this, proot)
        bridge = PhoneBridge(File(proot.rootfs, "opt/boddy/.bridge-token"), PhoneActions(this))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // "Shut down" from the notification arrives as a plain service start; going foreground
        // just to stop would be refused from the background on newer Android.
        if (intent?.action == ACTION_STOP) {
            stopAll()
            return START_NOT_STICKY
        }
        goForeground()
        when (intent?.action) {
            ACTION_INSTALL -> install()
            ACTION_START -> startDesktop(intent.getIntExtra(EXTRA_WIDTH, 1600), intent.getIntExtra(EXTRA_HEIGHT, 740))
            ACTION_RESTART -> {
                stopDesktop()
                startDesktop(intent.getIntExtra(EXTRA_WIDTH, 1300), intent.getIntExtra(EXTRA_HEIGHT, 600))
            }
            ACTION_RUN -> intent.getStringExtra(EXTRA_APP)?.let { runInDesktop(it) }
            // Android killed us and is restarting the service: bring the desktop back if it was on.
            null -> Settings(this).lastDesktop?.let { (w, h) ->
                Boddy.log("Boddy was stopped by Android; restarting the desktop")
                startDesktop(w, h)
            } ?: stopSelf()
        }
        // Ask Android to restart the service if it has to kill it.
        return START_STICKY
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
        // `desktop` is only set once the process starts, so also guard the moment before.
        if (starting || desktop?.isAlive == true) return
        starting = true
        Boddy.phase.value = Boddy.Phase.RUNNING
        Settings(this).lastDesktop = width to height
        acquireWakeLock()
        bridge.start()
        audio.start()
        scope.launch {
            try {
                installer.copyScripts()
                installer.writeResolvConf()
            } catch (e: Exception) {
                Boddy.log("Couldn't copy the launcher scripts: ${e.message}")
            }
            val p = try {
                proot.process("/bin/bash", "/opt/boddy/start-desktop.sh", width.toString(), height.toString()).start()
            } finally {
                starting = false
            }
            desktop = p
            p.inputStream.bufferedReader().forEachLine { Boddy.log(it) }
            val code = p.waitFor()
            if (desktop !== p) return@launch // stopped or restarted on purpose

            // The desktop died on its own (Android killed part of it, or it crashed): bring it back.
            val now = System.currentTimeMillis()
            recentRestarts.removeAll { now - it > 10 * 60_000 }
            if (recentRestarts.size < 3) {
                recentRestarts += now
                Boddy.log("The desktop stopped unexpectedly (exit $code); restarting it…")
                desktop = null
                startDesktop(width, height)
                return@launch
            }
            Boddy.log("The desktop keeps stopping; giving up. Check \"Keep Boddy running\" on the home screen.")
            desktop = null
            bridge.stop()
            audio.stop()
            Settings(this@BoddyService).lastDesktop = null
            Boddy.phase.value = Boddy.Phase.READY
            releaseWakeLock()
            stopSelf()
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
        Settings(this).lastDesktop = null
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

    /** Keeps the CPU and Wi-Fi at full speed while Boddy works, even with the screen off. */
    private fun acquireWakeLock() {
        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Boddy::running")
                .apply { acquire() }
        }
        if (wifiLock == null) {
            @Suppress("DEPRECATION")
            wifiLock = getSystemService(WifiManager::class.java)
                ?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Boddy::running")
                ?.apply { setReferenceCounted(false); acquire() }
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        wifiLock?.let { if (it.isHeld) it.release() }
        wifiLock = null
    }

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Studio Launcher", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, BoddyService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Studio Launcher is running")
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
