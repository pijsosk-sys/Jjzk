package com.redcore.booster.overlay

import android.app.AppOpsManager
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.redcore.booster.MainActivity
import com.redcore.booster.R
import com.redcore.booster.RedcoreApp
import com.redcore.booster.app
import com.redcore.booster.booster.ActionState
import com.redcore.booster.booster.Perm
import com.redcore.booster.booster.Permissions
import com.redcore.booster.core.EndReason
import com.redcore.booster.data.AppSettings
import com.redcore.booster.record.ScreenRecordService
import com.redcore.booster.service.RedcoreAccessibilityService
import com.redcore.booster.ui.theme.RedcoreTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Hosts all in-game windows: the HUD/menu, swipe handles, crosshair, stopwatch, barrage ticker and
 * screen hang-up. Every window goes through [OverlayWindows], so the service only reports windows Android
 * actually accepted. Compose needs lifecycle/saved-state/view-model owners, which this service provides.
 */
class OverlayService : LifecycleService(), SavedStateRegistryOwner, ViewModelStoreOwner {
    private val ssrc = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = ssrc.savedStateRegistry
    override val viewModelStore = ViewModelStore()

    private lateinit var wm: WindowManager
    private lateinit var windows: OverlayWindows
    private lateinit var params: WindowManager.LayoutParams
    private val main = Handler(Looper.getMainLooper())
    val expanded = MutableStateFlow(false)
    val stopwatchOpen = MutableStateFlow(false)
    val hangupOn = MutableStateFlow(false)

    private var handleKey = ""
    private var crossKey = ""
    private var savedX = 0
    private var savedY = 0
    private var opsListener: AppOpsManager.OnOpChangedListener? = null

    private val dp get() = resources.displayMetrics.density

    override fun onCreate() {
        ssrc.performAttach()
        ssrc.performRestore(null)
        super.onCreate()
        // startForeground must always run first: a started foreground service that never calls it crashes the app.
        val fgOk = runCatching {
            ServiceCompat.startForeground(this, NOTIF_ID, notification(), if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
        }.isSuccess
        if (!fgOk || !overlayAllowed()) {
            if (fgOk) toast("Allow \"Display over other apps\" for REDCORE to show the game menu.")
            stopSelf(); return
        }
        wm = getSystemService(WindowManager::class.java)
        windows = OverlayWindows(WindowManagerHost(wm)) { tag, t -> onWindowRejected(tag, t) }
        watchOverlayPermission()
        lifecycleScope.launch { windows.active.collect { _windows.value = it; running.value = MAIN in it } }
        lifecycleScope.launch {
            if (!addMainWindow()) { stopSelf(); return@launch }
            addTicker()
            app.settings.settings.collect { applySettings(it) }
        }
        lifecycleScope.launch { app.boost.session.collect { s -> setKeepScreenOn(s?.spec?.keepScreenOn == true) } }
        lifecycleScope.launch { app.boost.effects.events.collect { toast(it) } }
        // The session can end the hang-up (e.g. you left the game): remove its black window too.
        lifecycleScope.launch { app.boost.hangupActive.collect { on -> if (!on && windows.isAttached(HANGUP)) { windows.detach(HANGUP); hangupOn.value = false } } }
        lifecycleScope.launch { expanded.collect { updateVisibility() } }
    }

    // ------------------------------------------------------------------ permission & failures

    /** AppOps is checked directly: Settings.canDrawOverlays can lag behind a change on some versions. */
    private fun overlayAllowed(): Boolean {
        val ops = getSystemService(AppOpsManager::class.java) ?: return Settings.canDrawOverlays(this)
        val mode = if (Build.VERSION.SDK_INT >= 29) ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, Process.myUid(), packageName)
        else @Suppress("DEPRECATION") ops.checkOpNoThrow(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, Process.myUid(), packageName)
        return when (mode) {
            AppOpsManager.MODE_ALLOWED -> true
            AppOpsManager.MODE_DEFAULT -> checkSelfPermission(android.Manifest.permission.SYSTEM_ALERT_WINDOW) == PackageManager.PERMISSION_GRANTED || Settings.canDrawOverlays(this)
            else -> false
        }
    }

    /** If the user revokes "Display over other apps" while playing, close everything cleanly instead of leaving hidden windows. */
    private fun watchOverlayPermission() {
        val ops = getSystemService(AppOpsManager::class.java) ?: return
        val l = AppOpsManager.OnOpChangedListener { _, pkg ->
            if (pkg == packageName) main.postDelayed({ if (!overlayAllowed()) onOverlayRevoked() }, 300)
        }
        opsListener = l
        runCatching { ops.startWatchingMode(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, packageName, l) }
    }

