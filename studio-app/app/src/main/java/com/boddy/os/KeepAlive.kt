package com.boddy.os

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as AndroidSettings

/**
 * The phone settings that decide whether Android kills Boddy in the background (e.g. while
 * you watch TikTok). Each check is true when it's fine, false when it needs fixing, or null
 * when this phone doesn't let us tell.
 */
object KeepAlive {

    /** Battery optimisation off ("Unrestricted"), so Samsung doesn't put Boddy to sleep. */
    fun batteryUnrestricted(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    /**
     * Android 12+ kills apps with many background processes (a desktop has dozens). Android 14+
     * has a Developer options switch for it, stored in this global setting.
     */
    fun childProcessLimitOff(context: Context): Boolean? {
        if (Build.VERSION.SDK_INT < 31) return true
        val value = AndroidSettings.Global.getString(context.contentResolver, "settings_enable_monitor_phantom_procs")
        return when (value) {
            "false" -> true
            "true" -> false
            else -> if (Build.VERSION.SDK_INT >= 34) false else null
        }
    }

    fun allGood(context: Context) = batteryUnrestricted(context) && childProcessLimitOff(context) != false

    @SuppressLint("BatteryLife") // Boddy runs a whole desktop; it genuinely needs this.
    fun fixBattery(activity: Activity) {
        open(activity, Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${activity.packageName}")))
            || open(activity, Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    /** Opens Developer options (or About phone, where it's unlocked by tapping Build number). */
    fun openDeveloperOptions(activity: Activity) {
        open(activity, Intent(AndroidSettings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
            || open(activity, Intent(AndroidSettings.ACTION_DEVICE_INFO_SETTINGS))
    }

    /** Whether Linux can see the phone's files (Android 11+ "All files access"). */
    fun phoneFilesAllowed(): Boolean = Build.VERSION.SDK_INT < 30 || android.os.Environment.isExternalStorageManager()

    fun allowPhoneFiles(activity: Activity) {
        if (Build.VERSION.SDK_INT < 30) return
        open(activity, Intent(AndroidSettings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${activity.packageName}")))
            || open(activity, Intent(AndroidSettings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
    }

    private fun open(activity: Activity, intent: Intent) = try {
        activity.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
