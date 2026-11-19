package com.redcore.booster

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.redcore.booster.booster.BoostController
import com.redcore.booster.data.DeviceInfoProvider
import com.redcore.booster.data.GameRepository
import com.redcore.booster.data.NetworkTester
import com.redcore.booster.data.SettingsRepository
import com.redcore.booster.data.StorageAnalyzer
import com.redcore.booster.data.SystemMonitor
import com.redcore.booster.data.UsageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RedcoreApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings by lazy { SettingsRepository(this) }
    val usage by lazy { UsageRepository(this) }
    val games by lazy { GameRepository(this, settings) }
    val monitor by lazy { SystemMonitor(this, scope) }
    val boost by lazy { BoostController(this, settings, usage, games) }
    val network by lazy { NetworkTester(this) }
    val storage by lazy { StorageAnalyzer(this, games, usage) }
    val deviceInfo by lazy { DeviceInfoProvider(this) }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { games.onPackagesChanged(i.data?.schemeSpecificPart) }
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_SESSION, "Gaming session", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shown while the gaming toolkit or game detection is running"
            setShowBadge(false)
        })
        nm.createNotificationChannel(NotificationChannel(CH_RECORD, "Screen recording", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_ALERT, "Heat, break & restore alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "COOL guard heat warnings, play-break reminders and settings restored after an interruption"
        })
        // A new process means no session is running: put back anything a crash, an Android kill or a reboot
        // left changed, before any new session can start (BoostController serialises the two).
        scope.launch {
            runCatching { settings.clearLegacyRestorePoint() }
            boost.recoverIfNeeded()
        }
        scope.launch { settings.settings.collect { settings.latest = it } }
        // Keep labels / "installed" flags in the game library current when apps are installed or removed.
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED); addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageReceiver, f, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    /** When REDCORE goes to the background (e.g. a game opens), give our caches back to the system. */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        @Suppress("DEPRECATION")
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) games.clearIconCache()
    }

    companion object {
        const val CH_SESSION = "session"
        const val CH_RECORD = "record"
        const val CH_ALERT = "alerts"
    }
}

val Context.app: RedcoreApp get() = applicationContext as RedcoreApp