    private fun onOverlayRevoked() {
        toast("\"Display over other apps\" was turned off, so the game menu closed.")
        if (windows.isAttached(HANGUP)) endHangup()
        EdgeGuard.hide(this)
        windows.detachAll()
        stopSelf()
    }

    private fun onWindowRejected(tag: String, t: Throwable) {
        val what = when (tag) {
            MAIN -> "the game menu"; TICKER -> "the barrage line"; CROSSHAIR -> "the crosshair"; STOPWATCH -> "the stopwatch"
            HANGUP -> "screen hang-up"; else -> "the swipe handle"
        }
        toast("Android refused to show $what${if (!overlayAllowed()) " — \"Display over other apps\" is off" else ""}.")
    }

    // ------------------------------------------------------------------ windows

    private fun overlayParams(w: Int, h: Int, flags: Int) = WindowManager.LayoutParams(
        w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or flags,
        PixelFormat.TRANSLUCENT
    ).apply { if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES }

    private fun composeView(content: @Composable () -> Unit) = ComposeView(this).apply {
        setViewTreeLifecycleOwner(this@OverlayService)
        setViewTreeSavedStateRegistryOwner(this@OverlayService)
        setViewTreeViewModelStoreOwner(this@OverlayService)
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
        setContent { RedcoreTheme { content() } }
    }

