package com.redcore.booster.booster

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.provider.Settings
import android.view.Display
import com.redcore.booster.data.GameMode
import com.redcore.booster.data.GameRepository
import com.redcore.booster.data.ModeSpec
import com.redcore.booster.data.RefreshTarget
import com.redcore.booster.data.SettingsRepository
import com.redcore.booster.data.UsageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.roundToInt

enum class ActionState { APPLIED, SKIPPED, BLOCKED }

/** Outcome of a single booster action. Shown to the user verbatim — nothing is claimed that did not happen. */
data class ActionResult(val name: String, val state: ActionState, val detail: String)

data class GameSession(
    val pkg: String?,
    val label: String,
    val mode: GameMode,
    val spec: ModeSpec,
    val startedAt: Long,
    val results: List<ActionResult>,
)

class BoostController(
    private val context: Context,
    private val settings: SettingsRepository,
    private val usage: UsageRepository,
    private val games: GameRepository,
) {
    private val nm = context.getSystemService(NotificationManager::class.java)
    private val am = context.getSystemService(ActivityManager::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)

    private val _session = MutableStateFlow<GameSession?>(null)
    val session: StateFlow<GameSession?> = _session.asStateFlow()

    // ---------------------------------------------------------------- capability probes

    fun supportedRefreshRates(): List<Float> {
        val d = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY) ?: return emptyList()
        return d.supportedModes.map { it.refreshRate }.distinctBy { it.roundToInt() }.sorted()
    }

    fun canSetBrightness() = Settings.System.canWrite(context)
    fun canSetDnd() = nm.isNotificationPolicyAccessGranted

    // ---------------------------------------------------------------- individual actions

    fun isDndOn(): Boolean = nm.currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL

    fun setDnd(on: Boolean): ActionResult {
        if (!canSetDnd()) return ActionResult("Do Not Disturb", ActionState.BLOCKED, "Grant Do Not Disturb access to enable this.")
        return try {
            nm.setInterruptionFilter(if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL)
            ActionResult("Do Not Disturb", ActionState.APPLIED, if (on) "Priority-only mode: notifications are silenced." else "Notifications restored.")
        } catch (e: Exception) {
            ActionResult("Do Not Disturb", ActionState.BLOCKED, e.message ?: "Rejected by system.")
        }
    }

    /** Brightness 0–100 %. */
    fun currentBrightnessPct(): Int? = try {
        (Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) * 100f / 255f).roundToInt()
    } catch (_: Exception) { null }

    fun isAutoBrightness(): Boolean = try {
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
    } catch (_: Exception) { false }

    fun setBrightness(pct: Int): ActionResult {
        if (!canSetBrightness()) return ActionResult("Brightness", ActionState.BLOCKED, "Grant \"Modify system settings\" to control brightness.")
        return try {
            val cr = context.contentResolver
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, (pct.coerceIn(1, 100) * 255 / 100))
            ActionResult("Brightness", ActionState.APPLIED, "Set to $pct % (auto-brightness paused).")
        } catch (e: Exception) {
            ActionResult("Brightness", ActionState.BLOCKED, e.message ?: "Rejected by system.")
        }
    }

    /**
     * Refresh rate is not a public setting. We try the standard keys used by AOSP; most ROMs only accept
     * them from apps that were granted WRITE_SECURE_SETTINGS over ADB. The result tells the user exactly what happened.
     */
    fun setRefresh(target: RefreshTarget): ActionResult {
        val rates = supportedRefreshRates()
        if (target == RefreshTarget.DEFAULT) return ActionResult("Refresh rate", ActionState.SKIPPED, "Left at system default.")
        if (rates.size < 2) return ActionResult("Refresh rate", ActionState.SKIPPED, "This display reports a single refresh rate (${rates.firstOrNull()?.roundToInt() ?: "?"} Hz).")
        val value = if (target == RefreshTarget.HIGHEST) rates.last() else rates.first()
        return try {
            val cr = context.contentResolver
            Settings.System.putFloat(cr, KEY_PEAK, value)
            if (target == RefreshTarget.HIGHEST) Settings.System.putFloat(cr, KEY_MIN, value) else Settings.System.putFloat(cr, KEY_MIN, 0f)
            ActionResult("Refresh rate", ActionState.APPLIED, "${if (target == RefreshTarget.HIGHEST) "Locked to" else "Capped at"} ${value.roundToInt()} Hz. Games may still request their own rate.")
        } catch (_: Exception) {
            ActionResult(
                "Refresh rate", ActionState.BLOCKED,
                "Android blocks apps from changing refresh rate on this phone unless granted via ADB. See Settings › Permissions."
            )
        }
    }

    suspend fun cleanBackground(): ActionResult = withContext(Dispatchers.IO) {
        if (!usage.hasAccess()) return@withContext ActionResult("Background apps", ActionState.BLOCKED, "Grant Usage access so REDCORE can see which apps ran recently.")
        val launcher = context.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName
        val keep = setOfNotNull(launcher, context.packageName, _session.value?.pkg)
        val candidates = usage.recentBackgroundApps(keep).map { it.pkg }.filterNot { games.isGamePackage(it) }
        candidates.forEach { runCatching { am.killBackgroundProcesses(it) } }
        if (candidates.isEmpty()) ActionResult("Background apps", ActionState.SKIPPED, "No recently used apps to close.")
        else ActionResult("Background apps", ActionState.APPLIED, "Asked Android to close ${candidates.size} cached app${if (candidates.size == 1) "" else "s"}. Android may restart services it considers important.")
    }

    fun volumePct(): Int {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return if (max > 0) audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max else 0
    }

    fun setVolumePct(pct: Int) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (pct * max / 100f).roundToInt(), 0)
    }

    // ---------------------------------------------------------------- sessions

    /** Applies [spec] and remembers the previous system state so it can be restored when the session ends. */
    suspend fun startSession(pkg: String?, mode: GameMode, spec: ModeSpec): GameSession {
        if (_session.value == null) saveRestorePoint()
        val results = mutableListOf<ActionResult>()
        results += if (spec.dnd) setDnd(true) else ActionResult("Do Not Disturb", ActionState.SKIPPED, "Not used in ${mode.title} mode.")
        results += spec.brightness?.let { setBrightness(it) } ?: ActionResult("Brightness", ActionState.SKIPPED, "Unchanged.")
        results += setRefresh(spec.refresh)
        results += if (spec.cleanBackground) cleanBackground() else ActionResult("Background apps", ActionState.SKIPPED, "Not used in ${mode.title} mode.")
        results += if (spec.keepScreenOn) {
            if (Settings.canDrawOverlays(context)) ActionResult("Keep screen awake", ActionState.APPLIED, "Screen stays on while the toolkit is active.")
            else ActionResult("Keep screen awake", ActionState.BLOCKED, "Needs \"Display over other apps\".")
        } else ActionResult("Keep screen awake", ActionState.SKIPPED, "Uses normal screen timeout.")
        val cfg = settings.current()
        if (cfg.edgeGuard) results += if (com.redcore.booster.overlay.EdgeGuard.show(context, cfg.edgeWidthDp))
            ActionResult("Edge touch guard", ActionState.APPLIED, "Palm touches on the side edges are blocked (${cfg.edgeWidthDp} dp).")
        else ActionResult("Edge touch guard", ActionState.BLOCKED, "Needs \"Display over other apps\".")
        if (cfg.triggers) results += if (Permissions.accessibilityEnabled(context))
            ActionResult("Volume-key triggers", ActionState.APPLIED, "Vol− taps L1, Vol+ taps R1.")
        else ActionResult("Volume-key triggers", ActionState.BLOCKED, "Enable the REDCORE accessibility service in Settings › Permissions.")
        val s = GameSession(pkg, pkg?.let { games.label(it) } ?: "Gaming session", mode, spec, System.currentTimeMillis(), results)
        _session.value = s
        return s
    }

    suspend fun endSession(): List<ActionResult> {
        val out = mutableListOf<ActionResult>()
        com.redcore.booster.overlay.EdgeGuard.hide(context)
        if (settings.current().restoreOnExit) out += restore()
        _session.value = null
        return out
    }

    private suspend fun saveRestorePoint() {
        val o = JSONObject()
        o.put("dnd", nm.currentInterruptionFilter)
        currentBrightnessPct()?.let { o.put("bri", it) }
        o.put("auto", isAutoBrightness())
        runCatching { Settings.System.getFloat(context.contentResolver, KEY_PEAK) }.getOrNull()?.let { o.put("peak", it.toDouble()) }
        runCatching { Settings.System.getFloat(context.contentResolver, KEY_MIN) }.getOrNull()?.let { o.put("min", it.toDouble()) }
        settings.saveSessionRestore(o.toString())
    }

    private suspend fun restore(): List<ActionResult> {
        val raw = settings.sessionRestore() ?: return emptyList()
        val o = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyList()
        val res = mutableListOf<ActionResult>()
        val spec = _session.value?.spec
        if (canSetDnd() && o.has("dnd") && (spec == null || spec.dnd)) runCatching {
            nm.setInterruptionFilter(o.getInt("dnd").coerceAtLeast(NotificationManager.INTERRUPTION_FILTER_ALL))
            res += ActionResult("Do Not Disturb", ActionState.APPLIED, "Restored.")
        }
        if (canSetBrightness() && (spec == null || spec.brightness != null)) runCatching {
            val cr = context.contentResolver
            if (o.has("bri")) Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, o.getInt("bri") * 255 / 100)
            if (o.optBoolean("auto")) Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
            res += ActionResult("Brightness", ActionState.APPLIED, "Restored.")
        }
        if (spec == null || spec.refresh != RefreshTarget.DEFAULT) runCatching {
            val cr = context.contentResolver
            if (o.has("peak")) Settings.System.putFloat(cr, KEY_PEAK, o.getDouble("peak").toFloat())
            if (o.has("min")) Settings.System.putFloat(cr, KEY_MIN, o.getDouble("min").toFloat())
        }
        settings.saveSessionRestore(null)
        return res
    }

    companion object {
        private const val KEY_PEAK = "peak_refresh_rate"
        private const val KEY_MIN = "min_refresh_rate"
    }
}
