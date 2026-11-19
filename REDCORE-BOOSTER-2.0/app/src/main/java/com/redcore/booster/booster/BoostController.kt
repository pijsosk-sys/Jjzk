package com.redcore.booster.booster

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.view.Display
import com.redcore.booster.core.EndReason
import com.redcore.booster.core.JournalMeta
import com.redcore.booster.core.RestoreAction
import com.redcore.booster.core.RestoreJournal
import com.redcore.booster.core.Tweak
import com.redcore.booster.data.CallMode
import com.redcore.booster.data.GameMode
import com.redcore.booster.data.GameRepository
import com.redcore.booster.data.ModeSpec
import com.redcore.booster.data.NotifMode
import com.redcore.booster.data.RefreshTarget
import com.redcore.booster.data.SettingsRepository
import com.redcore.booster.data.UsageRepository
import com.redcore.booster.data.fmtBytes
import com.redcore.booster.data.fmtTemp
import com.redcore.booster.overlay.EdgeGuard
import com.redcore.booster.service.BarrageBus
import com.redcore.booster.service.CallPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

enum class ActionState { APPLIED, SKIPPED, BLOCKED }

/** Outcome of a single booster action. Shown to the user verbatim — nothing is claimed that did not happen. */
data class ActionResult(val name: String, val state: ActionState, val detail: String, val verified: Boolean = false)

data class GameSession(
    val pkg: String?,
    val label: String,
    val mode: GameMode,
    val spec: ModeSpec,
    val startedAt: Long,
    val results: List<ActionResult>,
) {
    val appliedCount get() = results.count { it.state == ActionState.APPLIED }
}

/** A setting REDCORE changed but could not put back yet (usually because a permission was revoked). */
data class PendingRestore(val tweak: Tweak, val requirement: String)

/** What restoring after an interrupted session (crash, Android kill, reboot) did. */
data class RecoveryReport(val label: String, val results: List<ActionResult>)

/** One live effect, re-read from the system so the UI shows what is really in force right now. */
data class LiveEffect(val name: String, val value: String, val holding: Boolean)

/** Result of writing one setting. */
private sealed interface Change {
    data class Done(val before: String?, val after: String?) : Change
    data class Same(val value: String?) : Change
    data class Failed(val reason: String) : Change
}

