package com.redcore.booster.booster

import android.app.NotificationManager
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.redcore.booster.R
import com.redcore.booster.RedcoreApp
import com.redcore.booster.app
import com.redcore.booster.data.AppSettings
import com.redcore.booster.data.CallMode
import com.redcore.booster.data.FpsProbe
import com.redcore.booster.data.LiveStats
import com.redcore.booster.data.ModeSpec
import com.redcore.booster.data.NotifMode
import com.redcore.booster.service.BarrageBus
import com.redcore.booster.service.CallPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.roundToInt

enum class CoolLevel(val title: String) { OFF("Off"), NORMAL("Cool"), WARM("Warm · easing off"), HOT("Hot · cooling down") }

data class EffectsState(
    val coolLevel: CoolLevel = CoolLevel.OFF,
    val coolNote: String? = null,
    val pingMs: Int? = null,
    val fps: Int? = null,
    val fpsSource: String? = null,
    val sessionStart: Long = 0L,
    val wifiLowLatency: Boolean = false,
    /** Set when real game FPS shows a 60 FPS cap on a faster screen. */
    val fpsAdvice: String? = null,
)

/** Summary shown after a game session. Every number comes from real samples; missing data stays null. */
data class SessionReport(
    val label: String,
    val mode: String,
    val minutes: Long,
    val batteryUsed: Int?,
    val maxTemp: Float?,
    val avgTemp: Float?,
    val coolEvents: Int,
    val avgPing: Int?,
    val avgFps: Int?,
    val advice: String?,
)

/**
 * Everything that runs *during* a game session: COOL guard, ping & FPS sampling, break reminders,
 * low-latency Wi-Fi, 4D vibration and the sound EQ. Started/stopped by [BoostController].
 */
class SessionEffects(private val context: Context, private val boost: BoostController) {
    private var scope: CoroutineScope? = null
    private val _state = MutableStateFlow(EffectsState())
    val state: StateFlow<EffectsState> = _state.asStateFlow()
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    /** Short messages for the in-game overlay (shown as toasts). */
    val events: SharedFlow<String> = _events

    val haptics = Haptics4D(context)
    val audio = GameAudio()
    private var wifiLock: WifiManager.WifiLock? = null

    private var spec: ModeSpec? = null
    private var pkg: String? = null
    @Volatile var cfg: AppSettings = AppSettings()
        private set

    // cool guard bookkeeping
    private var levelSince = 0L
    private var preCoolBrightness: Int? = null
    private var preCoolPeak: Float? = null
    private var nextBreakAt = 0L

    // session report accumulators
    private var batStart: Int? = null
    private var batLast: Int? = null
    private var tempSum = 0.0
    private var tempN = 0
    private var tempMax: Float? = null
    private var pingSum = 0L
    private var pingN = 0
    private var fpsSum = 0L
    private var fpsN = 0
    private var coolEvents = 0
    private val recentFps = ArrayDeque<Int>()

    fun start(pkg: String?, spec: ModeSpec, settings: AppSettings): List<ActionResult> {
        stop()
        this.pkg = pkg; this.spec = spec; cfg = settings
        val sc = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope = sc
        val now = System.currentTimeMillis()
        _state.value = EffectsState(coolLevel = if (settings.coolGuard) CoolLevel.NORMAL else CoolLevel.OFF, sessionStart = now)
        batStart = null; batLast = null; tempSum = 0.0; tempN = 0; tempMax = null
        pingSum = 0; pingN = 0; fpsSum = 0; fpsN = 0; coolEvents = 0; recentFps.clear()
        nextBreakAt = if (settings.breakMinutes > 0) SystemClock.elapsedRealtime() + settings.breakMinutes * 60_000L else Long.MAX_VALUE
        val out = mutableListOf<ActionResult>()
        out += if (spec.lowLatencyWifi) acquireWifi() else ActionResult("Low-latency Wi-Fi", ActionState.SKIPPED, "Not used in this mode.")
        if (settings.haptics) out += haptics.start(settings.hapticStrength)
        if (settings.eq != com.redcore.booster.data.EqPreset.OFF) out += audio.apply(settings.eq)
        out += if (settings.coolGuard) ActionResult("COOL guard", ActionState.APPLIED, "Eases off above ${settings.coolTarget}°C.")
        else ActionResult("COOL guard", ActionState.SKIPPED, "Off — turn it on to keep the phone from overheating.")

        val app = context.app
        sc.launch { app.monitor.stats.collect { onStats(it) } }       // also keeps sampling alive during the session
        sc.launch { app.settings.settings.collect { onSettings(it) } }
        sc.launch {
            while (isActive) {
                if (cfg.showFps) {
                    val r = withContext(Dispatchers.IO) { FpsProbe.read(context, pkg) }
                    if (r != null && r.source == "game") { fpsSum += r.fps; fpsN++ }
                    _state.update { it.copy(fps = r?.fps, fpsSource = r?.source, fpsAdvice = r?.let { adviceFor(it) }) }
                }
                delay(1000)
            }
        }
        sc.launch {
            while (isActive) {
                if (cfg.showPing) {
                    val ms = withContext(Dispatchers.IO) { tcpPing() }
                    if (ms != null) { pingSum += ms; pingN++ }
                    _state.update { it.copy(pingMs = ms) }
                }
                delay(4000)
            }
        }
        return out
    }

