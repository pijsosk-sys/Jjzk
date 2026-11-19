package com.redcore.booster.service

import android.app.KeyguardManager
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
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
import com.redcore.booster.core.AppKind
import com.redcore.booster.core.EndReason
import com.redcore.booster.core.Foreground
import com.redcore.booster.core.ForegroundTracker
import com.redcore.booster.core.SessionCommand
import com.redcore.booster.core.SessionMachine
import com.redcore.booster.data.GameProfile
import com.redcore.booster.overlay.OverlayService
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Watches which app is on screen (Usage Access) and drives game sessions:
 *  - AUTO mode ("Auto game boost" on): starts a game's profile when a library game opens.
 *  - WATCH mode: a session started from REDCORE's Play button is ended (and settings restored) when you
 *    leave the game, even with auto boost off.
 *
 * Foreground state is modelled explicitly (unknown / screen off / locked / REDCORE / launcher / other app /
 * game) by [ForegroundTracker]; [SessionMachine] decides transitions with grace periods, so a missing event
 * never keeps a stale session alive and a quick notification shade or lock doesn't end one.
 * Polls every 2 s while the screen is on and every 10 s while it's off.
 */
class GameDetectionService : LifecycleService() {
    private val tracker = ForegroundTracker()
    private val machine = SessionMachine()
    @Volatile private var library: Map<String, GameProfile> = emptyMap()
    @Volatile private var autoDetect = false
    private var launchers: Set<String> = emptySet()
    private var launchersAt = 0L
    private var lastText = ""
    /** True while this service itself is ending a session, so the session listener doesn't treat it as a user action. */
    private var endingSelf = false

