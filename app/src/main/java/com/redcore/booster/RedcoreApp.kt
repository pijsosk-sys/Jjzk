package com.redcore.booster

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
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

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_SESSION, "Gaming session", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shown while the gaming toolkit or game detection is running"
            setShowBadge(false)
        })
        nm.createNotificationChannel(NotificationChannel(CH_RECORD, "Screen recording", NotificationManager.IMPORTANCE_LOW))
    }

    companion object {
        const val CH_SESSION = "session"
        const val CH_RECORD = "record"
    }
}

val Context.app: RedcoreApp get() = applicationContext as RedcoreApp
