package com.redcore.booster.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.redcore.booster.app
import kotlinx.coroutines.launch

/**
 * After a reboot (or an app update) REDCORE may have been in the middle of a game session: Do Not Disturb,
 * brightness, refresh rate and animation settings survive a reboot. This puts them back, then resumes
 * auto game boost if it was on. Starting the foreground service from these broadcasts is allowed on
 * every Android version REDCORE supports.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        val app = context.app
        app.scope.launch {
            try {
                app.boost.recoverIfNeeded()
                val s = app.settings.current()
                if (s.autoDetect && app.usage.hasAccess()) GameDetectionService.start(context)
            } finally {
                pending.finish()
            }
        }
    }
}