class BoostController(
    private val context: Context,
    private val settings: SettingsRepository,
    private val usage: UsageRepository,
    private val games: GameRepository,
    private val backend: SettingsBackend = AndroidSettingsBackend(context),
    private val store: JournalStore = JournalStore(context),
) {
    private val nm = context.getSystemService(NotificationManager::class.java)
    private val am = context.getSystemService(ActivityManager::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)

    val effects = SessionEffects(context, this)

    private val sessionMutex = Mutex()
    private val journalLock = Any()
    private var journal: RestoreJournal = store.load()

    private val _session = MutableStateFlow<GameSession?>(null)
    val session: StateFlow<GameSession?> = _session.asStateFlow()

    private val _report = MutableStateFlow<SessionReport?>(null)
    /** Summary of the last finished session, shown once in the app. */
    val lastReport: StateFlow<SessionReport?> = _report.asStateFlow()
    fun clearReport() { _report.value = null }

    private val _pending = MutableStateFlow(pendingFrom(journal, sessionActive = false))
    /** Settings that still need restoring (shown as a banner with the permission to grant). */
    val pendingRestore: StateFlow<List<PendingRestore>> = _pending.asStateFlow()

    private val _recovery = MutableStateFlow<RecoveryReport?>(null)
    val recovery: StateFlow<RecoveryReport?> = _recovery.asStateFlow()
    fun clearRecovery() { _recovery.value = null }

    /** Screen hang-up: brightness REDCORE lowered and the values to return to. */
    private var hangupPrev: Pair<String?, String?>? = null
    private val _hangup = MutableStateFlow(false)
    val hangupActive: StateFlow<Boolean> = _hangup.asStateFlow()

    // ------------------------------------------------------------------ capability probes

    private fun display(): Display? = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)

    fun supportedRefreshRates(): List<Float> =
        display()?.supportedModes.orEmpty().map { it.refreshRate }.distinctBy { it.roundToInt() }.sorted()

    fun currentRefreshHz(): Int? = display()?.refreshRate?.roundToInt()

    fun canSetBrightness() = Settings.System.canWrite(context)
    fun canSetDnd() = nm.isNotificationPolicyAccessGranted
    fun canWriteSecure() = Permissions.has(context, AndroidSettingsBackend.WRITE_SECURE)

    /** Samsung One UI exposes its own "Motion smoothness" key in addition to the AOSP refresh keys. */
    fun hasSamsungRefreshMode(): Boolean =
        Build.MANUFACTURER.equals("samsung", true) && backend.read(Tweak.SAMSUNG_REFRESH_MODE) != null

    private fun journaling() = _session.value != null || _hangup.value

    // ------------------------------------------------------------------ journaled writes

    /**
     * Writes one setting the safe way: skip if it already has the value; record the original in the journal
     * *before* writing (when [journaled]); read it back to verify; undo the journal entry if nothing changed.
     */
    private fun change(t: Tweak, target: String?, journaled: Boolean = journaling()): Change {
        val before = backend.read(t)
        if (RestoreJournal.same(t, before, target)) return Change.Same(before)
        if (!backend.canWrite(t)) return Change.Failed("Needs ${backend.requirement(t)}.")
        var previous: com.redcore.booster.core.JournalEntry? = null
        if (journaled) synchronized(journalLock) {
            previous = journal.entries[t]
            val next = journal.noteOriginal(t, before).noteApplied(t, target)
            if (!store.save(next)) return Change.Failed("Couldn't save a restore point, so nothing was changed.")
            journal = next
        }
        val error = runCatching { backend.write(t, target) }.exceptionOrNull()
        val after = backend.read(t)
        val changed = !RestoreJournal.same(t, after, before)
        if (journaled) synchronized(journalLock) {
            journal = when {
                changed -> journal.noteApplied(t, after)
                previous != null -> journal.copy(entries = journal.entries + (t to previous!!))
                else -> journal.remove(t)
            }
            store.save(journal)
        }
        return when {
            !changed -> Change.Failed(error?.message?.let { "Android rejected it ($it)." } ?: "Android didn't accept the change.")
            else -> Change.Done(before, after)
        }
    }

    /** Puts specific settings back to their journaled originals right away (e.g. a mode switch no longer uses them). */
    private fun release(tweaks: Collection<Tweak>, keepUserChanges: Boolean = true): List<ActionResult> {
        val j = synchronized(journalLock) { journal }
        val relevant = AndroidSettingsBackend.restoreOrder.filter { it in tweaks && j.entries.containsKey(it) }
        if (relevant.isEmpty()) return emptyList()
        return restoreSteps(relevant, keepUserChanges)
    }

    private fun restoreSteps(order: List<Tweak>, keepUserChanges: Boolean): List<ActionResult> {
        val out = mutableListOf<ActionResult>()
        val j0 = synchronized(journalLock) { journal }
        val current = order.associateWith { backend.read(it) }
        val plan = RestoreJournal(j0.entries.filterKeys { it in order }).planRestore(current, keepUserChanges).associateBy { it.tweak }
        for (t in order) {
            val step = plan[t] ?: continue
            when (step.action) {
                RestoreAction.ALREADY_ORIGINAL -> dropEntry(t)
                RestoreAction.KEEP_USER_CHANGE -> {
                    dropEntry(t)
                    out += ActionResult(t.label, ActionState.SKIPPED, "Kept your change (${describe(t, step.current)}): you changed it during the game.")
                }
                RestoreAction.RESTORE -> {
                    if (!backend.canWrite(t)) {
                        synchronized(journalLock) { journal = journal.noteFailure(t); store.save(journal) }
                        out += ActionResult(t.label, ActionState.BLOCKED, "Not restored yet — needs ${backend.requirement(t)}. REDCORE will retry once it's granted.")
                        continue
                    }
                    val err = runCatching { backend.write(t, step.target) }.exceptionOrNull()
                    val now = backend.read(t)
                    if (RestoreJournal.same(t, now, step.target)) {
                        dropEntry(t)
                        out += ActionResult(t.label, ActionState.APPLIED, "Restored to ${describe(t, step.target)}.", verified = true)
                    } else {
                        synchronized(journalLock) { journal = journal.noteFailure(t); store.save(journal) }
                        out += ActionResult(t.label, ActionState.BLOCKED, "Couldn't restore${err?.message?.let { " ($it)" } ?: ""}. REDCORE will try again.")
                    }
                }
            }
        }
        refreshPending()
        return out
    }

    private fun dropEntry(t: Tweak) = synchronized(journalLock) {
        journal = journal.remove(t)
        if (journal.entries.isEmpty()) journal = RestoreJournal()
        store.save(journal)
    }

    private fun refreshPending() {
        _pending.value = pendingFrom(synchronized(journalLock) { journal }, _session.value != null || _hangup.value)
    }

    private fun pendingFrom(j: RestoreJournal, sessionActive: Boolean): List<PendingRestore> =
        if (sessionActive) emptyList() else j.entries.values.filter { it.failures > 0 }.map { PendingRestore(it.tweak, backend.requirement(it.tweak)) }

    /** Human-readable value of a setting for result messages. */
    private fun describe(t: Tweak, v: String?): String = when (t) {
        Tweak.BRIGHTNESS -> v?.toIntOrNull()?.let { "${rawToPct(it)} %" } ?: "default"
        Tweak.BRIGHTNESS_MODE -> if (v == "1") "auto" else "manual"
        Tweak.DND_FILTER -> when (v?.toIntOrNull()) { 1 -> "off"; 2 -> "priority only"; 3 -> "total silence"; 4 -> "alarms only"; else -> "unknown" }
        Tweak.DND_POLICY -> "your previous rules"
        Tweak.AUTO_ROTATE -> if (v == "1") "on" else "off"
        Tweak.USER_ROTATION -> when (v) { "0" -> "portrait"; "1" -> "landscape"; "2" -> "upside-down"; "3" -> "reverse landscape"; else -> "default" }
        Tweak.PEAK_REFRESH, Tweak.MIN_REFRESH -> v?.toFloatOrNull()?.let { if (it <= 0f) "no limit" else "${it.roundToInt()} Hz" } ?: "system default"
        Tweak.SAMSUNG_REFRESH_MODE -> when (v) { "0" -> "Standard"; "1" -> "Adaptive"; "2" -> "High"; else -> "default" }
        Tweak.MASTER_SYNC -> if (v == "false") "paused" else "on"
        Tweak.ANIM_ANIMATOR, Tweak.ANIM_TRANSITION, Tweak.ANIM_WINDOW -> v?.toFloatOrNull()?.let { "${it}×" } ?: "1.0× (default)"
    }

    private fun rawToPct(raw: Int) = (raw * 100f / 255f).roundToInt().coerceIn(0, 100)
    private fun pctToRaw(pct: Int) = (pct.coerceIn(1, 100) * 255f / 100f).roundToInt().coerceIn(1, 255)

    // ------------------------------------------------------------------ notifications & calls

    fun isDndOn(): Boolean = nm.currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL

    /**
     * Do Not Disturb tuned for games: alarms and **media stay audible** (so the game keeps its sound),
     * pop-ups are suppressed, and calls ring only when [calls] is ALLOW.
     */
    suspend fun setDnd(on: Boolean, calls: CallMode = effectiveCalls()): ActionResult = withContext(Dispatchers.IO) {
        if (!canSetDnd()) return@withContext ActionResult("Do Not Disturb", ActionState.BLOCKED, "Grant Do Not Disturb access to enable this.")
        if (on) {
            if (Build.VERSION.SDK_INT >= 28) {
                var cats = NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS or NotificationManager.Policy.PRIORITY_CATEGORY_MEDIA
                if (calls == CallMode.ALLOW) cats = cats or NotificationManager.Policy.PRIORITY_CATEGORY_CALLS or NotificationManager.Policy.PRIORITY_CATEGORY_REPEAT_CALLERS
                val suppressed = NotificationManager.Policy.SUPPRESSED_EFFECT_PEEK or NotificationManager.Policy.SUPPRESSED_EFFECT_FULL_SCREEN_INTENT or
                    NotificationManager.Policy.SUPPRESSED_EFFECT_LIGHTS or NotificationManager.Policy.SUPPRESSED_EFFECT_AMBIENT
                val policy = NotificationManager.Policy(cats, NotificationManager.Policy.PRIORITY_SENDERS_ANY, NotificationManager.Policy.PRIORITY_SENDERS_ANY, suppressed)
                change(Tweak.DND_POLICY, AndroidSettingsBackend.policyToString(policy))
            }
            when (val c = change(Tweak.DND_FILTER, NotificationManager.INTERRUPTION_FILTER_PRIORITY.toString())) {
                is Change.Done -> ActionResult("Do Not Disturb", ActionState.APPLIED, "Off → on. Pop-ups silenced; game sound and alarms still play.", verified = true)
                is Change.Same -> ActionResult("Do Not Disturb", ActionState.APPLIED, "Already on — pop-ups silenced; game sound and alarms still play.", verified = true)
                is Change.Failed -> ActionResult("Do Not Disturb", ActionState.BLOCKED, c.reason)
            }
        } else {
            when (val c = change(Tweak.DND_FILTER, NotificationManager.INTERRUPTION_FILTER_ALL.toString())) {
                is Change.Failed -> ActionResult("Do Not Disturb", ActionState.BLOCKED, c.reason)
                else -> ActionResult("Do Not Disturb", ActionState.APPLIED, "Notifications back on.", verified = true)
            }
        }
    }

    fun effectiveCalls(): CallMode = _session.value?.spec?.calls ?: effects.cfg.callMode

    /** Live switch from the in-game menu: All alerts / Barrage / Block. */
    suspend fun setNotifMode(m: NotifMode): ActionResult {
        BarrageBus.mode = m
        return setDnd(m != NotifMode.ALL)
    }

    private fun callsResult(calls: CallMode, dnd: Boolean): ActionResult {
        val role = Permissions.granted(context, Perm.CALL_SCREENING)
        return when (calls) {
            CallMode.ALLOW -> ActionResult("Calls", ActionState.SKIPPED, "Calls ring normally.")
            CallMode.SILENCE -> when {
                dnd && canSetDnd() -> ActionResult("Calls", ActionState.APPLIED, "Incoming calls are silenced by Do Not Disturb.")
                role -> ActionResult("Calls", ActionState.APPLIED, "Incoming calls are silenced by call screening.")
                else -> ActionResult("Calls", ActionState.BLOCKED, "Needs Do Not Disturb access or the Call blocking permission.")
            }
            CallMode.DECLINE -> when {
                role -> ActionResult("Calls", ActionState.APPLIED, "Incoming calls are declined (they show as missed calls).")
                dnd && canSetDnd() -> ActionResult("Calls", ActionState.APPLIED, "Calls silenced. Grant Call blocking to decline them instead.")
                else -> ActionResult("Calls", ActionState.BLOCKED, "Grant the Call blocking permission (Game Tools).")
            }
        }
    }

    // ------------------------------------------------------------------ display

    fun currentBrightnessPct(): Int? = backend.read(Tweak.BRIGHTNESS)?.toIntOrNull()?.let { rawToPct(it) }

    fun isAutoBrightness(): Boolean = backend.read(Tweak.BRIGHTNESS_MODE) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC.toString()

    /** Sets brightness 1–100 % and locks it (auto-brightness off), like a gaming phone's brightness lock. */
    suspend fun setBrightness(pct: Int): ActionResult = withContext(Dispatchers.IO) { setBrightnessNow(pct) }

    private fun setBrightnessNow(pct: Int): ActionResult {
        if (!canSetBrightness()) return ActionResult("Brightness", ActionState.BLOCKED, "Grant \"Modify system settings\" to control brightness.")
        val beforePct = currentBrightnessPct()
        val wasAuto = isAutoBrightness()
        val mode = change(Tweak.BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL.toString())
        if (mode is Change.Failed) return ActionResult("Brightness", ActionState.BLOCKED, mode.reason)
        return when (val c = change(Tweak.BRIGHTNESS, pctToRaw(pct).toString())) {
            is Change.Failed -> ActionResult("Brightness", ActionState.BLOCKED, c.reason)
            else -> ActionResult(
                "Brightness", ActionState.APPLIED,
                "${beforePct?.let { "$it %" } ?: "—"}${if (wasAuto) " (auto)" else ""} → ${currentBrightnessPct() ?: pct} % locked.", verified = true
            )
        }
    }

    suspend fun setAutoBrightness(on: Boolean): ActionResult = withContext(Dispatchers.IO) {
        if (!canSetBrightness()) return@withContext ActionResult("Auto brightness", ActionState.BLOCKED, "Grant \"Modify system settings\" first.")
        val target = if (on) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        when (val c = change(Tweak.BRIGHTNESS_MODE, target.toString())) {
            is Change.Failed -> ActionResult("Auto brightness", ActionState.BLOCKED, c.reason)
            else -> ActionResult("Auto brightness", ActionState.APPLIED, if (on) "Auto brightness on" else "Brightness locked", verified = true)
        }
    }

    fun isRotationLocked(): Boolean = backend.read(Tweak.AUTO_ROTATE) == "0"

    /** Locks the screen to its current orientation so a tilt can't flip the game. */
    suspend fun setRotationLock(on: Boolean): ActionResult = withContext(Dispatchers.IO) { setRotationLockNow(on) }

    private fun setRotationLockNow(on: Boolean): ActionResult {
        if (!canSetBrightness()) return ActionResult("Rotation lock", ActionState.BLOCKED, "Grant \"Modify system settings\".")
        return if (on) {
            val rot = display()?.rotation ?: 0
            change(Tweak.USER_ROTATION, rot.toString())
            when (val c = change(Tweak.AUTO_ROTATE, "0")) {
                is Change.Failed -> ActionResult("Rotation lock", ActionState.BLOCKED, c.reason)
                is Change.Same -> ActionResult("Rotation lock", ActionState.APPLIED, "Already locked (${describe(Tweak.USER_ROTATION, rot.toString())}).", verified = true)
                is Change.Done -> ActionResult("Rotation lock", ActionState.APPLIED, "Auto-rotate on → locked to ${describe(Tweak.USER_ROTATION, rot.toString())}.", verified = true)
            }
        } else when (val c = change(Tweak.AUTO_ROTATE, "1")) {
            is Change.Failed -> ActionResult("Rotation lock", ActionState.BLOCKED, c.reason)
            else -> ActionResult("Rotation lock", ActionState.APPLIED, "Auto-rotate on.", verified = true)
        }
    }

    // ------------------------------------------------------------------ refresh rate (AOSP keys + Samsung One UI)

    private val refreshTweaks = listOf(Tweak.MIN_REFRESH, Tweak.PEAK_REFRESH, Tweak.SAMSUNG_REFRESH_MODE)

    /**
     * Refresh rate is not a public setting. REDCORE writes the AOSP keys (Pixel, Motorola, Xiaomi, OnePlus…)
     * and, on Samsung, One UI's "Motion smoothness". All of these need the one-time ADB grant. The result
     * says what the display actually runs at afterwards.
     */
    suspend fun setRefresh(target: RefreshTarget, name: String = "Refresh rate"): ActionResult = withContext(Dispatchers.IO) { setRefreshNow(target, name) }

    private suspend fun setRefreshNow(target: RefreshTarget, name: String): ActionResult {
        val rates = supportedRefreshRates()
        val beforeHz = currentRefreshHz()
        if (target == RefreshTarget.DEFAULT) {
            val r = release(refreshTweaks)
            return ActionResult(name, if (r.any { it.state == ActionState.APPLIED }) ActionState.APPLIED else ActionState.SKIPPED,
                if (r.isEmpty()) "Left at the system default." else "Back to the system default.")
        }
        if (rates.size < 2) return ActionResult(name, ActionState.SKIPPED, "This display has a single refresh rate (${rates.firstOrNull()?.roundToInt() ?: beforeHz ?: "?"} Hz).")
        if (!canWriteSecure()) return ActionResult(name, ActionState.BLOCKED, "Android only lets apps change the refresh rate after a one-time ADB grant. See Settings › Permissions.")
        val hz = if (target == RefreshTarget.HIGHEST) rates.last() else rates.first()
        val outcomes = mutableListOf<Change>()
        outcomes += change(Tweak.MIN_REFRESH, hz.toString())
        outcomes += change(Tweak.PEAK_REFRESH, hz.toString())
        if (hasSamsungRefreshMode()) outcomes += change(Tweak.SAMSUNG_REFRESH_MODE, if (target == RefreshTarget.HIGHEST) "1" else "0")
        if (outcomes.all { it is Change.Failed }) return ActionResult(name, ActionState.BLOCKED, (outcomes.first() as Change.Failed).reason)
        delay(350) // let the display mode settle before reading it back
        val nowHz = currentRefreshHz()
        val word = if (target == RefreshTarget.HIGHEST) "Locked to" else "Capped at"
        val seen = when {
            nowHz == null -> ""
            nowHz == hz.roundToInt() -> " Display confirms $nowHz Hz."
            else -> " Display is at $nowHz Hz right now — some games and screens pick their own rate."
        }
        return ActionResult(name, ActionState.APPLIED, "${beforeHz?.let { "$it Hz → " } ?: ""}$word ${hz.roundToInt()} Hz.$seen", verified = nowHz == hz.roundToInt())
    }

    fun peakRefresh(): Float? = backend.read(Tweak.PEAK_REFRESH)?.toFloatOrNull()

    /** COOL guard: steps the refresh rate down. Returns the new cap, or null if not allowed. */
    suspend fun capRefresh(hot: Boolean): Int? = withContext(Dispatchers.IO) {
        val rates = supportedRefreshRates().map { it.roundToInt() }.distinct().sorted()
        if (rates.size < 2 || !canWriteSecure()) return@withContext null
        val target = if (hot) rates.first() else rates.lastOrNull { it <= 90 && it < rates.last() } ?: rates[rates.size - 2]
        change(Tweak.MIN_REFRESH, "0")
        val ok = change(Tweak.PEAK_REFRESH, target.toFloat().toString()) !is Change.Failed
        if (hot && hasSamsungRefreshMode()) change(Tweak.SAMSUNG_REFRESH_MODE, "0")
        if (ok) target else null
    }

    /** Cycles the refresh rate through the display's supported rates (in-game menu tile). */
    suspend fun cycleRefresh(currentHz: Int?): ActionResult = withContext(Dispatchers.IO) {
        val rates = supportedRefreshRates().map { it.roundToInt() }.distinct().sorted()
        if (rates.size < 2) return@withContext ActionResult("Refresh rate", ActionState.SKIPPED, "This display only supports ${rates.firstOrNull() ?: "one"} Hz.")
        if (!canWriteSecure()) return@withContext ActionResult("Refresh rate", ActionState.BLOCKED, "Needs the one-time ADB grant (REDCORE › Settings › Permissions).")
        val next = rates.firstOrNull { it > (currentHz ?: 0) } ?: rates.first()
        change(Tweak.MIN_REFRESH, next.toFloat().toString())
        val ok = change(Tweak.PEAK_REFRESH, next.toFloat().toString()) !is Change.Failed
        if (hasSamsungRefreshMode()) change(Tweak.SAMSUNG_REFRESH_MODE, if (next == rates.last()) "1" else "0")
        delay(300)
        if (ok) ActionResult("Refresh rate", ActionState.APPLIED, "$next Hz requested · display at ${currentRefreshHz() ?: "?"} Hz", verified = currentRefreshHz() == next)
        else ActionResult("Refresh rate", ActionState.BLOCKED, "Android rejected the change.")
    }

    /** Smooth 120 toggled live from the app or the game menu. */
    suspend fun applySmooth120(on: Boolean): ActionResult {
        val s = _session.value ?: return ActionResult("Smooth 120", ActionState.SKIPPED, if (on) "Applies when your next game starts." else "Off.")
        return if (on) setRefresh(RefreshTarget.HIGHEST, "Smooth 120")
        else if (s.spec.refresh == RefreshTarget.HIGHEST) ActionResult("Smooth 120", ActionState.SKIPPED, "${s.mode.title} mode keeps the highest refresh rate anyway.")
        else setRefresh(s.spec.refresh, "Smooth 120").let { it.copy(detail = "Off · " + it.detail) }
    }

    private fun setFastAnimations(): ActionResult {
        if (!canWriteSecure()) return ActionResult("Faster animations", ActionState.BLOCKED, "Needs the one-time ADB grant (Settings › Permissions).")
        val before = backend.read(Tweak.ANIM_ANIMATOR)?.toFloatOrNull() ?: 1f
        val r = listOf(Tweak.ANIM_ANIMATOR, Tweak.ANIM_TRANSITION, Tweak.ANIM_WINDOW).map { change(it, "0.5") }
        return if (r.all { it is Change.Failed }) ActionResult("Faster animations", ActionState.BLOCKED, (r.first() as Change.Failed).reason)
        else ActionResult("Faster animations", ActionState.APPLIED, "${before}× → 0.5× system animations.", verified = true)
    }

    // ------------------------------------------------------------------ background

    private fun setSyncPaused(): ActionResult = when (val c = change(Tweak.MASTER_SYNC, "false")) {
        is Change.Done -> ActionResult("Auto-sync", ActionState.APPLIED, "On → paused (Gmail, Drive, contacts…). Resumes after the game.", verified = true)
        is Change.Same -> ActionResult("Auto-sync", ActionState.SKIPPED, "Already paused.")
        is Change.Failed -> ActionResult("Auto-sync", ActionState.BLOCKED, c.reason)
    }

    private fun availMem(): Long = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.availMem

    /**
     * Android 14 made killBackgroundProcesses() a no-op for other apps, so on 14+ REDCORE says so instead of
     * claiming apps were closed. On older versions it asks Android to close recently used cached apps and
     * reports the measured change in free memory.
     */
    suspend fun cleanBackground(deep: Boolean = false): ActionResult = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= 34) return@withContext ActionResult(
            "Background apps", ActionState.SKIPPED,
            "Android 14 and newer don't let apps close other apps. Android frees memory for the game by itself; close apps you don't need from Recents."
        )
        if (!usage.hasAccess()) return@withContext ActionResult("Background apps", ActionState.BLOCKED, "Grant Usage access so REDCORE can see which apps ran recently.")
        val launcher = context.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName
        val keep = setOfNotNull(launcher, context.packageName, _session.value?.pkg)
        val window = if (deep) 24L * 3600 * 1000 else 3L * 3600 * 1000
        val candidates = usage.recentBackgroundApps(keep, window).map { it.pkg }.filterNot { games.isGamePackage(it) }
        if (candidates.isEmpty()) return@withContext ActionResult("Background apps", ActionState.SKIPPED, "No recently used apps to close.")
        val before = availMem()
        candidates.forEach { runCatching { am.killBackgroundProcesses(it) } }
        delay(600)
        val freed = availMem() - before
        ActionResult(
            "Background apps", ActionState.APPLIED,
            "${if (deep) "Deep clean: asked" else "Asked"} Android to close ${candidates.size} cached app${if (candidates.size == 1) "" else "s"}" +
                (if (freed >= 8L * 1024 * 1024) " · ${fmtBytes(freed)} more free memory measured." else " · free memory didn't change measurably.")
        )
    }

    fun volumePct(): Int {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return if (max > 0) audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max else 0
    }

    fun setVolumePct(pct: Int) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, (pct * max / 100f).roundToInt(), 0) }
    }

    // ------------------------------------------------------------------ screen hang-up

    /** Dims the screen for hang-up. Journaled, so a crash during hang-up can't leave the screen at 1 %. */
    suspend fun beginHangup(): ActionResult = withContext(Dispatchers.IO) {
        if (!canSetBrightness()) return@withContext ActionResult("Hang-up", ActionState.SKIPPED, "Screen blacked out. Grant \"Modify system settings\" to also dim the backlight.")
        if (hangupPrev == null) hangupPrev = backend.read(Tweak.BRIGHTNESS_MODE) to backend.read(Tweak.BRIGHTNESS)
        _hangup.value = true
        setBrightnessNow(1).copy(name = "Hang-up")
    }

    suspend fun endHangup() = withContext(Dispatchers.IO) {
        val prev = hangupPrev ?: run { _hangup.value = false; return@withContext }
        hangupPrev = null
        if (_session.value != null) {
            // Back to the session's own brightness (still journaled against the pre-session original).
            prev.second?.let { change(Tweak.BRIGHTNESS, it, journaled = true) }
            prev.first?.let { change(Tweak.BRIGHTNESS_MODE, it, journaled = true) }
        } else release(listOf(Tweak.BRIGHTNESS, Tweak.BRIGHTNESS_MODE), keepUserChanges = false)
        _hangup.value = false
        refreshPending()
    }

    // ------------------------------------------------------------------ sessions

    private fun tweaksFor(spec: ModeSpec, smooth120: Boolean): Set<Tweak> = buildSet {
        if (spec.dnd) { add(Tweak.DND_FILTER); add(Tweak.DND_POLICY) }
        if (spec.brightness != null) { add(Tweak.BRIGHTNESS); add(Tweak.BRIGHTNESS_MODE) }
        if (spec.rotationLock) { add(Tweak.AUTO_ROTATE); add(Tweak.USER_ROTATION) }
        if (spec.refresh != RefreshTarget.DEFAULT || smooth120) addAll(listOf(Tweak.PEAK_REFRESH, Tweak.MIN_REFRESH, Tweak.SAMSUNG_REFRESH_MODE))
        if (spec.pauseSync) add(Tweak.MASTER_SYNC)
        if (spec.fastAnimations) { add(Tweak.ANIM_ANIMATOR); add(Tweak.ANIM_TRANSITION); add(Tweak.ANIM_WINDOW) }
    }

    /** Puts back anything left over from an interrupted session (crash, kill, reboot). Safe to call any time. */
    suspend fun recoverIfNeeded(): RecoveryReport? = sessionMutex.withLock { recoverLocked() }

    private suspend fun recoverLocked(): RecoveryReport? = withContext(Dispatchers.IO) {
        if (_session.value != null || _hangup.value) return@withContext null
        val j = synchronized(journalLock) { journal }
        if (j.isEmpty) return@withContext null
        val r = restoreSteps(AndroidSettingsBackend.restoreOrder, keepUserChanges = true)
        val label = j.meta?.label ?: "your last game session"
        val report = RecoveryReport(label, r)
        if (r.isNotEmpty()) { _recovery.value = report; postRecoveryNotice(report) }
        report
    }

    /** Retries settings that couldn't be restored earlier (called when the app resumes, e.g. after a permission grant). */
    suspend fun retryPendingRestore(): List<ActionResult> = sessionMutex.withLock {
        if (_session.value != null || _hangup.value || _pending.value.isEmpty()) return@withLock emptyList()
        withContext(Dispatchers.IO) { restoreSteps(AndroidSettingsBackend.restoreOrder, keepUserChanges = true) }
    }

    /**
     * Applies [spec] for [pkg]. Idempotent for the same game and mode. Switching games ends the previous
     * session's bookkeeping first (report + play log) while keeping the original settings journaled, and a
     * mode switch puts back settings the new mode no longer uses.
     */
    suspend fun startSession(pkg: String?, mode: GameMode, spec: ModeSpec, force: Boolean = false): GameSession = sessionMutex.withLock {
        recoverLocked()
        val cur = _session.value
        if (!force && cur != null && cur.pkg == pkg && cur.mode == mode && cur.spec == spec) return@withLock cur
        val cfg = settings.current()
        if (cur != null && cur.pkg != pkg) finishBookkeeping(cur, cfg.fahrenheit)
        val startedAt = if (cur != null && cur.pkg == pkg) cur.startedAt else System.currentTimeMillis()
        val label = pkg?.let { games.label(it) } ?: "Gaming session"
        synchronized(journalLock) { journal = journal.withMeta(JournalMeta(pkg, label, startedAt)); if (!journal.isEmpty) store.save(journal) }
        // Mark the session active first so every write below is journaled.
        _session.value = GameSession(pkg, label, mode, spec, startedAt, cur?.results.orEmpty())
        val results = withContext(Dispatchers.IO) { applySpec(pkg, mode, spec, cfg, cur?.spec) }
        val s = GameSession(pkg, label, mode, spec, startedAt, results)
        _session.value = s
        refreshPending()
        s
    }

    private suspend fun applySpec(pkg: String?, mode: GameMode, spec: ModeSpec, cfg: com.redcore.booster.data.AppSettings, previous: ModeSpec?): List<ActionResult> {
        val results = mutableListOf<ActionResult>()
        // Settings the previous mode changed but this one doesn't use go back to their originals now.
        if (previous != null) {
            val drop = tweaksFor(previous, cfg.smooth120) - tweaksFor(spec, cfg.smooth120)
            if (drop.isNotEmpty()) release(drop)
        }
        val calls = spec.calls ?: cfg.callMode
        CallPolicy.mode = calls
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
        results += spec.brightness?.let { setBrightnessNow(it) } ?: ActionResult("Brightness", ActionState.SKIPPED, "Unchanged.")
        results += if (cfg.smooth120) setRefreshNow(RefreshTarget.HIGHEST, "Smooth 120") else setRefreshNow(spec.refresh, "Refresh rate")
        results += if (spec.rotationLock) setRotationLockNow(true) else ActionResult("Rotation lock", ActionState.SKIPPED, "Not used.")
        results += if (spec.pauseSync) setSyncPaused() else ActionResult("Auto-sync", ActionState.SKIPPED, "Not used in ${mode.title} mode.")
        results += if (spec.cleanBackground) cleanBackground(deep = spec.deepClean) else ActionResult("Background apps", ActionState.SKIPPED, "Not used in ${mode.title} mode.")
        results += if (spec.fastAnimations) setFastAnimations() else ActionResult("Faster animations", ActionState.SKIPPED, "Only in Limit mode.")
        results += if (spec.keepScreenOn) {
            if (Settings.canDrawOverlays(context)) ActionResult("Keep screen awake", ActionState.APPLIED, "Screen stays on while the toolkit is showing.")
            else ActionResult("Keep screen awake", ActionState.BLOCKED, "Needs \"Display over other apps\".")
        } else ActionResult("Keep screen awake", ActionState.SKIPPED, "Uses normal screen timeout.")
        if (spec.lowLatencyWifi) results += ActionResult(
            "Low-latency Wi-Fi", ActionState.SKIPPED,
            "Android only gives low-latency Wi-Fi to the app on screen, so during play only the game itself can request it."
        )
        if (cfg.edgeGuard) results += withContext(Dispatchers.Main) {
            val n = EdgeGuard.show(context, cfg.edgeWidthDp)
            if (n == 2) ActionResult("Edge touch guard", ActionState.APPLIED, "Palm touches on the side edges are blocked (${cfg.edgeWidthDp} dp).", verified = true)
            else if (n > 0) ActionResult("Edge touch guard", ActionState.APPLIED, "Only one edge strip could be shown.")
            else ActionResult("Edge touch guard", ActionState.BLOCKED, "Android didn't allow the edge layer. Check \"Display over other apps\".")
        }
        if (cfg.triggers) results += if (Permissions.accessibilityEnabled(context))
            ActionResult("L1 / R1 triggers", ActionState.APPLIED, "Vol− taps L1, Vol+ taps R1.")
        else ActionResult("L1 / R1 triggers", ActionState.BLOCKED, "Enable the REDCORE accessibility service in Settings › Permissions.")
        results += withContext(Dispatchers.Main) { effects.start(pkg, spec, cfg) }
        return results
    }

    /**
     * Switches the running session to [m]. For a library game the game's own overrides are kept and the new
     * mode is saved to that game's profile; a session without a game uses (and saves) the default mode.
     * Without a session it only changes the default mode.
     */
    suspend fun switchMode(m: GameMode): GameSession? {
        val s = _session.value
        val profile = s?.pkg?.let { games.profile(it) }
        if (profile != null) games.updateProfile(profile.copy(mode = m)) else settings.setMode(m)
        if (s == null) return null
        val spec = profile?.copy(mode = m)?.effective() ?: ModeSpec.of(m)
        return startSession(s.pkg, m, spec)
    }

    /** Re-applies the running session (or starts a game-less one) with its current game profile. */
    suspend fun reapply(defaultMode: GameMode): GameSession {
        val s = _session.value
        val profile = s?.pkg?.let { games.profile(it) }
        val mode = profile?.mode ?: s?.mode ?: defaultMode
        val spec = profile?.effective() ?: ModeSpec.of(mode)
        return startSession(s?.pkg, mode, spec, force = true)
    }

    /** Report + play-time log for a session that is ending or being replaced by another game. */
    private suspend fun finishBookkeeping(ended: GameSession, fahrenheit: Boolean) {
        val r = withContext(Dispatchers.Main) { effects.report(ended.label, ended.mode.title) }
        if (r.minutes >= 1 || r.maxTemp != null) { _report.value = r; postReport(r, fahrenheit) }
        if (ended.pkg != null) settings.logSession(ended.pkg, ended.startedAt, System.currentTimeMillis())
    }

    /**
     * Ends the session: report, stop live effects, then put back every setting REDCORE still owns.
     * Each restored (or deliberately kept) setting is reported individually.
     */
    suspend fun endSession(reason: EndReason? = null): List<ActionResult> = sessionMutex.withLock {
        val ended = _session.value
        val cfg = settings.current()
        if (ended != null) finishBookkeeping(ended, cfg.fahrenheit)
        withContext(Dispatchers.Main) { effects.stop(); EdgeGuard.hide(context) }
        BarrageBus.mode = NotifMode.ALL
        CallPolicy.mode = CallMode.ALLOW
        hangupPrev = null; _hangup.value = false
        val out = mutableListOf<ActionResult>()
        _session.value = null
        if (ended != null || !synchronized(journalLock) { journal }.isEmpty) {
            out += withContext(Dispatchers.IO) {
                if (cfg.restoreOnExit) restoreSteps(AndroidSettingsBackend.restoreOrder, keepUserChanges = true)
                else {
                    synchronized(journalLock) { journal = RestoreJournal(); store.save(journal) }
                    listOf(ActionResult("Settings", ActionState.SKIPPED, "Kept the game settings because \"Restore settings after play\" is off."))
                }
            }
        }
        if (reason != null && reason != EndReason.USER_ENDED) out.add(0, ActionResult("Session ended", ActionState.APPLIED, reason.description))
        refreshPending()
        out
    }

    /** What is in force right now, re-read from the system (for the live "active effects" panel). */
    fun liveEffects(): List<LiveEffect> {
        val s = _session.value ?: return emptyList()
        val j = synchronized(journalLock) { journal }
        val out = mutableListOf<LiveEffect>()
        fun held(t: Tweak) = j.entries[t]?.let { RestoreJournal.same(t, backend.read(t), it.applied) } ?: false
        if (s.spec.dnd) out += LiveEffect("Do Not Disturb", if (isDndOn()) "On" else "Off", isDndOn())
        if (j.entries.containsKey(Tweak.BRIGHTNESS)) out += LiveEffect("Brightness", "${currentBrightnessPct() ?: "—"} %", held(Tweak.BRIGHTNESS))
        if (j.entries.containsKey(Tweak.PEAK_REFRESH) || j.entries.containsKey(Tweak.SAMSUNG_REFRESH_MODE))
            out += LiveEffect("Refresh rate", "${currentRefreshHz() ?: "—"} Hz", held(Tweak.PEAK_REFRESH) || held(Tweak.SAMSUNG_REFRESH_MODE))
        if (j.entries.containsKey(Tweak.AUTO_ROTATE)) out += LiveEffect("Rotation lock", if (isRotationLocked()) "Locked" else "Auto", held(Tweak.AUTO_ROTATE))
        if (j.entries.containsKey(Tweak.MASTER_SYNC)) out += LiveEffect("Auto-sync", if (backend.read(Tweak.MASTER_SYNC) == "false") "Paused" else "On", held(Tweak.MASTER_SYNC))
        if (j.entries.containsKey(Tweak.ANIM_ANIMATOR)) out += LiveEffect("Animations", "${backend.read(Tweak.ANIM_ANIMATOR) ?: "1.0"}×", held(Tweak.ANIM_ANIMATOR))
        if (EdgeGuard.active.value) out += LiveEffect("Edge guard", "On", true)
        return out
    }

    private fun postReport(r: SessionReport, fahrenheit: Boolean) {
        val parts = listOfNotNull(
            "${r.minutes} min", r.batteryUsed?.let { "$it% battery" }, r.maxTemp?.let { "peak ${fmtTemp(it, fahrenheit)}" },
            r.avgFps?.let { "$it FPS avg" }, r.avgPing?.let { "$it ms ping" }
        )
        val n = androidx.core.app.NotificationCompat.Builder(context, com.redcore.booster.RedcoreApp.CH_SESSION)
            .setSmallIcon(com.redcore.booster.R.drawable.ic_stat).setColor(0xFFFF1E3C.toInt())
            .setContentTitle("${r.label} · session report").setContentText(parts.joinToString(" · ")).setAutoCancel(true).build()
        runCatching { nm.notify(NOTIF_REPORT, n) }
    }

    private fun postRecoveryNotice(r: RecoveryReport) {
        val restored = r.results.count { it.state == ActionState.APPLIED }
        val blocked = r.results.count { it.state == ActionState.BLOCKED }
        val text = buildString {
            append("REDCORE was interrupted during ${r.label}. ")
            if (restored > 0) append("$restored setting${if (restored == 1) "" else "s"} put back. ")
            if (blocked > 0) append("$blocked still need a permission — open REDCORE.")
        }
        val n = androidx.core.app.NotificationCompat.Builder(context, com.redcore.booster.RedcoreApp.CH_ALERT)
            .setSmallIcon(com.redcore.booster.R.drawable.ic_stat).setColor(0xFFFF1E3C.toInt())
            .setContentTitle("Settings restored").setContentText(text)
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(text)).setAutoCancel(true).build()
        runCatching { nm.notify(NOTIF_RECOVERY, n) }
    }

    companion object {
        const val NOTIF_REPORT = 63
        const val NOTIF_RECOVERY = 64
    }
}
