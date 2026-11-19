package com.redcore.booster.booster

import android.app.NotificationManager
import android.content.ContentResolver
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.redcore.booster.core.RestoreJournal
import com.redcore.booster.core.Tweak

/** Reads and writes the system settings behind each [Tweak]. Values are strings so they can be journaled. */
interface SettingsBackend {
    fun read(t: Tweak): String?

    /** Writes [value] (null = remove the setting). Throws if Android rejects the write. */
    fun write(t: Tweak, value: String?)

    /** Whether REDCORE currently holds the permission needed to write [t]. */
    fun canWrite(t: Tweak): Boolean

    /** The permission or grant the user needs for [t], in plain words. */
    fun requirement(t: Tweak): String
}

/** Real Android implementation. Every call is cheap (binder/settings provider) and safe on any thread. */
class AndroidSettingsBackend(private val context: Context) : SettingsBackend {
    private val cr: ContentResolver get() = context.contentResolver
    private val nm get() = context.getSystemService(NotificationManager::class.java)

    override fun read(t: Tweak): String? = runCatching {
        when (t) {
            Tweak.DND_FILTER -> nm.currentInterruptionFilter.toString()
            Tweak.DND_POLICY -> if (nm.isNotificationPolicyAccessGranted) policyToString(nm.notificationPolicy) else null
            Tweak.BRIGHTNESS_MODE -> Settings.System.getString(cr, Settings.System.SCREEN_BRIGHTNESS_MODE)
            Tweak.BRIGHTNESS -> Settings.System.getString(cr, Settings.System.SCREEN_BRIGHTNESS)
            Tweak.AUTO_ROTATE -> Settings.System.getString(cr, Settings.System.ACCELEROMETER_ROTATION)
            Tweak.USER_ROTATION -> Settings.System.getString(cr, Settings.System.USER_ROTATION)
            Tweak.PEAK_REFRESH -> Settings.System.getString(cr, KEY_PEAK)
            Tweak.MIN_REFRESH -> Settings.System.getString(cr, KEY_MIN)
            Tweak.SAMSUNG_REFRESH_MODE -> Settings.Secure.getString(cr, KEY_SAMSUNG_REFRESH)
            Tweak.MASTER_SYNC -> ContentResolver.getMasterSyncAutomatically().toString()
            Tweak.ANIM_ANIMATOR -> Settings.Global.getString(cr, Settings.Global.ANIMATOR_DURATION_SCALE)
            Tweak.ANIM_TRANSITION -> Settings.Global.getString(cr, Settings.Global.TRANSITION_ANIMATION_SCALE)
            Tweak.ANIM_WINDOW -> Settings.Global.getString(cr, Settings.Global.WINDOW_ANIMATION_SCALE)
            Tweak.PC_POWER_PLAN, Tweak.PC_GAME_MODE, Tweak.PC_POWER_PROFILE -> null // PC-only keys
        }
    }.getOrNull()

    override fun write(t: Tweak, value: String?) {
        when (t) {
            Tweak.DND_FILTER -> nm.setInterruptionFilter(value?.toIntOrNull() ?: NotificationManager.INTERRUPTION_FILTER_ALL)
            Tweak.DND_POLICY -> policyFromString(value)?.let { nm.notificationPolicy = it } ?: error("No Do Not Disturb rules to write")
            Tweak.BRIGHTNESS_MODE -> putSystem(Settings.System.SCREEN_BRIGHTNESS_MODE, value)
            Tweak.BRIGHTNESS -> putSystem(Settings.System.SCREEN_BRIGHTNESS, value)
            Tweak.AUTO_ROTATE -> putSystem(Settings.System.ACCELEROMETER_ROTATION, value)
            Tweak.USER_ROTATION -> putSystem(Settings.System.USER_ROTATION, value)
            Tweak.PEAK_REFRESH -> putSystem(KEY_PEAK, value)
            Tweak.MIN_REFRESH -> putSystem(KEY_MIN, value)
            Tweak.SAMSUNG_REFRESH_MODE -> check(Settings.Secure.putString(cr, KEY_SAMSUNG_REFRESH, value)) { "Rejected by the system" }
            Tweak.MASTER_SYNC -> ContentResolver.setMasterSyncAutomatically(value?.toBooleanStrictOrNull() ?: true)
            Tweak.ANIM_ANIMATOR -> putGlobal(Settings.Global.ANIMATOR_DURATION_SCALE, value)
            Tweak.ANIM_TRANSITION -> putGlobal(Settings.Global.TRANSITION_ANIMATION_SCALE, value)
            Tweak.ANIM_WINDOW -> putGlobal(Settings.Global.WINDOW_ANIMATION_SCALE, value)
            Tweak.PC_POWER_PLAN, Tweak.PC_GAME_MODE, Tweak.PC_POWER_PROFILE -> error("Not an Android setting")
        }
    }

