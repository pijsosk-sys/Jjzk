package com.redcore.booster.overlay

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
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
import com.redcore.booster.booster.Perm
import com.redcore.booster.booster.Permissions
import com.redcore.booster.data.AppSettings
import com.redcore.booster.record.ScreenRecordService
import com.redcore.booster.service.RedcoreAccessibilityService
import com.redcore.booster.ui.theme.RedcoreTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Hosts all in-game windows: the HUD/menu, swipe handles, crosshair, stopwatch, barrage ticker and
 * screen hang-up. Compose needs lifecycle/saved-state/view-model owners, which this service provides.
 */
class OverlayService : LifecycleService(), SavedStateRegistryOwner, ViewModelStoreOwner {
    private val ssrc = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = ssrc.savedStateRegistry
    override val viewModelStore = ViewModelStore()

    private lateinit var wm: WindowManager
    private var view: ComposeView? = null
    private lateinit var params: WindowManager.LayoutParams
    val expanded = MutableStateFlow(false)
    val stopwatchOpen = MutableStateFlow(false)
    val hangupOn = MutableStateFlow(false)

    private val handles = mutableListOf<View>()
    private var handleKey = ""
    private var crossView: ComposeView? = null
    private var crossKey = ""
    private var swView: ComposeView? = null
    private var swParams: WindowManager.LayoutParams? = null
    private var tickerView: ComposeView? = null
    private var hangView: ComposeView? = null
    private var hangSavedBrightness: Int? = null
    private var hangSavedAuto = false

    private val dp get() = resources.displayMetrics.density

    override fun onCreate() {
        ssrc.performAttach()
        ssrc.performRestore(null)
        super.onCreate()
        // startForeground must always run first: a started foreground service that never calls it crashes the app.
        ServiceCompat.startForeground(this, NOTIF_ID, notification(), if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        wm = getSystemService(WindowManager::class.java)
        running.value = true
        lifecycleScope.launch { addOverlay() }
        lifecycleScope.launch { app.boost.session.collect { s -> setKeepScreenOn(s?.spec?.keepScreenOn == true) } }
        lifecycleScope.launch { app.settings.settings.collect { applySettings(it) } }
        lifecycleScope.launch { app.boost.effects.events.collect { toast(it) } }
        addTicker()
    }

    private fun overlayParams(w: Int, h: Int, flags: Int) = WindowManager.LayoutParams(
        w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or flags,
        PixelFormat.TRANSLUCENT
    ).apply { if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES }

    private fun composeWindow(lp: WindowManager.LayoutParams, content: @Composable () -> Unit): ComposeView {
        val cv = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setViewTreeViewModelStoreOwner(this@OverlayService)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setContent { RedcoreTheme { content() } }
        }
        runCatching { wm.addView(cv, lp) }
        return cv
    }

    private fun removeWindow(v: View?) { if (v != null) runCatching { wm.removeView(v) } }

