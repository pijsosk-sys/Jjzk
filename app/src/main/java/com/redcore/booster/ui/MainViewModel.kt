package com.redcore.booster.ui

import android.app.Application
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.redcore.booster.app
import com.redcore.booster.booster.ActionResult
import com.redcore.booster.booster.Perm
import com.redcore.booster.booster.Permissions
import com.redcore.booster.data.AppSettings
import com.redcore.booster.data.DeviceReport
import com.redcore.booster.data.GameMode
import com.redcore.booster.data.GameProfile
import com.redcore.booster.data.InstalledApp
import com.redcore.booster.data.LibraryGame
import com.redcore.booster.data.ModeSpec
import com.redcore.booster.data.PlayStat
import com.redcore.booster.data.PlaySummary
import com.redcore.booster.overlay.OverlayService
import com.redcore.booster.service.GameDetectionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ResultSheet(val title: String, val results: List<ActionResult>)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val a = application.app

    val stats = a.monitor.stats
    val history = a.monitor.history
    val settings: StateFlow<AppSettings> = a.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val loaded: StateFlow<Boolean> = a.settings.settings.map { true }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val library: StateFlow<List<LibraryGame>?> = a.games.library.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val session = a.boost.session
    val toolkitRunning = OverlayService.running

    private val _perms = MutableStateFlow(Perm.entries.associateWith { false })
    val perms: StateFlow<Map<Perm, Boolean>> = _perms.asStateFlow()

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

    private val _background = MutableStateFlow<List<PlayStat>?>(null)
    val background = _background.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.Default) { _device.value = a.deviceInfo.report() }
        viewModelScope.launch {
            val s = a.settings.settings.first()
            if (!s.libraryScanned) a.games.scanForGames()
        }
    }

    /** Called on every resume: permissions may have been granted in system settings. */
    fun refresh() {
        val ctx = getApplication<Application>()
        _perms.value = Perm.entries.associateWith { Permissions.granted(ctx, it) }
        viewModelScope.launch {
            val lib = a.games.library.first().map { it.profile.pkg }.toSet()
            _recent.value = a.usage.recent(lib)
            _today.value = a.usage.today(lib)
            _week.value = a.usage.week(lib)
            _background.value = if (a.usage.hasAccess()) a.usage.recentBackgroundApps(lib + ctx.packageName) else null
        }
    }

    fun loadInstalled() = viewModelScope.launch { if (_installed.value == null) _installed.value = a.games.installedApps() }

    fun setMode(m: GameMode) = viewModelScope.launch {
        a.settings.setMode(m)
        // If a session without a specific game is running, re-apply immediately so the switch is real.
        val s = session.value
        if (s != null && s.pkg == null) a.boost.startSession(null, m, ModeSpec.of(m))
    }

    fun boostNow() = viewModelScope.launch {
        val m = settings.value.globalMode
        val s = a.boost.startSession(null, m, ModeSpec.of(m))
        _sheet.value = ResultSheet("${m.title} mode active", s.results)
    }

    fun endSession() = viewModelScope.launch {
        val r = a.boost.endSession()
        if (r.isNotEmpty()) _sheet.value = ResultSheet("Session ended — settings restored", r) else _toast.tryEmit("Gaming session ended")
    }

    fun cleanBackground() = viewModelScope.launch {
        val r = a.boost.cleanBackground()
        _sheet.value = ResultSheet("Background apps", listOf(r))
        refresh()
    }

    fun dismissSheet() { _sheet.value = null }

    fun launchGame(pkg: String) = viewModelScope.launch {
        val ctx = getApplication<Application>()
        val intent = a.games.launchIntent(pkg)
        if (intent == null) { _toast.tryEmit("This game can't be opened (no launcher entry)."); return@launch }
        val profile = a.games.profile(pkg) ?: GameProfile(pkg, settings.value.globalMode)
        a.boost.startSession(pkg, profile.mode, profile.effective())
        if (profile.overlay && Settings.canDrawOverlays(ctx)) OverlayService.start(ctx)
        withContext(Dispatchers.Main) { ctx.startActivity(intent) }
    }

    fun toggleToolkit() {
        val ctx = getApplication<Application>()
        if (OverlayService.running.value) OverlayService.stop(ctx)
        else if (Settings.canDrawOverlays(ctx)) OverlayService.start(ctx)
        else _toast.tryEmit("Allow \"Display over other apps\" to use the floating toolkit.")
    }

    fun addGames(pkgs: Collection<String>) = viewModelScope.launch {
        a.games.add(pkgs); refresh()
        _toast.tryEmit("Added ${pkgs.size} game${if (pkgs.size == 1) "" else "s"} to Game Space")
    }

    fun removeGame(pkg: String) = viewModelScope.launch { a.games.remove(pkg); refresh() }
    fun updateProfile(p: GameProfile) = viewModelScope.launch { a.games.updateProfile(p) }

    fun scan() = viewModelScope.launch {
        val n = a.games.scanForGames()
        _toast.tryEmit(if (n == 0) "No new games found. Use + to add any app manually." else "Found $n new game${if (n == 1) "" else "s"}")
        refresh()
    }

    fun setAutoDetect(v: Boolean) = viewModelScope.launch {
        val ctx = getApplication<Application>()
        if (v && !Permissions.granted(ctx, Perm.USAGE)) { _toast.tryEmit("Grant Usage access first so REDCORE can see when a game opens."); return@launch }
        a.settings.setAutoDetect(v)
        if (v) GameDetectionService.start(ctx) else GameDetectionService.stop(ctx)
    }

    fun setHud(v: Boolean) = viewModelScope.launch { a.settings.setHud(v) }
    fun setRestore(v: Boolean) = viewModelScope.launch { a.settings.setRestore(v) }
    fun setFahrenheit(v: Boolean) = viewModelScope.launch { a.settings.setFahrenheit(v) }
    fun setOverlayScale(v: Float) = viewModelScope.launch { a.settings.setOverlayScale(v) }
    fun setEdgeGuard(v: Boolean) = viewModelScope.launch { a.settings.setEdgeGuard(v) }
    fun setEdgeRange(v: Int) = viewModelScope.launch {
        a.settings.setEdgeRange(v)
        if (com.redcore.booster.overlay.EdgeGuard.active.value) com.redcore.booster.overlay.EdgeGuard.show(getApplication(), a.settings.current().edgeWidthDp)
    }
    fun previewEdge() = viewModelScope.launch {
        if (!com.redcore.booster.overlay.EdgeGuard.preview(getApplication(), a.settings.current().edgeWidthDp)) _toast.tryEmit("Allow \"Display over other apps\" first.")
    }
    fun setTriggers(v: Boolean) = viewModelScope.launch { a.settings.setTriggers(v) }
    fun setTriggerPoints(l1x: Float, l1y: Float, r1x: Float, r1y: Float) = viewModelScope.launch { a.settings.setTriggerPoints(l1x, l1y, r1x, r1y) }
    fun finishOnboarding() = viewModelScope.launch { a.settings.setOnboarded() }

    fun toast(msg: String) { _toast.tryEmit(msg) }
}