    fun stop() {
        scope?.cancel(); scope = null
        haptics.stop(); audio.release()
        runCatching { wifiLock?.takeIf { it.isHeld }?.release() }; wifiLock = null
        if (_state.value.coolLevel == CoolLevel.WARM || _state.value.coolLevel == CoolLevel.HOT) relax()
        _state.value = EffectsState()
        preCoolBrightness = null; preCoolPeak = null
    }

    private fun acquireWifi(): ActionResult = try {
        val wm = context.applicationContext.getSystemService(WifiManager::class.java)
        @Suppress("DEPRECATION")
        val mode = if (Build.VERSION.SDK_INT >= 29) WifiManager.WIFI_MODE_FULL_LOW_LATENCY else WifiManager.WIFI_MODE_FULL_HIGH_PERF
        val l = wm.createWifiLock(mode, "redcore:game").apply { setReferenceCounted(false); acquire() }
        wifiLock = l
        _state.update { it.copy(wifiLowLatency = true) }
        ActionResult("Low-latency Wi-Fi", ActionState.APPLIED, "Requested from Android (applies while the screen is on, on phones that support it).")
    } catch (t: Throwable) {
        ActionResult("Low-latency Wi-Fi", ActionState.BLOCKED, t.message ?: "Not supported.")
    }

    private fun onSettings(s: AppSettings) {
        val old = cfg
        cfg = s
        CallPolicy.mode = spec?.calls ?: s.callMode
        if (BarrageBus.mode != NotifMode.ALL) BarrageBus.mode = s.notifMode
        if (scope == null) return
        if (s.haptics != old.haptics || (s.haptics && !haptics.running)) {
            if (s.haptics) haptics.start(s.hapticStrength).let { if (it.state != ActionState.APPLIED) _events.tryEmit(it.detail) } else haptics.stop()
        }
        haptics.strength = s.hapticStrength
        if (s.eq != old.eq) audio.apply(s.eq).let { if (it.state == ActionState.BLOCKED) _events.tryEmit(it.detail) }
        if (!s.coolGuard && old.coolGuard) { relax(); _state.update { it.copy(coolLevel = CoolLevel.OFF, coolNote = null) } }
        if (s.coolGuard && !old.coolGuard) _state.update { it.copy(coolLevel = CoolLevel.NORMAL, coolNote = "Watching temperature") }
    }

    /** Real game FPS stuck at ~60 on a 90/120 Hz screen means the game's own frame-rate setting is the limit. */
    private fun adviceFor(r: FpsProbe.Reading): String? {
        if (r.source != "game") return null
        recentFps.addLast(r.fps); while (recentFps.size > 15) recentFps.removeFirst()
        if (recentFps.size < 10) return _state.value.fpsAdvice
        val median = recentFps.sorted()[recentFps.size / 2]
        val maxHz = boost.supportedRefreshRates().maxOrNull()?.roundToInt() ?: 60
        return if (median in 54..62 && maxHz >= 90)
            "Game is running at 60 FPS on a $maxHz Hz screen — set its Frame rate option to 90/120 if it has one"
        else null
    }

    fun report(label: String, mode: String): SessionReport {
        val start = _state.value.sessionStart
        val adv = _state.value.fpsAdvice
        return SessionReport(
            label, mode,
            minutes = if (start > 0) (System.currentTimeMillis() - start) / 60_000 else 0,
            batteryUsed = batStart?.let { b -> batLast?.let { (b - it).takeIf { d -> d >= 0 } } },
            maxTemp = tempMax, avgTemp = if (tempN > 0) (tempSum / tempN).toFloat() else null,
            coolEvents = coolEvents,
            avgPing = if (pingN > 0) (pingSum / pingN).toInt() else null,
            avgFps = if (fpsN > 0) (fpsSum / fpsN).toInt() else null,
            advice = adv,
        )
    }

