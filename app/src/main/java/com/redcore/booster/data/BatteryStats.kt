package com.redcore.booster.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.BatteryManager

object BatteryStats {
    fun statusName(s: Int): String? = when (s) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
        BatteryManager.BATTERY_STATUS_FULL -> "Full"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
        else -> null
    }

    fun plugName(p: Int): String = when (p) {
        BatteryManager.BATTERY_PLUGGED_AC -> "AC charger"
        BatteryManager.BATTERY_PLUGGED_USB -> "USB"
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
        8 -> "Dock"
        else -> "Battery"
    }

    fun healthName(h: Int): String? = when (h) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
        BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
        BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
        else -> null
    }

    /**
     * Design capacity as declared in the system power profile. This is an internal Android class,
     * so it may be missing on some ROMs; in that case we report nothing rather than guessing.
     */
    @SuppressLint("PrivateApi")
    fun designCapacityMah(context: Context): Int? = try {
        val cls = Class.forName("com.android.internal.os.PowerProfile")
        val profile = cls.getConstructor(Context::class.java).newInstance(context)
        val v = cls.getMethod("getBatteryCapacity").invoke(profile) as Double
        v.toInt().takeIf { it in 500..30000 }
    } catch (_: Throwable) {
        null
    }
}
