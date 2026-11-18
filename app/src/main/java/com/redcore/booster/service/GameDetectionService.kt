package com.redcore.booster.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.redcore.booster.MainActivity
import com.redcore.booster.R
import com.redcore.booster.RedcoreApp
import com.redcore.booster.app
import com.redcore.booster.overlay.OverlayService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Watches which app is in the foreground (via Usage Access) and starts the game's profile when a
 * library game opens; restores settings when you leave it. Polls every 2.5 s only while enabled.
 */
class GameDetectionService : LifecycleService() {
    private var loop: Job? = null
    private var activeGame: String? = null
    private var leftAt = 0L

    override fun onCreate() {
        super.onCreate()
        val n = notification("Watching for games from your library")
        ServiceCompat.startForeground(this, NOTIF_ID, n, if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
        loop = lifecycleScope.launch { run() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            lifecycleScope.launch { app.settings.setAutoDetect(false) }
            stopSelf()
        }
        return START_STICKY
    }

    private suspend fun run() {
        val a = app
        var lastFg: String? = null
        while (lifecycleScope.isActive) {
            if (!a.usage.hasAccess()) { stopSelf(); return }
            val fg = a.usage.foregroundPackage(6000) ?: lastFg
            lastFg = fg
            val library = a.settings.library.first()
            val profile = library.firstOrNull { it.pkg == fg }
            when {
                profile != null && fg != activeGame -> {
                    activeGame = fg
                    leftAt = 0
                    a.boost.startSession(fg, profile.mode, profile.effective())
                    if (profile.overlay && Settings.canDrawOverlays(this)) OverlayService.start(this)
                    updateNotification("Boosting ${a.games.label(fg!!)} · ${profile.mode.title} mode")
                }
                activeGame != null && fg != activeGame && fg != packageName && fg != null -> {
                    if (leftAt == 0L) leftAt = System.currentTimeMillis()
                    if (System.currentTimeMillis() - leftAt > 4000) {
                        a.boost.endSession()
                        OverlayService.stop(this)
                        activeGame = null
                        leftAt = 0
                        updateNotification("Watching for games from your library")
                    }
                }
                else -> leftAt = 0
            }
            delay(2500)
        }
    }

    private fun updateNotification(text: String) {
        getSystemService(android.app.NotificationManager::class.java).notify(NOTIF_ID, notification(text))
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, GameDetectionService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, RedcoreApp.CH_SESSION)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("REDCORE auto game boost")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Turn off", stop)
            .setColor(0xFFFF1E3C.toInt())
            .build()
    }

    override fun onDestroy() {
        loop?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIF_ID = 42
        private const val ACTION_STOP = "stop"
        fun start(c: Context) = ContextCompat.startForegroundService(c, Intent(c, GameDetectionService::class.java))
        fun stop(c: Context) = c.stopService(Intent(c, GameDetectionService::class.java))
    }
}