    private fun putSystem(key: String, value: String?) = check(Settings.System.putString(cr, key, value)) { "Rejected by the system" }
    private fun putGlobal(key: String, value: String?) = check(Settings.Global.putString(cr, key, value)) { "Rejected by the system" }

    override fun canWrite(t: Tweak): Boolean = when (t) {
        Tweak.DND_FILTER, Tweak.DND_POLICY -> nm.isNotificationPolicyAccessGranted
        Tweak.BRIGHTNESS_MODE, Tweak.BRIGHTNESS, Tweak.AUTO_ROTATE, Tweak.USER_ROTATION -> Settings.System.canWrite(context)
        // Non-public System keys and Secure/Global keys need the one-time ADB grant.
        Tweak.PEAK_REFRESH, Tweak.MIN_REFRESH, Tweak.SAMSUNG_REFRESH_MODE,
        Tweak.ANIM_ANIMATOR, Tweak.ANIM_TRANSITION, Tweak.ANIM_WINDOW -> Permissions.has(context, WRITE_SECURE)
        Tweak.MASTER_SYNC -> true
        Tweak.PC_POWER_PLAN, Tweak.PC_GAME_MODE, Tweak.PC_POWER_PROFILE -> false
    }

    override fun requirement(t: Tweak): String = when (t) {
        Tweak.DND_FILTER, Tweak.DND_POLICY -> "Do Not Disturb access"
        Tweak.BRIGHTNESS_MODE, Tweak.BRIGHTNESS, Tweak.AUTO_ROTATE, Tweak.USER_ROTATION -> "\"Modify system settings\""
        Tweak.MASTER_SYNC -> "nothing"
        Tweak.PC_POWER_PLAN, Tweak.PC_GAME_MODE, Tweak.PC_POWER_PROFILE -> "the PC version"
        else -> "the one-time ADB grant (Settings › Permissions)"
    }

    companion object {
        const val KEY_PEAK = "peak_refresh_rate"
        const val KEY_MIN = "min_refresh_rate"
        /** Samsung One UI "Motion smoothness": 0 = Standard (60 Hz), 1 = Adaptive (up to the panel maximum). */
        const val KEY_SAMSUNG_REFRESH = "refresh_rate_mode"
        const val WRITE_SECURE = "android.permission.WRITE_SECURE_SETTINGS"

        fun policyToString(p: NotificationManager.Policy): String {
            val conv = if (Build.VERSION.SDK_INT >= 30) ",${p.priorityConversationSenders}" else ""
            return "${p.priorityCategories},${p.priorityCallSenders},${p.priorityMessageSenders},${p.suppressedVisualEffects}$conv"
        }

        fun policyFromString(s: String?): NotificationManager.Policy? {
            val v = s?.split(',')?.mapNotNull { it.trim().toIntOrNull() } ?: return null
            if (v.size < 4) return null
            return if (Build.VERSION.SDK_INT >= 30 && v.size >= 5) NotificationManager.Policy(v[0], v[1], v[2], v[3], v[4])
            else NotificationManager.Policy(v[0], v[1], v[2], v[3])
        }

        /** Restore order: values before the modes that would override them, minimum before peak refresh. */
        val restoreOrder = listOf(
            Tweak.DND_POLICY, Tweak.DND_FILTER,
            Tweak.BRIGHTNESS, Tweak.BRIGHTNESS_MODE,
            Tweak.USER_ROTATION, Tweak.AUTO_ROTATE,
            Tweak.MIN_REFRESH, Tweak.PEAK_REFRESH, Tweak.SAMSUNG_REFRESH_MODE,
            Tweak.MASTER_SYNC,
            Tweak.ANIM_ANIMATOR, Tweak.ANIM_TRANSITION, Tweak.ANIM_WINDOW,
        )
    }
}

/**
 * Persists the restore journal synchronously (commit), *before* the system setting is written, so a crash,
 * a kill by Android or a reboot at any point can be undone on the next start.
 * The file is excluded from cloud backup (see data_extraction_rules.xml): it describes this phone only.
 */
@android.annotation.SuppressLint("ApplySharedPref") // commit() on purpose: the journal must be on disk *before* a setting is changed
class JournalStore(context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    fun load(): RestoreJournal = RestoreJournal.parse(prefs.getString(KEY, null))
    fun save(j: RestoreJournal): Boolean =
        if (j.isEmpty) prefs.edit().remove(KEY).commit() else prefs.edit().putString(KEY, j.serialize()).commit()

    companion object {
        const val FILE = "redcore_restore_journal"
        private const val KEY = "journal"
    }
}
