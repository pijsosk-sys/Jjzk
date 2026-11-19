package com.redcore.booster.booster

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.redcore.booster.data.UsageRepository
import com.redcore.booster.service.RedcoreAccessibilityService

enum class Perm(val title: String, val why: String) {
    OVERLAY("Display over other apps", "Shows the floating gaming toolkit and HUD on top of games, and keeps the screen awake while you play."),
    USAGE("Usage access", "Detects when you open or leave a game, so sessions start and settings are restored automatically. Also play-time stats."),
    DND("Do Not Disturb access", "Lets gaming modes silence notifications during play."),
    WRITE_SETTINGS("Modify system settings", "Lets modes lock brightness and screen rotation."),
    NOTIFICATIONS("Notifications", "Session notification with quick controls, heat warnings and the session report."),
    BATTERY("Unrestricted battery", "Stops Android (and Samsung's \"sleeping apps\") from pausing auto game boost in the background."),
    ACCESSIBILITY("Screenshot & triggers", "Optional accessibility service for the toolkit's screenshot button and volume-key triggers. Never reads screen content."),
    MEDIA("Media & files", "Lets the storage analyzer find large photos, videos and audio files."),
    MICROPHONE("Microphone", "Voice in screen recordings and 4D vibration (audio is analysed on the phone, never recorded)."),
    NOTIF_ACCESS("Notification access", "Shows messages as a scrolling barrage over games instead of pop-ups. Nothing is stored."),
    CALL_SCREENING("Call blocking", "Lets \"No calls\" decline incoming calls during games (Android 10+)."),
    FPS_ADB("FPS counter (ADB)", "Lets REDCORE read the game's real frame rate. Granted once over ADB."),
    SECURE_SETTINGS("Refresh rate & animations (ADB)", "Unlocks refresh-rate lock (e.g. 120 Hz), faster animations and COOL guard refresh steps. Granted once over ADB.");
}

object Permissions {
    fun granted(c: Context, p: Perm): Boolean = when (p) {
        Perm.OVERLAY -> Settings.canDrawOverlays(c)
        Perm.USAGE -> UsageRepository(c).hasAccess()
        Perm.DND -> c.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted
        Perm.WRITE_SETTINGS -> Settings.System.canWrite(c)
        // The runtime permission alone isn't enough: the user can switch notifications off per app.
        Perm.NOTIFICATIONS -> NotificationManagerCompat.from(c).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 || has(c, Manifest.permission.POST_NOTIFICATIONS))
        Perm.BATTERY -> c.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(c.packageName) == true
        Perm.ACCESSIBILITY -> accessibilityEnabled(c)
        Perm.MEDIA -> mediaPermissions().all { has(c, it) }
        Perm.MICROPHONE -> has(c, Manifest.permission.RECORD_AUDIO)
        Perm.NOTIF_ACCESS -> NotificationManagerCompat.getEnabledListenerPackages(c).contains(c.packageName)
        Perm.CALL_SCREENING -> Build.VERSION.SDK_INT >= 29 && c.getSystemService(android.app.role.RoleManager::class.java)?.isRoleHeld(android.app.role.RoleManager.ROLE_CALL_SCREENING) == true
        Perm.FPS_ADB -> has(c, "android.permission.DUMP")
        Perm.SECURE_SETTINGS -> has(c, "android.permission.WRITE_SECURE_SETTINGS")
    }

    fun has(c: Context, perm: String) = ContextCompat.checkSelfPermission(c, perm) == PackageManager.PERMISSION_GRANTED

    fun mediaPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= 33)
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
    else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    /** Runtime permissions (requested with a pop-up) behind each permission card. */
    fun runtimePermissions(p: Perm): Array<String> = when (p) {
        Perm.MEDIA -> mediaPermissions()
        Perm.MICROPHONE -> arrayOf(Manifest.permission.RECORD_AUDIO)
        Perm.NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray()
        else -> emptyArray()
    }

    fun appDetailsIntent(c: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${c.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Settings screen for special permissions. Runtime permissions are requested by the UI with a launcher instead. */
    @android.annotation.SuppressLint("BatteryLife") // sideloaded gaming utility; the user opts in from a clear explanation
    fun settingsIntent(c: Context, p: Perm): Intent? {
        val pkg = Uri.parse("package:${c.packageName}")
        return when (p) {
            Perm.OVERLAY -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg)
            Perm.USAGE -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            Perm.DND -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            Perm.WRITE_SETTINGS -> Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, pkg)
            Perm.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            Perm.NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, c.packageName)
            Perm.BATTERY -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg)
            Perm.NOTIF_ACCESS -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            Perm.CALL_SCREENING -> null // requested through RoleManager by the UI
            Perm.MEDIA, Perm.MICROPHONE, Perm.SECURE_SETTINGS, Perm.FPS_ADB -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
        }?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Fallback when the direct intent isn't available on a phone. */
    fun fallbackIntent(c: Context, p: Perm): Intent? = when (p) {
        Perm.BATTERY -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${c.packageName}"))
    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun accessibilityEnabled(c: Context): Boolean {
        val enabled = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(c, RedcoreAccessibilityService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    private val storeInstallers = setOf(
        "com.android.vending", "com.sec.android.app.samsungapps", "com.amazon.venezia", "com.huawei.appmarket",
        "com.xiaomi.market", "com.xiaomi.mipicks", "com.heytap.market", "com.oppo.market", "com.vivo.appstore", "org.fdroid.fdroid",
    )

    /** Installer package, or null when unknown (adb, file manager, browser). */
    fun installer(c: Context): String? = runCatching {
        if (Build.VERSION.SDK_INT >= 30) c.packageManager.getInstallSourceInfo(c.packageName).installingPackageName
        else @Suppress("DEPRECATION") c.packageManager.getInstallerPackageName(c.packageName)
    }.getOrNull()

    /**
     * Android 13+ blocks Accessibility and Notification access for apps installed from an APK file until the
     * user taps App info › ⋮ › "Allow restricted settings". True when REDCORE was not installed by an app store.
     */
    fun restrictedSettingsLikely(c: Context): Boolean = Build.VERSION.SDK_INT >= 33 && installer(c) !in storeInstallers

    /** Permissions affected by Android 13+ "restricted settings" for sideloaded apps. */
    val restrictedBySideload = setOf(Perm.ACCESSIBILITY, Perm.NOTIF_ACCESS)

    const val ADB_GRANT = "adb shell pm grant com.redcore.booster android.permission.WRITE_SECURE_SETTINGS"
    const val ADB_GRANT_ALL = "adb shell pm grant com.redcore.booster android.permission.WRITE_SECURE_SETTINGS && adb shell pm grant com.redcore.booster android.permission.DUMP"

    /** Permissions that can only be granted over ADB (shown with a copyable command instead of a button). */
    val adbOnly = setOf(Perm.SECURE_SETTINGS, Perm.FPS_ADB)
}