    private fun onStats(s: LiveStats) {
        s.batteryPct?.let { if (batStart == null && !s.charging) batStart = it; if (!s.charging) batLast = it }
        s.batteryTempC?.let { tempSum += it; tempN++; tempMax = maxOf(tempMax ?: it, it) }
        // Break reminder
        if (SystemClock.elapsedRealtime() >= nextBreakAt) {
            val mins = cfg.breakMinutes
            nextBreakAt = SystemClock.elapsedRealtime() + mins * 60_000L
            notify(61, "Time for a short break", "You've been playing for a while. Rest your eyes and hands for a minute.")
            _events.tryEmit("⏱ Break reminder: you've played $mins more minutes")
        }
        if (!cfg.coolGuard) return
        val t = s.batteryTempC ?: return
        val thermal = s.thermalStatus ?: 0
        val target = cfg.coolTarget
        val cur = _state.value.coolLevel
        val want = when {
            t >= target + 3 || thermal >= 3 -> CoolLevel.HOT
            t >= target || thermal >= 2 -> CoolLevel.WARM
            else -> CoolLevel.NORMAL
        }
        val now = SystemClock.elapsedRealtime()
        val next = when {
            want.ordinal > cur.ordinal -> want                                    // heat up immediately
            want.ordinal < cur.ordinal && now - levelSince > 45_000 &&
                t <= (if (cur == CoolLevel.HOT) target + 1.5f else target - 1.5f) && thermal < 2 -> CoolLevel.entries[cur.ordinal - 1]
            else -> cur
        }
        if (next != cur) {
            if (next.ordinal > cur.ordinal && next != CoolLevel.NORMAL) coolEvents++
            levelSince = now; applyLevel(next, t)
        }
    }

    private fun applyLevel(level: CoolLevel, temp: Float) {
        val notes = mutableListOf<String>()
        when (level) {
            CoolLevel.WARM, CoolLevel.HOT -> {
                val cap = if (level == CoolLevel.HOT) 45 else 70
                val curB = boost.currentBrightnessPct()
                if (preCoolBrightness == null) preCoolBrightness = curB
                if (curB != null && curB > cap && boost.setBrightness(cap).state == ActionState.APPLIED) notes += "brightness ≤ $cap%"
                if (preCoolPeak == null) preCoolPeak = boost.peakRefresh()
                boost.capRefresh(hot = level == CoolLevel.HOT)?.let { notes += "$it Hz" }
                scope?.launch {
                    val r = boost.cleanBackground()
                    if (r.state == ActionState.APPLIED) _events.tryEmit("❄ COOL guard closed background apps")
                }
                if (level == CoolLevel.HOT) notify(62, "Phone is hot (${temp.roundToInt()}°C)", "COOL guard lowered brightness and refresh rate. A short break lets it cool down fastest.")
                _events.tryEmit("❄ COOL guard: ${temp.roundToInt()}°C — ${if (notes.isEmpty()) "easing off" else notes.joinToString(", ")}")
            }
            CoolLevel.NORMAL -> { relax(); notes += "back to full ${boost.session.value?.mode?.title ?: ""} settings" ; _events.tryEmit("❄ COOL guard: temperature back to normal") }
            CoolLevel.OFF -> {}
        }
        _state.update { it.copy(coolLevel = level, coolNote = notes.joinToString(" · ").ifEmpty { null }) }
    }

    /** Undo cooling caps and return to the session's own settings. */
    private fun relax() {
        val sp = spec
        val b = sp?.brightness ?: preCoolBrightness
        if (b != null) boost.setBrightness(b)
        if (sp?.refresh == com.redcore.booster.data.RefreshTarget.HIGHEST || cfg.smooth120) boost.setRefresh(com.redcore.booster.data.RefreshTarget.HIGHEST)
        else preCoolPeak?.let { boost.restorePeakRefresh(it) }
        preCoolBrightness = null; preCoolPeak = null
    }

    private fun notify(id: Int, title: String, text: String) {
        val n = NotificationCompat.Builder(context, RedcoreApp.CH_ALERT)
            .setSmallIcon(R.drawable.ic_stat).setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text)).setColor(0xFFFF1E3C.toInt()).setAutoCancel(true).build()
        runCatching { context.getSystemService(NotificationManager::class.java).notify(id, n) }
    }

    private fun tcpPing(): Int? = try {
        val t0 = SystemClock.elapsedRealtimeNanos()
        Socket().use { it.connect(InetSocketAddress("1.1.1.1", 443), 1500) }
        ((SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000).toInt()
    } catch (_: Exception) { null }

}
