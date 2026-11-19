package com.redcore.booster.booster

import android.app.NotificationManager
import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.redcore.booster.R
import com.redcore.booster.RedcoreApp
import com.redcore.booster.app
import com.redcore.booster.data.AppSettings
import com.redcore.booster.data.FpsProbe
import com.redcore.booster.data.LiveStats
import com.redcore.booster.data.ModeSpec
import com.redcore.booster.data.NotifMode
import com.redcore.booster.data.RefreshTarget
import com.redcore.booster.data.fmtTemp
import com.redcore.booster.data.fmtTempTarget
import com.redcore.booster.service.BarrageBus
import com.redcore.booster.service.CallPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
    val fpsSource: FpsProbe.Source? = null,
    /** Why there is no (game) FPS number right now, e.g. "needs the one-time ADB grant". */
    val fpsNote: String? = null,
    val sessionStart: Long = 0L,
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
 * 4D vibration and the sound EQ. Started/stopped by [BoostController].
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

    /** True while the in-game HUD or menu is on screen: FPS is then sampled every second instead of every 3 s. */
    @Volatile var overlayVisible = false

    private var spec: ModeSpec? = null
    @Volatile var cfg: AppSettings = AppSettings()
        private set

    // cool guard bookkeeping
    private var levelSince = 0L
    private var preCoolBrightness: Int? = null
    private var nextBreakAt = 0L

    // session report accumulators
    private var batPrev: Int? = null
    private var batDrain = 0
    private var batSeen = false
    private var tempSum = 0.0
    private var tempN = 0
    private var tempMax: Float? = null
    private var pingSum = 0L
    private var pingN = 0
    private var fpsSum = 0L
    private var fpsN = 0
    private var coolEvents = 0
    private val recentFps = ArrayDeque<Int>()

    private val power = context.getSystemService(PowerManager::class.java)
    private fun screenOn() = power?.isInteractive != false

    fun start(pkg: String?, spec: ModeSpec, settings: AppSettings): List<ActionResult> {
        stop()
        this.spec = spec; cfg = settings
        val sc = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope = sc
        val now = System.currentTimeMillis()
        _state.value = EffectsState(coolLevel = if (settings.coolGuard) CoolLevel.NORMAL else CoolLevel.OFF, sessionStart = now)
        batPrev = null; batDrain = 0; batSeen = false; tempSum = 0.0; tempN = 0; tempMax = null
        pingSum = 0; pingN = 0; fpsSum = 0; fpsN = 0; coolEvents = 0; recentFps.clear()
        nextBreakAt = if (settings.breakMinutes > 0) SystemClock.elapsedRealtime() + settings.breakMinutes * 60_000L else Long.MAX_VALUE
        val out = mutableListOf<ActionResult>()
        if (settings.haptics) out += haptics.start(settings.hapticStrength)
        if (settings.eq != com.redcore.booster.data.EqPreset.OFF) out += audio.apply(settings.eq)
        out += if (settings.coolGuard) ActionResult("COOL guard", ActionState.APPLIED, "Eases off above ${fmtTempTarget(settings.coolTarget, settings.fahrenheit)}.")
        else ActionResult("COOL guard", ActionState.SKIPPED, "Off — turn it on to keep the phone from overheating.")

        val app = context.app
        sc.launch { app.monitor.stats.collect { onStats(it) } }       // also keeps sampling alive during the session
        sc.launch { app.settings.settings.collect { onSettings(it) } }
        sc.launch {
            while (isActive) {
                if (cfg.showFps && screenOn()) {
                    val s = withContext(Dispatchers.IO) { FpsProbe.sample(context, pkg) }
                    val r = s.reading
                    if (r != null && r.source == FpsProbe.Source.GAME_LAYER) { fpsSum += r.value; fpsN++ }
                    _state.update { it.copy(fps = r?.value, fpsSource = r?.source, fpsNote = s.note, fpsAdvice = adviceFor(r)) }
                } else if (!cfg.showFps) _state.update { it.copy(fps = null, fpsSource = null, fpsNote = null) }
                delay(if (overlayVisible) 1_000 else 3_000)
            }
        }
        sc.launch {
            while (isActive) {
                if (cfg.showPing && screenOn()) {
                    val ms = withContext(Dispatchers.IO) { tcpPing() }
                    if (ms != null) { pingSum += ms; pingN++ }
                    _state.update { it.copy(pingMs = ms) }
                }
                delay(4_000)
            }
        }
        return out
    }

    /** Stops live effects. Settings themselves are put back by [BoostController] from its journal. */
    fun stop() {
        scope?.cancel(); scope = null
        haptics.stop(); audio.release()
        _state.value = EffectsState()
        preCoolBrightness = null
    }

    private fun onSettings(s: AppSettings) {
        val old = cfg
        cfg = s
        CallPolicy.mode = spec?.calls ?: s.callMode
        if (BarrageBus.mode != NotifMode.ALL) BarrageBus.mode = s.notifMode
        val sc = scope ?: return
        if (s.haptics != old.haptics || (s.haptics && !haptics.running)) {
            if (s.haptics) haptics.start(s.hapticStrength).let { if (it.state != ActionState.APPLIED) _events.tryEmit(it.detail) } else haptics.stop()
        }
        haptics.strength = s.hapticStrength
        if (s.eq != old.eq) audio.apply(s.eq).let { if (it.state == ActionState.BLOCKED) _events.tryEmit(it.detail) }
        if (!s.coolGuard && old.coolGuard) sc.launch { relax(); _state.update { it.copy(coolLevel = CoolLevel.OFF, coolNote = null) } }
        if (s.coolGuard && !old.coolGuard) _state.update { it.copy(coolLevel = CoolLevel.NORMAL, coolNote = "Watching temperature") }
    }

    /** Real game FPS stuck at ~60 on a 90/120 Hz screen means the game's own frame-rate setting is the limit. */
    private fun adviceFor(r: FpsProbe.Reading?): String? {
        if (r == null || r.source != FpsProbe.Source.GAME_LAYER) return null
        recentFps.addLast(r.value); while (recentFps.size > 15) recentFps.removeFirst()
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
            batteryUsed = if (batSeen) batDrain else null,
            maxTemp = tempMax, avgTemp = if (tempN > 0) (tempSum / tempN).toFloat() else null,
            coolEvents = coolEvents,
            avgPing = if (pingN > 0) (pingSum / pingN).toInt() else null,
            avgFps = if (fpsN > 0) (fpsSum / fpsN).toInt() else null,
            advice = adv,
        )
    }

    private fun onStats(s: LiveStats) {
        // Battery used = sum of drops seen while unplugged, so charging mid-session neither hides nor fakes drain.
        if (s.charging) batPrev = null
        else s.batteryPct?.let { lvl ->
            batPrev?.let { if (lvl < it) batDrain += it - lvl }
            batPrev = lvl; batSeen = true
        }
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
            levelSince = now
            _state.update { it.copy(coolLevel = next) }
            scope?.launch { applyLevel(next, t) }
        }
    }

    private suspend fun applyLevel(level: CoolLevel, temp: Float) {
        val notes = mutableListOf<String>()
        val shown = fmtTemp(temp, cfg.fahrenheit)
        when (level) {
            CoolLevel.WARM, CoolLevel.HOT -> {
                val cap = if (level == CoolLevel.HOT) 45 else 70
                val curB = boost.currentBrightnessPct()
                if (preCoolBrightness == null) preCoolBrightness = curB
                if (curB != null && curB > cap && boost.setBrightness(cap).state == ActionState.APPLIED) notes += "brightness ≤ $cap%"
                boost.capRefresh(hot = level == CoolLevel.HOT)?.let { notes += "$it Hz" }
                val r = boost.cleanBackground()
                if (r.state == ActionState.APPLIED) notes += "background apps closed"
                if (level == CoolLevel.HOT) notify(62, "Phone is hot ($shown)", "COOL guard lowered brightness and refresh rate. A short break lets it cool down fastest.")
                _events.tryEmit("❄ COOL guard: $shown — ${if (notes.isEmpty()) "easing off" else notes.joinToString(", ")}")
            }
            CoolLevel.NORMAL -> { relax(); notes += "back to full ${boost.session.value?.mode?.title ?: ""} settings"; _events.tryEmit("❄ COOL guard: temperature back to normal") }
            CoolLevel.OFF -> {}
        }
        _state.update { it.copy(coolLevel = level, coolNote = notes.joinToString(" · ").ifEmpty { null }) }
    }

    /** Undo cooling caps and return to the session's own settings. */
    private suspend fun relax() {
        val sp = spec
        val b = sp?.brightness ?: preCoolBrightness
        if (b != null && boost.canSetBrightness()) boost.setBrightness(b)
        val target = when {
            cfg.smooth120 || sp?.refresh == RefreshTarget.HIGHEST -> RefreshTarget.HIGHEST
            else -> sp?.refresh ?: RefreshTarget.DEFAULT
        }
        if (boost.canWriteSecure()) boost.setRefresh(target)
        preCoolBrightness = null
    }

    private fun notify(id: Int, title: String, text: String) {
        val n = NotificationCompat.Builder(context, RedcoreApp.CH_ALERT)
            .setSmallIcon(R.drawable.ic_stat).setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text)).setColor(0xFFFF1E3C.toInt()).setAutoCancel(true).build()
        runCatching { context.getSystemService(NotificationManager::class.java).notify(id, n) }
    }

    /** TCP handshake to a fixed IP (no DNS lookup in the measurement). */
    private fun tcpPing(): Int? = try {
        val addr = InetSocketAddress(java.net.InetAddress.getByAddress(byteArrayOf(1, 1, 1, 1)), 443)
        val t0 = SystemClock.elapsedRealtimeNanos()
        Socket().use { it.connect(addr, 1500) }
        ((SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000).toInt()
    } catch (_: Exception) { null }
}