    override fun onCreate() {
        super.onCreate()
        val ok = runCatching {
            ServiceCompat.startForeground(this, NOTIF_ID, notification("Starting game detection…"),
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
        }.isSuccess
        if (!ok) { stopSelf(); return }
        running = true
        val a = app
        lifecycleScope.launch { a.settings.library.collect { lib -> library = lib.associateBy { it.pkg } } }
        lifecycleScope.launch { a.settings.settings.collect { autoDetect = it.autoDetect } }
        lifecycleScope.launch {
            a.boost.session.collect { s ->
                when {
                    s != null && s.pkg != null && s.pkg != machine.active -> machine.onExternalStart(s.pkg)
                    s == null && machine.active != null && !endingSelf -> machine.onExternalEnd(byUser = true)
                }
            }
        }
        lifecycleScope.launch { loop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) lifecycleScope.launch {
            // "Turn off" from the notification: stop auto boost. A running session is still watched so its
            // settings are restored when you leave the game.
            app.settings.setAutoDetect(false)
            autoDetect = false
            if (app.boost.session.value == null) stopSelf()
        }
        return START_STICKY
    }

    private fun classify(pkg: String): AppKind = when {
        pkg == packageName -> AppKind.SELF
        pkg in launchers -> AppKind.LAUNCHER
        library.containsKey(pkg) -> AppKind.GAME
        else -> AppKind.OTHER
    }

    private fun refreshLaunchers() {
        val now = System.currentTimeMillis()
        if (now - launchersAt < 5 * 60_000 && launchers.isNotEmpty()) return
        launchersAt = now
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        launchers = runCatching { packageManager.queryIntentActivities(home, 0).map { it.activityInfo.packageName }.toSet() }.getOrDefault(emptySet())
    }

    private suspend fun loop() {
        val a = app
        val power = getSystemService(PowerManager::class.java)
        val keyguard = getSystemService(KeyguardManager::class.java)
        while (lifecycleScope.isActive) {
            val now = System.currentTimeMillis()
            if (!a.usage.hasAccess()) {
                // Can't see the screen any more: end now rather than leave settings changed indefinitely.
                execute(machine.onTrackingLost())
                tracker.reset()
                notifyAccessLost()
                stopSelf()
                return
            }
            refreshLaunchers()
            val interactive = power?.isInteractive != false
            val locked = keyguard?.isKeyguardLocked == true
            if (interactive) {
                val from = if (tracker.lastEventTimeMs == Long.MIN_VALUE) now - LOOKBACK_MS else (tracker.lastEventTimeMs - OVERLAP_MS)
                tracker.accept(a.usage.appEvents(from, now))
            }
            val fg = ForegroundTracker.withScreenState(tracker.current(::classify), interactive, locked)
            execute(machine.onForeground(fg, now, autoStart = autoDetect))
            updateNotification(fg, now)
            if (!autoDetect && machine.active == null && a.boost.session.value == null) {
                // Watch mode finished: nothing left to watch.
                stopSelf()
                return
            }
            delay(if (interactive) 2_000 else 10_000)
        }
    }

    private suspend fun execute(cmds: List<SessionCommand>) {
        val a = app
        for ((i, c) in cmds.withIndex()) when (c) {
            is SessionCommand.End -> {
                // Game A -> game B: BoostController switches in place (report + log for A, journal kept), so
                // settings don't flicker back to normal and on again.
                if (c.reason == EndReason.SWITCHED_GAME && cmds.getOrNull(i + 1) is SessionCommand.Start) continue
                endingSelf = true
                try {
                    val cur = a.boost.session.value
                    if (cur == null || cur.pkg == c.pkg) a.boost.endSession(c.reason)
                } finally { endingSelf = false }
                OverlayService.stop(this)
            }
            is SessionCommand.Start -> {
                val profile = library[c.pkg] ?: continue
                a.boost.startSession(c.pkg, profile.mode, profile.effective())
                if (profile.overlay && Settings.canDrawOverlays(this)) OverlayService.start(this)
            }
        }
    }

    private fun updateNotification(fg: Foreground, now: Long) {
        val s = app.boost.session.value
        val text = when {
            s != null && machine.pendingEndInMs(now) != null ->
                "${s.label}: you left the game — restoring settings in ${(machine.pendingEndInMs(now)!! / 1000) + 1} s"
            s != null -> "Boosting ${s.label} · ${s.mode.title} · ${s.appliedCount} active"
            fg is Foreground.ScreenOff -> "Screen off · watching for games"
            autoDetect -> "Watching for games from your library"
            else -> "Watching your game session"
        }
        if (text == lastText) return
        lastText = text
        runCatching { getSystemService(android.app.NotificationManager::class.java).notify(NOTIF_ID, notification(text)) }
    }

    private fun notifyAccessLost() {
        val n = NotificationCompat.Builder(this, RedcoreApp.CH_ALERT)
            .setSmallIcon(R.drawable.ic_stat).setColor(0xFFFF1E3C.toInt())
            .setContentTitle("Auto game boost paused")
            .setContentText("Usage access was turned off, so REDCORE ended the session and restored your settings.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Usage access was turned off, so REDCORE ended the session and restored your settings. Turn it back on in REDCORE › Settings to resume."))
            .setContentIntent(PendingIntent.getActivity(this, 5, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .setAutoCancel(true).build()
        runCatching { getSystemService(android.app.NotificationManager::class.java).notify(NOTIF_ACCESS, n) }
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, GameDetectionService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, RedcoreApp.CH_SESSION)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("REDCORE game boost")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, "Turn off auto boost", stop)
            .setColor(0xFFFF1E3C.toInt())
            .build()
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    companion object {
        private const val NOTIF_ID = 42
        private const val NOTIF_ACCESS = 43
        private const val ACTION_STOP = "stop"
        private const val LOOKBACK_MS = 30 * 60_000L
        private const val OVERLAP_MS = 2_000L
        @Volatile var running = false
            private set

        /** Starts detection. Must be called while REDCORE is in the foreground or from an exempt context (boot). */
        fun start(c: Context): Boolean = runCatching {
            ContextCompat.startForegroundService(c, Intent(c, GameDetectionService::class.java)); true
        }.getOrDefault(false)

        fun stop(c: Context) = c.stopService(Intent(c, GameDetectionService::class.java))
    }
}
