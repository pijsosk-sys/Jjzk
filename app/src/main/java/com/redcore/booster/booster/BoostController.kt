package com.redcore.booster.booster

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.view.Display
import com.redcore.booster.data.CallMode
import com.redcore.booster.data.GameMode
import com.redcore.booster.data.GameRepository
import com.redcore.booster.data.ModeSpec
import com.redcore.booster.data.NotifMode
import com.redcore.booster.data.RefreshTarget
import com.redcore.booster.data.SettingsRepository
import com.redcore.booster.data.UsageRepository
import com.redcore.booster.overlay.EdgeGuard
import com.redcore.booster.service.BarrageBus
import com.redcore.booster.service.CallPolicy
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
    private val cr get() = context.contentResolver

    val effects = SessionEffects(context, this)

    private val _session = MutableStateFlow<GameSession?>(null)
    val session: StateFlow<GameSession?> = _session.asStateFlow()

    private val _report = MutableStateFlow<SessionReport?>(null)
    /** Summary of the last finished session, shown once in the app. */
    val lastReport: StateFlow<SessionReport?> = _report.asStateFlow()
    fun clearReport() { _report.value = null }

    /** Smooth 120 toggled live from the app or the game menu. */
    fun applySmooth120(on: Boolean): ActionResult {
        val s = _session.value ?: return ActionResult("Smooth 120", ActionState.SKIPPED, if (on) "Applies when your next game starts." else "Off.")
        return if (on) setRefresh(RefreshTarget.HIGHEST).copy(name = "Smooth 120")
        else if (s.spec.refresh == RefreshTarget.HIGHEST) ActionResult("Smooth 120", ActionState.SKIPPED, "${s.mode.title} mode keeps the highest refresh rate anyway.")
        else { setRefresh(s.spec.refresh); ActionResult("Smooth 120", ActionState.APPLIED, "Refresh rate back to the ${s.mode.title} setting.") }
    }

    // ---------------------------------------------------------------- capability probes

    private fun display(): Display? = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)

    fun supportedRefreshRates(): List<Float> =
        display()?.supportedModes.orEmpty().map { it.refreshRate }.distinctBy { it.roundToInt() }.sorted()

    fun canSetBrightness() = Settings.System.canWrite(context)
    fun canSetDnd() = nm.isNotificationPolicyAccessGranted
    fun canWriteSecure() = Permissions.has(context, "android.permission.WRITE_SECURE_SETTINGS")

    // ---------------------------------------------------------------- notifications & calls

    fun isDndOn(): Boolean = nm.currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL

    /**
     * Do Not Disturb tuned for games: alarms and **media stay audible** (so the game keeps its sound),
     * pop-ups are suppressed, and calls ring only when [calls] is ALLOW.
     */
    fun setDnd(on: Boolean, calls: CallMode = effectiveCalls()): ActionResult {
        if (!canSetDnd()) return ActionResult("Do Not Disturb", ActionState.BLOCKED, "Grant Do Not Disturb access to enable this.")
        return try {
            if (on) {
                if (Build.VERSION.SDK_INT >= 28) {
                    var cats = NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS or NotificationManager.Policy.PRIORITY_CATEGORY_MEDIA
                    if (calls == CallMode.ALLOW) cats = cats or NotificationManager.Policy.PRIORITY_CATEGORY_CALLS or NotificationManager.Policy.PRIORITY_CATEGORY_REPEAT_CALLERS
                    val suppressed = NotificationManager.Policy.SUPPRESSED_EFFECT_PEEK or NotificationManager.Policy.SUPPRESSED_EFFECT_FULL_SCREEN_INTENT or
                        NotificationManager.Policy.SUPPRESSED_EFFECT_LIGHTS or NotificationManager.Policy.SUPPRESSED_EFFECT_AMBIENT
                    nm.notificationPolicy = NotificationManager.Policy(cats, NotificationManager.Policy.PRIORITY_SENDERS_ANY, NotificationManager.Policy.PRIORITY_SENDERS_ANY, suppressed)
                }
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                ActionResult("Do Not Disturb", ActionState.APPLIED, "Pop-ups silenced; game sound and alarms still play.")
            } else {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                ActionResult("Do Not Disturb", ActionState.APPLIED, "Notifications restored.")
            }
        } catch (e: Exception) {
            ActionResult("Do Not Disturb", ActionState.BLOCKED, e.message ?: "Rejected by system.")
        }
    }

    fun effectiveCalls(): CallMode = _session.value?.spec?.calls ?: effects.cfg.callMode

    /** Live switch from the in-game menu: All alerts / Barrage / Block. */
    fun setNotifMode(m: NotifMode): ActionResult {
        BarrageBus.mode = m
        return setDnd(m != NotifMode.ALL)
    }

    private fun callsResult(calls: CallMode, dnd: Boolean): ActionResult {
        val role = Permissions.granted(context, Perm.CALL_SCREENING)
        return when (calls) {
            CallMode.ALLOW -> ActionResult("Calls", ActionState.SKIPPED, "Calls ring normally.")
            CallMode.SILENCE -> when {
                dnd && canSetDnd() -> ActionResult("Calls", ActionState.APPLIED, "Incoming calls are silenced.")
                role -> ActionResult("Calls", ActionState.APPLIED, "Incoming calls are silenced.")
                else -> ActionResult("Calls", ActionState.BLOCKED, "Needs Do Not Disturb access or the Call blocking permission.")
            }
            CallMode.DECLINE -> when {
                role -> ActionResult("Calls", ActionState.APPLIED, "Incoming calls are declined (they show as missed calls).")
                dnd && canSetDnd() -> ActionResult("Calls", ActionState.APPLIED, "Calls silenced. Grant Call blocking to decline them instead.")
                else -> ActionResult("Calls", ActionState.BLOCKED, "Grant the Call blocking permission (Game Tools).")
            }
        }
    }

    // ---------------------------------------------------------------- display

    fun currentBrightnessPct(): Int? = try {
        (Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS) * 100f / 255f).roundToInt()
    } catch (_: Exception) { null }

    fun isAutoBrightness(): Boolean = try {
        Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
    } catch (_: Exception) { false }

    /** Sets brightness 1–100 % and locks it (auto-brightness off), like a gaming phone's brightness lock. */
    fun setBrightness(pct: Int): ActionResult {
        if (!canSetBrightness()) return ActionResult("Brightness", ActionState.BLOCKED, "Grant \"Modify system settings\" to control brightness.")
        return try {
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, (pct.coerceIn(1, 100) * 255 / 100))
            ActionResult("Brightness", ActionState.APPLIED, "Locked at $pct % (auto-brightness paused).")
        } catch (e: Exception) {
            ActionResult("Brightness", ActionState.BLOCKED, e.message ?: "Rejected by system.")
        }
    }

    fun setAutoBrightness(on: Boolean): ActionResult {
        if (!canSetBrightness()) return ActionResult("Auto brightness", ActionState.BLOCKED, "Grant \"Modify system settings\" first.")
        return runCatching {
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE,
                if (on) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            ActionResult("Auto brightness", ActionState.APPLIED, if (on) "Auto brightness on" else "Brightness locked")
        }.getOrElse { ActionResult("Auto brightness", ActionState.BLOCKED, it.message ?: "Rejected by system.") }
    }

    fun isRotationLocked(): Boolean = runCatching { Settings.System.getInt(cr, Settings.System.ACCELEROMETER_ROTATION) == 0 }.getOrDefault(false)

    /** Locks the screen to its current orientation so a tilt can't flip the game. */
    fun setRotationLock(on: Boolean): ActionResult {
        if (!canSetBrightness()) return ActionResult("Rotation lock", ActionState.BLOCKED, "Grant \"Modify system settings\".")
        return runCatching {
            if (on) {
                Settings.System.putInt(cr, Settings.System.USER_ROTATION, display()?.rotation ?: 0)
                Settings.System.putInt(cr, Settings.System.ACCELEROMETER_ROTATION, 0)
            } else Settings.System.putInt(cr, Settings.System.ACCELEROMETER_ROTATION, 1)
            ActionResult("Rotation lock", ActionState.APPLIED, if (on) "Screen orientation locked." else "Auto-rotate restored.")
        }.getOrElse { ActionResult("Rotation lock", ActionState.BLOCKED, it.message ?: "Rejected by system.") }
    }

    /**
     * Refresh rate is not a public setting. We try the standard AOSP keys; most ROMs only accept them from apps
     * granted WRITE_SECURE_SETTINGS over ADB. The result tells the user exactly what happened.
     */
    fun setRefresh(target: RefreshTarget): ActionResult {
        val rates = supportedRefreshRates()
        if (target == RefreshTarget.DEFAULT) return ActionResult("Refresh rate", ActionState.SKIPPED, "Left at system default.")
        if (rates.size < 2) return ActionResult("Refresh rate", ActionState.SKIPPED, "This display reports a single refresh rate (${rates.firstOrNull()?.roundToInt() ?: "?"} Hz).")
        val value = if (target == RefreshTarget.HIGHEST) rates.last() else rates.first()
        return if (writeRefresh(value, lockMin = target == RefreshTarget.HIGHEST))
            ActionResult("Refresh rate", ActionState.APPLIED, "${if (target == RefreshTarget.HIGHEST) "Locked to" else "Capped at"} ${value.roundToInt()} Hz. Games may still request their own rate.")
        else ActionResult("Refresh rate", ActionState.BLOCKED, "Android blocks apps from changing refresh rate on this phone unless granted via ADB. See Settings › Permissions.")
    }

    private fun writeRefresh(hz: Float, lockMin: Boolean): Boolean = try {
        Settings.System.putFloat(cr, KEY_PEAK, hz)
        Settings.System.putFloat(cr, KEY_MIN, if (lockMin) hz else 0f)
        true
    } catch (_: Exception) { false }

    fun peakRefresh(): Float? = runCatching { Settings.System.getFloat(cr, KEY_PEAK) }.getOrNull()
    fun restorePeakRefresh(v: Float) { runCatching { Settings.System.putFloat(cr, KEY_PEAK, v); Settings.System.putFloat(cr, KEY_MIN, 0f) } }

    /** COOL guard helper: steps the refresh rate down. Returns the new rate, or null if not allowed. */
    fun capRefresh(hot: Boolean): Int? {
        val rates = supportedRefreshRates().map { it.roundToInt() }.distinct().sorted()
        if (rates.size < 2) return null
        val target = if (hot) rates.first() else rates.lastOrNull { it <= 90 && it < rates.last() } ?: rates[rates.size - 2]
        return if (writeRefresh(target.toFloat(), lockMin = false)) target else null
    }

    /** Cycles the peak refresh rate through the display's supported rates. Needs the ADB-granted permission on most phones. */
    fun cycleRefresh(currentHz: Int?): ActionResult {
        val rates = supportedRefreshRates().map { it.roundToInt() }.distinct().sorted()
        if (rates.size < 2) return ActionResult("Refresh rate", ActionState.SKIPPED, "This display only supports ${rates.firstOrNull() ?: "one"} Hz.")
        val next = rates.firstOrNull { it > (currentHz ?: 0) } ?: rates.first()
        return if (writeRefresh(next.toFloat(), lockMin = true)) ActionResult("Refresh rate", ActionState.APPLIED, "$next Hz")
        else ActionResult("Refresh rate", ActionState.BLOCKED, "Needs the one-time ADB grant (REDCORE › Settings › Permissions).")
    }

    private fun setFastAnimations(on: Boolean): ActionResult {
        if (!canWriteSecure()) return ActionResult("Faster animations", ActionState.BLOCKED, "Needs the one-time ADB grant.")
        return runCatching {
            val v = if (on) 0.5f else 1f
            Settings.Global.putFloat(cr, Settings.Global.ANIMATOR_DURATION_SCALE, v)
            Settings.Global.putFloat(cr, Settings.Global.TRANSITION_ANIMATION_SCALE, v)
            Settings.Global.putFloat(cr, Settings.Global.WINDOW_ANIMATION_SCALE, v)
            ActionResult("Faster animations", ActionState.APPLIED, "System animations at 0.5× for snappier menus.")
        }.getOrElse { ActionResult("Faster animations", ActionState.BLOCKED, it.message ?: "Rejected by system.") }
    }

    // ---------------------------------------------------------------- background

    private fun setSyncPaused(paused: Boolean): ActionResult = try {
        ContentResolver.setMasterSyncAutomatically(!paused)
        ActionResult("Auto-sync", ActionState.APPLIED, if (paused) "Account sync paused (Gmail, Drive, contacts…) — resumes after the game." else "Account sync restored.")
    } catch (e: Exception) {
        ActionResult("Auto-sync", ActionState.BLOCKED, e.message ?: "Rejected by system.")
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
        val cfg = settings.current()
        val calls = spec.calls ?: cfg.callMode
        CallPolicy.mode = calls
        val results = mutableListOf<ActionResult>()
        if (spec.dnd) {
            BarrageBus.mode = cfg.notifMode
            results += setDnd(true, calls)
            if (cfg.notifMode == NotifMode.BARRAGE) results += if (Permissions.granted(context, Perm.NOTIF_ACCESS))
                ActionResult("Barrage notifications", ActionState.APPLIED, "Messages scroll across the top instead of popping up.")
            else ActionResult("Barrage notifications", ActionState.BLOCKED, "Grant Notification access to see messages as a barrage.")
        } else {
            BarrageBus.mode = NotifMode.ALL
            results += ActionResult("Do Not Disturb", ActionState.SKIPPED, "Not used in ${mode.title} mode.")
        }
        results += callsResult(calls, spec.dnd)
        results += spec.brightness?.let { setBrightness(it) } ?: ActionResult("Brightness", ActionState.SKIPPED, "Unchanged.")
        results += if (cfg.smooth120) setRefresh(RefreshTarget.HIGHEST).let { r ->
            r.copy(name = "Smooth 120", detail = if (r.state == ActionState.APPLIED) r.detail.substringBefore(" Games may") + " — games that offer 90/120 FPS can use it." else r.detail)
        } else setRefresh(spec.refresh)
        results += if (spec.rotationLock) setRotationLock(true) else ActionResult("Rotation lock", ActionState.SKIPPED, "Not used.")
        results += if (spec.pauseSync) setSyncPaused(true) else ActionResult("Auto-sync", ActionState.SKIPPED, "Not used in ${mode.title} mode.")
        results += if (spec.cleanBackground) cleanBackground() else ActionResult("Background apps", ActionState.SKIPPED, "Not used in ${mode.title} mode.")
        results += if (spec.fastAnimations) setFastAnimations(true) else ActionResult("Faster animations", ActionState.SKIPPED, "Only in Limit mode.")
        results += if (spec.keepScreenOn) {
            if (Settings.canDrawOverlays(context)) ActionResult("Keep screen awake", ActionState.APPLIED, "Screen stays on while the toolkit is active.")
            else ActionResult("Keep screen awake", ActionState.BLOCKED, "Needs \"Display over other apps\".")
        } else ActionResult("Keep screen awake", ActionState.SKIPPED, "Uses normal screen timeout.")
        if (cfg.edgeGuard) results += if (EdgeGuard.show(context, cfg.edgeWidthDp))
            ActionResult("Edge touch guard", ActionState.APPLIED, "Palm touches on the side edges are blocked (${cfg.edgeWidthDp} dp).")
        else ActionResult("Edge touch guard", ActionState.BLOCKED, "Needs \"Display over other apps\".")
        if (cfg.triggers) results += if (Permissions.accessibilityEnabled(context))
            ActionResult("L1 / R1 triggers", ActionState.APPLIED, "Vol− taps L1, Vol+ taps R1.")
        else ActionResult("L1 / R1 triggers", ActionState.BLOCKED, "Enable the REDCORE accessibility service in Settings › Permissions.")
        results += withContext(Dispatchers.Main) { effects.start(pkg, spec, cfg) }

        val s = GameSession(pkg, pkg?.let { games.label(it) } ?: "Gaming session", mode, spec, _session.value?.startedAt ?: System.currentTimeMillis(), results)
        _session.value = s
        return s
    }

    suspend fun endSession(): List<ActionResult> {
        val out = mutableListOf<ActionResult>()
        val ended = _session.value
        if (ended != null) {
            val r = effects.report(ended.label, ended.mode.title)
            if (r.minutes >= 1 || r.maxTemp != null) { _report.value = r; postReport(r) }
        }
        withContext(Dispatchers.Main) { effects.stop() }
        EdgeGuard.hide(context)
        BarrageBus.mode = NotifMode.ALL
        if (ended?.pkg != null) settings.logSession(ended.pkg, ended.startedAt, System.currentTimeMillis())
        if (settings.current().restoreOnExit) out += restore()
        _session.value = null
        return out
    }

    private fun postReport(r: SessionReport) {
        val parts = listOfNotNull(
            "${r.minutes} min", r.batteryUsed?.let { "$it% battery" }, r.maxTemp?.let { "peak %.0f°C".format(it) },
            r.avgFps?.let { "$it FPS avg" }, r.avgPing?.let { "$it ms ping" }
        )
        val n = androidx.core.app.NotificationCompat.Builder(context, com.redcore.booster.RedcoreApp.CH_SESSION)
            .setSmallIcon(com.redcore.booster.R.drawable.ic_stat).setColor(0xFFFF1E3C.toInt())
            .setContentTitle("${r.label} · session report").setContentText(parts.joinToString(" · ")).setAutoCancel(true).build()
        runCatching { nm.notify(63, n) }
    }

    private suspend fun saveRestorePoint() {
        val o = JSONObject()
        o.put("dnd", nm.currentInterruptionFilter)
        if (Build.VERSION.SDK_INT >= 28 && canSetDnd()) runCatching {
            val p = nm.notificationPolicy
            o.put("pc", p.priorityCategories); o.put("pcs", p.priorityCallSenders); o.put("pms", p.priorityMessageSenders); o.put("sve", p.suppressedVisualEffects)
            if (Build.VERSION.SDK_INT >= 30) o.put("pconv", p.priorityConversationSenders)
        }
        currentBrightnessPct()?.let { o.put("bri", it) }
        o.put("auto", isAutoBrightness())
        o.put("rot", runCatching { Settings.System.getInt(cr, Settings.System.ACCELEROMETER_ROTATION) }.getOrDefault(1))
        o.put("sync", runCatching { ContentResolver.getMasterSyncAutomatically() }.getOrDefault(true))
        peakRefresh()?.let { o.put("peak", it.toDouble()) }
        runCatching { Settings.System.getFloat(cr, KEY_MIN) }.getOrNull()?.let { o.put("min", it.toDouble()) }
        runCatching { Settings.Global.getFloat(cr, Settings.Global.ANIMATOR_DURATION_SCALE) }.getOrNull()?.let { o.put("anim", it.toDouble()) }
        runCatching { Settings.Global.getFloat(cr, Settings.Global.TRANSITION_ANIMATION_SCALE) }.getOrNull()?.let { o.put("trans", it.toDouble()) }
        runCatching { Settings.Global.getFloat(cr, Settings.Global.WINDOW_ANIMATION_SCALE) }.getOrNull()?.let { o.put("win", it.toDouble()) }
        settings.saveSessionRestore(o.toString())
    }

    private suspend fun restore(): List<ActionResult> {
        val raw = settings.sessionRestore() ?: return emptyList()
        val o = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyList()
        val res = mutableListOf<ActionResult>()
        val spec = _session.value?.spec
        if (canSetDnd()) runCatching {
            if (Build.VERSION.SDK_INT >= 28 && o.has("pc")) {
                nm.notificationPolicy = if (Build.VERSION.SDK_INT >= 30 && o.has("pconv"))
                    NotificationManager.Policy(o.getInt("pc"), o.getInt("pcs"), o.getInt("pms"), o.getInt("sve"), o.getInt("pconv"))
                else NotificationManager.Policy(o.getInt("pc"), o.getInt("pcs"), o.getInt("pms"), o.getInt("sve"))
            }
            if (o.has("dnd")) nm.setInterruptionFilter(o.getInt("dnd").coerceAtLeast(NotificationManager.INTERRUPTION_FILTER_ALL))
            res += ActionResult("Do Not Disturb", ActionState.APPLIED, "Restored.")
        }
        if (canSetBrightness()) runCatching {
            if (spec == null || spec.brightness != null || effects.state.value.coolLevel != CoolLevel.OFF) {
                if (o.has("bri")) Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, o.getInt("bri") * 255 / 100)
                if (o.optBoolean("auto")) Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
                res += ActionResult("Brightness", ActionState.APPLIED, "Restored.")
            }
            if (o.has("rot")) Settings.System.putInt(cr, Settings.System.ACCELEROMETER_ROTATION, o.getInt("rot"))
        }
        runCatching { if (o.has("sync")) ContentResolver.setMasterSyncAutomatically(o.getBoolean("sync")) }
        runCatching {
            if (o.has("peak")) Settings.System.putFloat(cr, KEY_PEAK, o.getDouble("peak").toFloat())
            if (o.has("min")) Settings.System.putFloat(cr, KEY_MIN, o.getDouble("min").toFloat())
        }
        if (canWriteSecure()) runCatching {
            if (o.has("anim")) Settings.Global.putFloat(cr, Settings.Global.ANIMATOR_DURATION_SCALE, o.getDouble("anim").toFloat())
            if (o.has("trans")) Settings.Global.putFloat(cr, Settings.Global.TRANSITION_ANIMATION_SCALE, o.getDouble("trans").toFloat())
            if (o.has("win")) Settings.Global.putFloat(cr, Settings.Global.WINDOW_ANIMATION_SCALE, o.getDouble("win").toFloat())
        }
        settings.saveSessionRestore(null)
        return res
    }

    companion object {
        private const val KEY_PEAK = "peak_refresh_rate"
        private const val KEY_MIN = "min_refresh_rate"
    }
}
