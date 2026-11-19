package com.redcore.booster.ui

import android.app.Application
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.redcore.booster.app
import com.redcore.booster.booster.ActionResult
import com.redcore.booster.booster.ActionState
import com.redcore.booster.booster.LiveEffect
import com.redcore.booster.booster.Perm
import com.redcore.booster.booster.Permissions
import com.redcore.booster.core.EndReason
import com.redcore.booster.data.AppSettings
import com.redcore.booster.data.CallMode
import com.redcore.booster.data.DeviceReport
import com.redcore.booster.data.GameMode
import com.redcore.booster.data.GameProfile
import com.redcore.booster.data.InstalledApp
import com.redcore.booster.data.LibraryGame
import com.redcore.booster.data.NotifMode
import com.redcore.booster.data.PlayStat
import com.redcore.booster.data.PlaySummary
import com.redcore.booster.data.SettingsRepository
import com.redcore.booster.overlay.EdgeGuard
import com.redcore.booster.overlay.OverlayService
import com.redcore.booster.service.GameDetectionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ResultSheet(val title: String, val results: List<ActionResult>)

/** A boost in progress, shown as the full-screen boost sequence. [results] is null while actions are still running. */
data class BoostRun(
    val pkg: String?,
    val label: String,
    val mode: GameMode,
    val results: List<ActionResult>? = null,
    val freedMb: Long? = null,
    val batterySaver: Boolean = false,
    val launch: Boolean = false,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val a = application.app

    val stats = a.monitor.stats
    val history = a.monitor.history
    val settings: StateFlow<AppSettings> = a.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val loaded: StateFlow<Boolean> = a.settings.settings.map { true }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val library: StateFlow<List<LibraryGame>?> = a.games.library.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val session = a.boost.session
    val fx = a.boost.effects.state
    val report = a.boost.lastReport
    val pendingRestore = a.boost.pendingRestore
    val recovery = a.boost.recovery
    fun dismissReport() = a.boost.clearReport()
    fun dismissRecovery() = a.boost.clearRecovery()
    val toolkitRunning = OverlayService.running

    private val _perms = MutableStateFlow(Perm.entries.associateWith { false })
    val perms: StateFlow<Map<Perm, Boolean>> = _perms.asStateFlow()

    /** Android 13+ blocks Accessibility / Notification access for APK installs until "Allow restricted settings". */
    val restrictedSettingsLikely: Boolean = Permissions.restrictedSettingsLikely(application)

    private val _recent = MutableStateFlow<List<PlayStat>>(emptyList())
    val recent: StateFlow<List<PlayStat>> = _recent.asStateFlow()

    private val _today = MutableStateFlow<PlaySummary?>(null)
    val today = _today.asStateFlow()
    private val _week = MutableStateFlow<PlaySummary?>(null)
    val week = _week.asStateFlow()

    private val _device = MutableStateFlow<DeviceReport?>(null)
    val device = _device.asStateFlow()

    private val _installed = MutableStateFlow<List<InstalledApp>?>(null)
    val installed = _installed.asStateFlow()

    private val _sheet = MutableStateFlow<ResultSheet?>(null)
    val sheet = _sheet.asStateFlow()

    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toast: SharedFlow<String> = _toast

    private val _live = MutableStateFlow<List<LiveEffect>>(emptyList())
    /** Effects in force right now, re-read from the system every 2 s while a screen shows them. */
    val liveEffects: StateFlow<List<LiveEffect>> = _live.asStateFlow()
    private var liveJob: Job? = null

    private val _ask = MutableStateFlow<com.redcore.booster.ui.screens.PermAsk?>(null)
    val ask = _ask.asStateFlow()
    private var pendingPerm: Perm? = null
    private var pendingAction: (() -> Unit)? = null
    private var declineAction: (() -> Unit)? = null

    /** Runs [action] if [p] is granted; otherwise explains why it's needed and resumes the action once it is granted. */
    fun require(p: Perm, message: String, declineLabel: String = "Not now", onDecline: (() -> Unit)? = null, action: () -> Unit) {
        if (Permissions.granted(getApplication(), p)) { action(); return }
        pendingPerm = p; pendingAction = action; declineAction = onDecline
        _ask.value = com.redcore.booster.ui.screens.PermAsk(p, message, declineLabel)
    }

    /** Called when the user taps Allow — the UI then opens the system screen / runtime prompt. */
    fun askAccepted() { _ask.value = null }

    fun askDeclined() {
        _ask.value = null
        val d = declineAction
        pendingPerm = null; pendingAction = null; declineAction = null
        d?.invoke()
    }

    private val _run = MutableStateFlow<BoostRun?>(null)
    val run = _run.asStateFlow()
    private val _nav = MutableSharedFlow<Dest>(extraBufferCapacity = 2)
    val navEvents: SharedFlow<Dest> = _nav

    private fun availMem(): Long {
        val am = getApplication<Application>().getSystemService(android.app.ActivityManager::class.java)
        return android.app.ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.availMem
    }
    private fun batterySaver() = getApplication<Application>().getSystemService(android.os.PowerManager::class.java)?.isPowerSaveMode == true

    /** Called when the boost sequence finishes (or the user taps to skip). */
    fun runFinished() {
        val r = _run.value ?: return
        if (r.results == null) return
        _run.value = null
        if (r.launch && r.pkg != null) startGame(r.pkg) else _nav.tryEmit(Dest.GAMES)
        refresh()
    }

    private fun startGame(pkg: String) {
        val intent = a.games.launchIntent(pkg) ?: run { _toast.tryEmit("This game can't be opened (no launcher entry)."); return }
        runCatching { getApplication<Application>().startActivity(intent) }.onFailure { _toast.tryEmit("Couldn't open the game.") }
    }

    private val _background = MutableStateFlow<List<PlayStat>?>(null)
    val background = _background.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.Default) { _device.value = runCatching { a.deviceInfo.report() }.getOrNull() }
        viewModelScope.launch {
            val s = a.settings.settings.first()
            if (!s.libraryScanned) a.games.scanForGames()
        }
    }

    /** Called on every resume: permissions may have been granted or revoked in system settings. */
    fun refresh() {
        val ctx = getApplication<Application>()
        _perms.value = Perm.entries.associateWith { Permissions.granted(ctx, it) }
        pendingPerm?.let { p ->
            if (_perms.value[p] == true) {
                val act = pendingAction
                pendingPerm = null; pendingAction = null; declineAction = null
                act?.invoke()
            }
        }
        viewModelScope.launch {
            // A permission may just have come back: finish restoring settings left over from an earlier session.
            val retried = a.boost.retryPendingRestore()
            if (retried.any { it.state == ActionState.APPLIED }) _sheet.value = ResultSheet("Settings restored", retried)
            val lib = a.games.library.first().map { it.profile.pkg }.toSet()
            _recent.value = a.usage.recent(lib)
            _today.value = a.usage.today(lib)
            _week.value = a.usage.week(lib)
            _background.value = if (a.usage.hasAccess()) a.usage.recentBackgroundApps(lib + ctx.packageName) else null
        }
    }

    /** Polls the live effects while a screen that shows them is visible. */
    fun watchLiveEffects(on: Boolean) {
        liveJob?.cancel()
        if (!on) return
        liveJob = viewModelScope.launch {
            while (isActive) {
                _live.value = withContext(Dispatchers.IO) { a.boost.liveEffects() }
                delay(2_000)
            }
        }
    }

    fun loadInstalled() = viewModelScope.launch { _installed.value = a.games.installedApps() }

    /** Mode selector: changes the running session (keeping the game's own overrides) or the default mode. */
    fun setMode(m: GameMode) = viewModelScope.launch {
        val r = a.boost.switchMode(m)
        if (r != null) _toast.tryEmit("${m.title} mode · ${r.appliedCount} optimizations active")
    }

    /** "Boost" / "Activate" / "Re-apply": runs the boost sequence; a running game session keeps its game and profile. */
    fun boostNow() = viewModelScope.launch {
        if (_run.value != null) return@launch
        val m = session.value?.mode ?: settings.value.globalMode
        _run.value = BoostRun(session.value?.pkg, session.value?.label ?: "Game Space", m, batterySaver = batterySaver())
        val before = availMem()
        val s = a.boost.reapply(settings.value.globalMode)
        delay(700) // let Android finish closing apps before measuring
        val freed = ((availMem() - before) / (1024 * 1024)).takeIf { it >= 16 }
        _run.value = _run.value?.copy(label = s.label, mode = s.mode, results = s.results, freedMb = freed)
        watchSessionIfPossible()
    }

    fun endSession() = viewModelScope.launch {
        val hadSession = session.value != null
        val r = a.boost.endSession(EndReason.USER_ENDED)
        OverlayService.stop(getApplication())
        when {
            r.isNotEmpty() -> _sheet.value = ResultSheet("Session ended — here's what was restored", r)
            hadSession -> _toast.tryEmit("Gaming session ended — nothing needed restoring")
        }
    }

    fun cleanBackground() = viewModelScope.launch {
        val r = a.boost.cleanBackground()
        _sheet.value = ResultSheet("Background apps", listOf(r))
        refresh()
    }

    fun dismissSheet() { _sheet.value = null }

    fun launchGame(pkg: String) {
        val wantsOverlay = library.value?.firstOrNull { it.profile.pkg == pkg }?.profile?.overlay ?: true
        if (wantsOverlay && !Settings.canDrawOverlays(getApplication())) {
            require(Perm.OVERLAY, "Allow REDCORE to display over other apps so the gaming menu and HUD can appear inside ${a.games.label(pkg)}.",
                declineLabel = "Play without menu", onDecline = { doLaunch(pkg) }) { doLaunch(pkg) }
        } else doLaunch(pkg)
    }

    private fun doLaunch(pkg: String) = viewModelScope.launch {
        val ctx = getApplication<Application>()
        if (a.games.launchIntent(pkg) == null) { _toast.tryEmit("This game can't be opened (no launcher entry)."); return@launch }
        if (_run.value != null) return@launch
        val profile = a.games.profile(pkg) ?: GameProfile(pkg, settings.value.globalMode)
        _run.value = BoostRun(pkg, a.games.label(pkg), profile.mode, batterySaver = batterySaver(), launch = true)
        val before = availMem()
        val s = a.boost.startSession(pkg, profile.mode, profile.effective())
        if (profile.overlay && Settings.canDrawOverlays(ctx)) OverlayService.start(ctx)
        watchSessionIfPossible()
        delay(450)
        val freed = ((availMem() - before) / (1024 * 1024)).takeIf { it >= 16 }
        val results = if (a.usage.hasAccess()) s.results
        else s.results + ActionResult("Auto restore", ActionState.BLOCKED, "Grant Usage access so REDCORE notices when you close the game and puts your settings back. Until then, tap End when you finish.")
        _run.value = _run.value?.copy(results = results, freedMb = freed)
    }

    /**
     * With Usage access, game detection runs in watch mode so a session started here ends — and settings are
     * restored — when you leave the game, even with auto game boost off.
     */
    private fun watchSessionIfPossible() {
        if (a.usage.hasAccess() && !GameDetectionService.running) GameDetectionService.start(getApplication())
    }

    fun toggleToolkit() {
        val ctx = getApplication<Application>()
        if (OverlayService.running.value) { OverlayService.stop(ctx); return }
        require(Perm.OVERLAY, "The gaming menu floats on top of your games, so Android needs you to allow REDCORE to display over other apps.") {
            if (OverlayService.start(ctx)) _toast.tryEmit("Toolkit on — swipe in from a side edge in any game")
            else _toast.tryEmit("Android didn't let the toolkit start. Open REDCORE again and retry.")
        }
    }

    fun addGames(pkgs: Collection<String>) = viewModelScope.launch {
        a.games.add(pkgs); refresh()
        _toast.tryEmit("Added ${pkgs.size} game${if (pkgs.size == 1) "" else "s"} to Game Space")
    }

    fun removeGame(pkg: String) = viewModelScope.launch { a.games.remove(pkg); refresh() }
    fun updateProfile(p: GameProfile) = viewModelScope.launch {
        a.games.updateProfile(p)
        // Editing the profile of the game being played applies it right away.
        val s = session.value
        if (s != null && s.pkg == p.pkg) a.boost.startSession(p.pkg, p.mode, p.effective())
    }

    fun scan() = viewModelScope.launch {
        val n = a.games.scanForGames()
        _toast.tryEmit(if (n == 0) "No new games found. Use + to add any app manually." else "Found $n new game${if (n == 1) "" else "s"}")
        refresh()
    }

    fun setAutoDetect(v: Boolean) = viewModelScope.launch {
        val ctx = getApplication<Application>()
        if (!v) {
            a.settings.setAutoDetect(false)
            // Keep watching a running session so its settings are restored; otherwise stop detection.
            if (session.value == null) GameDetectionService.stop(ctx)
            return@launch
        }
        require(Perm.USAGE, "Auto game boost watches which app is in front so it can apply a game's profile the moment you open it — and restore your settings when you leave. Android calls this Usage access.") {
            viewModelScope.launch {
                a.settings.setAutoDetect(true)
                if (!GameDetectionService.start(ctx)) _toast.tryEmit("Android didn't allow auto game boost to start. Open REDCORE and try again.")
                else if (!Permissions.granted(ctx, Perm.BATTERY)) _toast.tryEmit("Tip: allow unrestricted battery so Android doesn't pause auto boost.")
            }
        }
    }

    fun setHud(v: Boolean) = viewModelScope.launch { a.settings.setHud(v) }
    fun setRestore(v: Boolean) = viewModelScope.launch { a.settings.setRestore(v) }
    fun setFahrenheit(v: Boolean) = viewModelScope.launch { a.settings.setFahrenheit(v) }
    fun setOverlayScale(v: Float) = viewModelScope.launch { a.settings.setOverlayScale(v) }
    fun setEdgeGuard(v: Boolean) {
        if (!v) { viewModelScope.launch { a.settings.setEdgeGuard(false) }; return }
        require(Perm.OVERLAY, "The edge guard is a thin invisible layer over the screen edges, which needs permission to display over other apps.") {
            viewModelScope.launch { a.settings.setEdgeGuard(true) }
        }
    }
    fun setEdgeRange(v: Int) = viewModelScope.launch {
        a.settings.setEdgeRange(v)
        if (EdgeGuard.active.value) EdgeGuard.show(getApplication(), a.settings.current().edgeWidthDp)
    }
    fun previewEdge() = viewModelScope.launch {
        if (!EdgeGuard.preview(getApplication(), a.settings.current().edgeWidthDp)) _toast.tryEmit("Allow \"Display over other apps\" first.")
    }
    fun setTriggers(v: Boolean) {
        if (!v) { viewModelScope.launch { a.settings.setTriggers(false) }; return }
        require(Perm.ACCESSIBILITY, "L1/R1 triggers turn the volume keys into screen taps. Android only allows that through an accessibility service. REDCORE's service never reads what's on your screen.") {
            viewModelScope.launch { a.settings.setTriggers(true) }
        }
    }
    fun setTriggerPoints(l1x: Float, l1y: Float, r1x: Float, r1y: Float) = viewModelScope.launch { a.settings.setTriggerPoints(l1x, l1y, r1x, r1y) }
    fun <T> put(key: androidx.datastore.preferences.core.Preferences.Key<T>, value: T) = viewModelScope.launch { a.settings.put(key, value) }

    fun setSmooth120(on: Boolean) = viewModelScope.launch {
        a.settings.put(SettingsRepository.K.smooth, on)
        val r = a.boost.applySmooth120(on)
        when {
            on && r.state == ActionState.BLOCKED -> _toast.tryEmit("Smooth 120 needs the one-time ADB grant — see Settings › Permissions.")
            r.state == ActionState.APPLIED -> _toast.tryEmit(r.detail)
        }
    }

    private val _vendor = MutableStateFlow<List<InstalledApp>>(emptyList())
    val vendorTools = _vendor.asStateFlow()
    fun loadVendorTools() = viewModelScope.launch { _vendor.value = a.games.vendorGameTools() }

    fun setHaptics(on: Boolean) {
        if (!on) { put(SettingsRepository.K.haptics, false); return }
        require(Perm.MICROPHONE, "4D vibration listens to the game's sound to know when to vibrate. Android calls this microphone access. Nothing is recorded or stored.") {
            put(SettingsRepository.K.haptics, true)
        }
    }

    fun setCalls(m: CallMode) {
        put(SettingsRepository.K.calls, m.name)
        if (m == CallMode.DECLINE && android.os.Build.VERSION.SDK_INT >= 29)
            require(Perm.CALL_SCREENING, "To decline calls during games, Android needs REDCORE to be your call-screening app. It only acts while a game session is running.") {}
    }

    fun setNotif(m: NotifMode) {
        put(SettingsRepository.K.notif, m.name)
        if (m == NotifMode.BARRAGE)
            require(Perm.NOTIF_ACCESS, "Barrage shows your messages as a small line gliding over the game instead of pop-ups. Android calls this notification access. Nothing is stored.") {}
    }

    fun setCool(on: Boolean) = put(SettingsRepository.K.cool, on)
    fun setCoolTarget(t: Int) = put(SettingsRepository.K.coolTarget, t)

    fun finishOnboarding() = viewModelScope.launch { a.settings.setOnboarded() }

    fun toast(msg: String) { _toast.tryEmit(msg) }
}