    private suspend fun addOverlay() {
        val s = app.settings.settings.first()
        params = overlayParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, 0).apply {
            gravity = Gravity.TOP or Gravity.START
            x = s.bubbleX; y = s.bubbleY
        }
        view = composeWindow(params) { OverlayRoot(this@OverlayService) }
    }

    private fun applySettings(s: AppSettings) {
        updateHandles(s)
        updateCrosshair(s)
    }

    // ------------------------------------------------------------------ swipe-to-open handles

    private fun updateHandles(s: AppSettings) {
        val key = "${s.swipeSide}${s.handleHint}"
        if (key == handleKey && handles.isNotEmpty() == s.swipeSide) return
        handleKey = key
        handles.forEach { removeWindow(it) }; handles.clear()
        fun add(dir: Int, gravity: Int, w: Int, h: Int, x: Int, y: Int) {
            val v = SwipeHandleView(this, dir, s.handleHint) { if (!expanded.value && !hangupOn.value) setExpanded(true) }
            val lp = overlayParams((w * dp).toInt(), (h * dp).toInt(), WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS).apply {
                this.gravity = gravity; this.x = (x * dp).toInt(); this.y = (y * dp).toInt()
            }
            if (Build.VERSION.SDK_INT >= 29 && dir != SwipeHandleView.DOWN) {
                // Ask Android not to treat swipes on this small strip as the system Back gesture.
                v.addOnLayoutChangeListener { vv, l, t, r, b, _, _, _, _ -> vv.systemGestureExclusionRects = listOf(Rect(0, 0, r - l, b - t)) }
            }
            runCatching { wm.addView(v, lp); handles += v }
        }
        // Side edges only: swipe inward from the upper part of the left or right edge.
        if (s.swipeSide) {
            add(SwipeHandleView.RIGHT, Gravity.START or Gravity.TOP, 18, 160, 0, 56)
            add(SwipeHandleView.LEFT, Gravity.END or Gravity.TOP, 18, 160, 0, 56)
        }
        setHandlesActive(!expanded.value)
    }

    private fun setHandlesActive(active: Boolean) {
        handles.forEach { v ->
            val lp = v.layoutParams as? WindowManager.LayoutParams ?: return@forEach
            v.visibility = if (active) View.VISIBLE else View.INVISIBLE
            lp.flags = if (active) lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv() else lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            runCatching { wm.updateViewLayout(v, lp) }
        }
    }

    // ------------------------------------------------------------------ crosshair

    private fun updateCrosshair(s: AppSettings) {
        val key = "${s.crosshair}${s.crossSize}"
        if (key == crossKey) return
        crossKey = key
        removeWindow(crossView); crossView = null
        if (!s.crosshair) return
        val px = ((36 + 64 * s.crossSize) * dp).toInt()
        // Not touchable + alpha ≤ 0.8 so Android passes every touch through to the game (Android 12+ rule).
        val lp = overlayParams(px, px, WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS).apply {
            gravity = Gravity.CENTER; alpha = 0.78f
        }
        crossView = composeWindow(lp) {
            val cfg by app.settings.settings.collectAsState(s)
            CrosshairGlyph(cfg.crossStyle, cfg.crossColor)
        }
    }

    fun toggleCrosshair() { lifecycleScope.launch { app.settings.put(com.redcore.booster.data.SettingsRepository.K.cross, !app.settings.current().crosshair) } }

    // ------------------------------------------------------------------ barrage ticker

    private fun addTicker() {
        val lp = overlayParams(WindowManager.LayoutParams.MATCH_PARENT, (40 * dp).toInt(), WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE).apply {
            gravity = Gravity.TOP; y = (8 * dp).toInt(); alpha = 0.78f
        }
        tickerView = composeWindow(lp) { BarrageTicker() }
    }

    // ------------------------------------------------------------------ stopwatch

    fun toggleStopwatch() {
        if (swView != null) { removeWindow(swView); swView = null; swParams = null; stopwatchOpen.value = false; return }
        setExpanded(false)
        val lp = overlayParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, 0).apply {
            gravity = Gravity.TOP or Gravity.START; x = (24 * dp).toInt(); y = (64 * dp).toInt()
        }
        swParams = lp
        swView = composeWindow(lp) {
            StopwatchWidget(
                onDrag = { dx, dy -> swParams?.let { p -> p.x += dx.toInt(); p.y = (p.y + dy.toInt()).coerceAtLeast(0); runCatching { wm.updateViewLayout(swView, p) } } },
                onClose = { toggleStopwatch() },
                onAlarm = { app.boost.effects.haptics.tick(); toast("⏱ Timer finished") },
            )
        }
        stopwatchOpen.value = true
    }

    // ------------------------------------------------------------------ screen hang-up

    fun startHangup() {
        if (hangView != null) return
        setExpanded(false)
        val lp = overlayParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val boost = app.boost
        if (boost.canSetBrightness()) {
            hangSavedBrightness = boost.currentBrightnessPct(); hangSavedAuto = boost.isAutoBrightness()
            boost.setBrightness(1)
        }
        hangView = composeWindow(lp) {
            val st by app.monitor.stats.collectAsState()
            val session by app.boost.session.collectAsState()
            HangupScreen(session?.label ?: "Game", st) { endHangup() }
        }
        hangupOn.value = true
    }

    fun endHangup() {
        removeWindow(hangView); hangView = null
        val boost = app.boost
        hangSavedBrightness?.let { boost.setBrightness(it) }
        if (hangSavedAuto) boost.setAutoBrightness(true)
        hangSavedBrightness = null
        hangupOn.value = false
    }

    // ------------------------------------------------------------------ main window control used by the UI

    fun dragBy(dx: Float, dy: Float) {
        val v = view ?: return
        if (expanded.value) return
        params.x = (params.x + dx).toInt()
        params.y = (params.y + dy).toInt().coerceAtLeast(0)
        runCatching { wm.updateViewLayout(v, params) }
    }

    fun dragEnd() {
        if (expanded.value) return
        val v = view ?: return
        val screenW = resources.displayMetrics.widthPixels
        params.x = if (params.x + v.width / 2 < screenW / 2) 0 else screenW - v.width
        runCatching { wm.updateViewLayout(v, params) }
        lifecycleScope.launch { app.settings.setBubble(params.x, params.y) }
    }

    fun setExpanded(e: Boolean) {
        val v = view ?: return
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
        }
        runCatching { wm.updateViewLayout(v, params) }
        setHandlesActive(!e)
        EdgeGuard.setPassThrough(this, e)
    }

    private var savedX = 0
    private var savedY = 0

    private fun setKeepScreenOn(on: Boolean) {
        val v = view ?: return
        params.flags = if (on) params.flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON else params.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON.inv()
        runCatching { wm.updateViewLayout(v, params) }
    }

    fun screenshot() {
        if (!Permissions.granted(this, Perm.ACCESSIBILITY) || RedcoreAccessibilityService.instance == null) {
            requestPermission(Perm.ACCESSIBILITY, "Turn on \"REDCORE screenshot & triggers\" in Accessibility to use the screenshot button.")
            return
        }
        setExpanded(false)
        lifecycleScope.launch {
            view?.visibility = View.INVISIBLE
            tickerView?.visibility = View.INVISIBLE
            delay(450)
            val ok = RedcoreAccessibilityService.takeScreenshot()
            delay(800)
            view?.visibility = View.VISIBLE
            tickerView?.visibility = View.VISIBLE
            if (!ok) toast("Screenshot needs Android 9 or newer.")
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
        if (i != null) runCatching { startActivity(i) } else openApp("TOOLS")
    }

    fun endSession() {
        lifecycleScope.launch {
            app.boost.endSession()
            stopSelf()
        }
    }

    fun toast(msg: String) = Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()

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
        if (hangView != null) endHangup()
        listOf(view, crossView, swView, tickerView).forEach { removeWindow(it) }
        handles.forEach { removeWindow(it) }; handles.clear()
        view = null; crossView = null; swView = null; tickerView = null
        if (::wm.isInitialized) EdgeGuard.setPassThrough(this, false)
        running.value = false
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
        val running = MutableStateFlow(false)

        fun start(c: Context) = ContextCompat.startForegroundService(c, Intent(c, OverlayService::class.java))
        fun stop(c: Context) = c.stopService(Intent(c, OverlayService::class.java))
    }
}