    private suspend fun addMainWindow(): Boolean {
        val s = app.settings.settings.first()
        params = overlayParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, 0).apply {
            gravity = Gravity.TOP or Gravity.START
            x = s.bubbleX; y = s.bubbleY
        }
        clampToScreen(params)
        return windows.attach(MAIN, composeView { OverlayRoot(this@OverlayService) }, params)
    }

    private fun applySettings(s: AppSettings) {
        updateHandles(s)
        updateCrosshair(s)
        app.boost.effects.overlayVisible = expanded.value || s.hudEnabled
    }

    private fun updateVisibility() {
        app.boost.effects.overlayVisible = expanded.value || app.boost.effects.cfg.hudEnabled
    }

    // ------------------------------------------------------------------ swipe-to-open handles

    private fun updateHandles(s: AppSettings) {
        val key = "${s.swipeSide}${s.handleHint}"
        val attached = windows.isAttached(HANDLE_L) || windows.isAttached(HANDLE_R)
        if (key == handleKey && attached == s.swipeSide) return
        handleKey = key
        windows.detach(HANDLE_L); windows.detach(HANDLE_R)
        fun add(tag: String, dir: Int, gravity: Int) {
            val v = SwipeHandleView(this, dir, s.handleHint) { if (!expanded.value && !hangupOn.value) setExpanded(true) }
            val lp = overlayParams((18 * dp).toInt(), (160 * dp).toInt(), WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS).apply {
                this.gravity = gravity; x = 0; y = (56 * dp).toInt()
            }
            if (Build.VERSION.SDK_INT >= 29) {
                // Ask Android not to treat swipes on this small strip as the system Back gesture.
                v.addOnLayoutChangeListener { vv, l, t, r, b, _, _, _, _ -> vv.systemGestureExclusionRects = listOf(Rect(0, 0, r - l, b - t)) }
            }
            windows.attach(tag, v, lp)
        }
        // Side edges only: swipe inward from the upper part of the left or right edge.
        if (s.swipeSide) {
            add(HANDLE_L, SwipeHandleView.RIGHT, Gravity.START or Gravity.TOP)
            add(HANDLE_R, SwipeHandleView.LEFT, Gravity.END or Gravity.TOP)
        }
        setHandlesActive(!expanded.value)
    }

    private fun setHandlesActive(active: Boolean) {
        for (tag in listOf(HANDLE_L, HANDLE_R)) {
            val v = windows.view(tag) ?: continue
            val lp = windows.params(tag) ?: continue
            // INVISIBLE + not touchable: Android 12+ lets touches pass through invisible overlay windows.
            v.visibility = if (active) View.VISIBLE else View.INVISIBLE
            lp.flags = if (active) lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv() else lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            windows.update(tag, lp)
        }
    }

    // ------------------------------------------------------------------ crosshair

    private fun updateCrosshair(s: AppSettings) {
        val key = "${s.crosshair}${s.crossSize}"
        if (key == crossKey && windows.isAttached(CROSSHAIR) == s.crosshair) return
        crossKey = key
        windows.detach(CROSSHAIR)
        if (!s.crosshair) return
        val px = ((36 + 64 * s.crossSize) * dp).toInt()
        // Not touchable + alpha ≤ 0.8 so Android passes every touch through to the game (Android 12+ rule).
        val lp = overlayParams(px, px, WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS).apply {
            gravity = Gravity.CENTER; alpha = 0.78f
        }
        windows.attach(CROSSHAIR, composeView {
            val cfg by app.settings.settings.collectAsState(s)
            CrosshairGlyph(cfg.crossStyle, cfg.crossColor)
        }, lp)
    }

    fun toggleCrosshair() { lifecycleScope.launch { app.settings.put(com.redcore.booster.data.SettingsRepository.K.cross, !app.settings.current().crosshair) } }

    // ------------------------------------------------------------------ barrage ticker (also holds keep-awake)

    private fun addTicker() {
        val lp = overlayParams(WindowManager.LayoutParams.MATCH_PARENT, (40 * dp).toInt(), WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE).apply {
            gravity = Gravity.TOP; y = (8 * dp).toInt(); alpha = 0.78f
        }
        windows.attach(TICKER, composeView { BarrageTicker() }, lp)
    }

    // ------------------------------------------------------------------ stopwatch

    fun toggleStopwatch() {
        if (windows.detach(STOPWATCH)) { stopwatchOpen.value = false; return }
        setExpanded(false)
        val lp = overlayParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, 0).apply {
            gravity = Gravity.TOP or Gravity.START; x = (24 * dp).toInt(); y = (64 * dp).toInt()
        }
        val ok = windows.attach(STOPWATCH, composeView {
            StopwatchWidget(
                onDrag = { dx, dy ->
                    windows.params(STOPWATCH)?.let { p -> p.x += dx.toInt(); p.y = (p.y + dy.toInt()).coerceAtLeast(0); clampToScreen(p); windows.update(STOPWATCH, p) }
                },
                onClose = { toggleStopwatch() },
                onAlarm = { app.boost.effects.haptics.tick(); toast("⏱ Timer finished") },
            )
        }, lp)
        stopwatchOpen.value = ok
    }

    // ------------------------------------------------------------------ screen hang-up

    /** The black window is added first; brightness is only lowered once Android has accepted it. */
    fun startHangup() {
        if (windows.isAttached(HANGUP)) return
        setExpanded(false)
        val lp = overlayParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val ok = windows.attach(HANGUP, composeView {
            val st by app.monitor.stats.collectAsState()
            val session by app.boost.session.collectAsState()
            HangupScreen(session?.label ?: "Game", st) { endHangup() }
        }, lp)
        if (!ok) return
        hangupOn.value = true
        lifecycleScope.launch {
            val r = app.boost.beginHangup()
            if (r.state != ActionState.APPLIED) toast(r.detail)
        }
    }

    fun endHangup() {
        windows.detach(HANGUP)
        hangupOn.value = false
        // App scope: this may run while the service is being destroyed.
        app.scope.launch { app.boost.endHangup() }
    }

    // ------------------------------------------------------------------ main window control used by the UI

    private fun screenSize(): Pair<Int, Int> {
        val d = getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
        val m = DisplayMetrics()
        @Suppress("DEPRECATION") d?.getRealMetrics(m)
        return if (m.widthPixels > 0) m.widthPixels to m.heightPixels else resources.displayMetrics.let { it.widthPixels to it.heightPixels }
    }

    private fun clampToScreen(p: WindowManager.LayoutParams) {
        val (w, h) = screenSize()
        p.x = p.x.coerceIn(0, (w - (48 * dp).toInt()).coerceAtLeast(0))
        p.y = p.y.coerceIn(0, (h - (48 * dp).toInt()).coerceAtLeast(0))
    }

    fun dragBy(dx: Float, dy: Float) {
        if (expanded.value || !windows.isAttached(MAIN)) return
        params.x = (params.x + dx).toInt()
        params.y = (params.y + dy).toInt().coerceAtLeast(0)
        windows.update(MAIN, params)
    }

    fun dragEnd() {
        if (expanded.value) return
        val v = windows.view(MAIN) ?: return
        snapToEdge(v)
        lifecycleScope.launch { app.settings.setBubble(params.x, params.y) }
    }

    private fun snapToEdge(v: View) {
        val screenW = screenSize().first
        params.x = if (params.x + v.width / 2 < screenW / 2) 0 else (screenW - v.width).coerceAtLeast(0)
        clampToScreen(params)
        windows.update(MAIN, params)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!::windows.isInitialized) return
        // Rotation / fold: keep the bubble on an edge and the stopwatch on screen.
        main.post {
            if (!expanded.value) windows.view(MAIN)?.let { snapToEdge(it) }
            windows.params(STOPWATCH)?.let { clampToScreen(it); windows.update(STOPWATCH, it) }
        }
    }

    fun setExpanded(e: Boolean) {
        if (!windows.isAttached(MAIN)) return
        if (expanded.value == e) return
        expanded.value = e
        if (e) {
            savedX = params.x; savedY = params.y
            params.width = WindowManager.LayoutParams.MATCH_PARENT
            params.height = WindowManager.LayoutParams.MATCH_PARENT
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            params.x = 0; params.y = 0
        } else {
            params.width = WindowManager.LayoutParams.WRAP_CONTENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS.inv()
            params.x = savedX; params.y = savedY
            clampToScreen(params)
        }
        windows.update(MAIN, params)
        setHandlesActive(!e)
        EdgeGuard.setPassThrough(this, e)
    }

    /** Keep-awake lives on the always-present ticker window (never 0×0), plus the main window. */
    private fun setKeepScreenOn(on: Boolean) {
        for (tag in listOf(TICKER, MAIN)) {
            val lp = windows.params(tag) ?: continue
            lp.flags = if (on) lp.flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON else lp.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON.inv()
            windows.update(tag, lp)
        }
    }

    fun screenshot() {
        if (!Permissions.granted(this, Perm.ACCESSIBILITY) || RedcoreAccessibilityService.instance == null) {
            requestPermission(Perm.ACCESSIBILITY, "Turn on \"REDCORE screenshot & triggers\" in Accessibility to use the screenshot button.")
            return
        }
        setExpanded(false)
        lifecycleScope.launch {
            val hidden = listOf(MAIN, TICKER).mapNotNull { windows.view(it) }
            hidden.forEach { it.visibility = View.INVISIBLE }
            delay(450)
            val ok = RedcoreAccessibilityService.takeScreenshot()
            delay(800)
            hidden.forEach { it.visibility = View.VISIBLE }
            if (!ok) toast(if (Build.VERSION.SDK_INT < 28) "Screenshot needs Android 9 or newer." else "Android didn't take the screenshot.")
        }
    }

    fun toggleRecording(mic: Boolean) {
        setExpanded(false)
        if (ScreenRecordService.recording.value) ScreenRecordService.stop(this)
        else ScreenRecordService.requestStart(this, mic)
    }

    fun openApp(dest: String? = null) {
        setExpanded(false)
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).apply {
            if (dest != null) putExtra(MainActivity.EXTRA_DEST, dest)
        })
    }

    /** Opens the system screen where the user can grant [p], then collapses the menu. */
    fun requestPermission(p: Perm, why: String) {
        toast(why)
        setExpanded(false)
        val i = Permissions.settingsIntent(this, p)
        if (i != null) runCatching { startActivity(i) }.onFailure { openApp("SETTINGS") } else openApp("SETTINGS")
    }

    fun endSession() {
        // App scope: the session must finish restoring even though this service stops right away.
        app.scope.launch { app.boost.endSession(EndReason.USER_ENDED) }
        stopSelf()
    }

    fun toast(msg: String) { main.post { Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show() } }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
            ACTION_END -> endSession()
            ACTION_OPEN -> setExpanded(true)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        opsListener?.let { l -> runCatching { getSystemService(AppOpsManager::class.java)?.stopWatchingMode(l) } }
        if (::windows.isInitialized) {
            if (windows.isAttached(HANGUP)) endHangup()
            windows.detachAll()
            EdgeGuard.setPassThrough(this, false)
        }
        app.boost.effects.overlayVisible = false
        running.value = false
        _windows.value = emptySet()
        viewModelStore.clear()
        super.onDestroy()
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val menu = PendingIntent.getService(this, 3, Intent(this, OverlayService::class.java).setAction(ACTION_OPEN), PendingIntent.FLAG_IMMUTABLE)
        val hide = PendingIntent.getService(this, 1, Intent(this, OverlayService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val end = PendingIntent.getService(this, 2, Intent(this, OverlayService::class.java).setAction(ACTION_END), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, RedcoreApp.CH_SESSION)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("REDCORE gaming toolkit")
            .setContentText("Swipe in from the left or right edge to open the game menu")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setColor(0xFFFF1E3C.toInt())
            .addAction(0, "Open menu", menu)
            .addAction(0, "Hide", hide)
            .addAction(0, "End session", end)
            .build()
    }

    companion object {
        private const val NOTIF_ID = 41
        private const val ACTION_STOP = "stop"
        private const val ACTION_END = "end"
        private const val ACTION_OPEN = "open"
        const val MAIN = "main"
        const val TICKER = "ticker"
        const val CROSSHAIR = "crosshair"
        const val STOPWATCH = "stopwatch"
        const val HANGUP = "hangup"
        const val HANDLE_L = "handle_left"
        const val HANDLE_R = "handle_right"

        /** True only while the main game-menu window is actually attached. */
        val running = MutableStateFlow(false)
        private val _windows = MutableStateFlow<Set<String>>(emptySet())
        /** Overlay windows Android has accepted right now. */
        val windowsShown: StateFlow<Set<String>> = _windows.asStateFlow()

        fun start(c: Context): Boolean = runCatching {
            ContextCompat.startForegroundService(c, Intent(c, OverlayService::class.java)); true
        }.getOrDefault(false)

        fun stop(c: Context) = c.stopService(Intent(c, OverlayService::class.java))
    }
}
