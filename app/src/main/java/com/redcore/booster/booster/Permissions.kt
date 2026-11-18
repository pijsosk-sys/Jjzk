package com.redcore.booster.booster

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.redcore.booster.data.UsageRepository
import com.redcore.booster.service.RedcoreAccessibilityService

enum class Perm(val title: String, val why: String) {
    OVERLAY("Display over other apps", "Shows the floating gaming toolkit and HUD on top of games."),
    USAGE("Usage access", "Play-time statistics, recently played games and automatic game detection."),
    DND("Do Not Disturb access", "Lets gaming modes silence notifications during play."),
    WRITE_SETTINGS("Modify system settings", "Lets modes and the toolkit change screen brightness."),
    NOTIFICATIONS("Notifications", "Shows the active-session notification with quick controls."),
    ACCESSIBILITY("Screenshot & triggers", "Optional accessibility service for the toolkit's screenshot button and volume-key triggers. Never reads screen content."),
    MEDIA("Media & files", "Lets the storage analyzer find large photos, videos and audio files."),
    MICROPHONE("Microphone", "Voice in screen recordings and 4D vibration (audio is analysed on the phone, never recorded)."),
    NOTIF_ACCESS("Notification access", "Shows messages as a scrolling barrage over games instead of pop-ups. Nothing is stored."),
    CALL_SCREENING("Call blocking", "Lets \"No calls\" decline incoming calls during games (Android 10+)."),
    FPS_ADB("FPS counter (ADB)", "Experimental: lets REDCORE read the game's real frame rate. Granted from a computer via ADB."),
    SECURE_SETTINGS("Refresh-rate control (ADB)", "Needed on most phones to change refresh rate. Can only be granted from a computer via ADB.");
}

object Permissions {
    fun granted(c: Context, p: Perm): Boolean = when (p) {
        Perm.OVERLAY -> Settings.canDrawOverlays(c)
        Perm.USAGE -> UsageRepository(c).hasAccess()
        Perm.DND -> c.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted
        Perm.WRITE_SETTINGS -> Settings.System.canWrite(c)
        Perm.NOTIFICATIONS -> Build.VERSION.SDK_INT < 33 || has(c, Manifest.permission.POST_NOTIFICATIONS)
        Perm.ACCESSIBILITY -> accessibilityEnabled(c)
        Perm.MEDIA -> mediaPermissions().all { has(c, it) }
        Perm.MICROPHONE -> has(c, Manifest.permission.RECORD_AUDIO)
        Perm.NOTIF_ACCESS -> androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(c).contains(c.packageName)
        Perm.CALL_SCREENING -> Build.VERSION.SDK_INT >= 29 && c.getSystemService(android.app.role.RoleManager::class.java)?.isRoleHeld(android.app.role.RoleManager.ROLE_CALL_SCREENING) == true
        Perm.FPS_ADB -> has(c, "android.permission.DUMP")
        Perm.SECURE_SETTINGS -> has(c, "android.permission.WRITE_SECURE_SETTINGS")
    }

    fun has(c: Context, perm: String) = ContextCompat.checkSelfPermission(c, perm) == PackageManager.PERMISSION_GRANTED

    fun mediaPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= 33)
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
    else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    /** Settings screen for special permissions. Runtime permissions are requested by the UI with a launcher instead. */
    fun settingsIntent(c: Context, p: Perm): Intent? {
        val pkg = Uri.parse("package:${c.packageName}")
        return when (p) {
            Perm.OVERLAY -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg)
            Perm.USAGE -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            Perm.DND -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            Perm.WRITE_SETTINGS -> Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, pkg)
            Perm.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            Perm.NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= 26) Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, c.packageName) else null
            Perm.NOTIF_ACCESS -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            Perm.CALL_SCREENING -> null // requested through RoleManager by the UI
            Perm.MEDIA, Perm.MICROPHONE, Perm.SECURE_SETTINGS, Perm.FPS_ADB -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
        }?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun accessibilityEnabled(c: Context): Boolean {
        val enabled = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(c, RedcoreAccessibilityService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    const val ADB_GRANT = "adb shell pm grant com.redcore.booster android.permission.WRITE_SECURE_SETTINGS"
    const val ADB_GRANT_ALL = "adb shell pm grant com.redcore.booster android.permission.WRITE_SECURE_SETTINGS && adb shell pm grant com.redcore.booster android.permission.DUMP"

    /** Permissions that can only be granted over ADB (shown with a copyable command instead of a button). */
    val adbOnly = setOf(Perm.SECURE_SETTINGS, Perm.FPS_ADB)
}
