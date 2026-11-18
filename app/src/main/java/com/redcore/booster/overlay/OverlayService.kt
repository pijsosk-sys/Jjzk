package com.redcore.booster.overlay

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Toast
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
import com.redcore.booster.record.ScreenRecordService
import com.redcore.booster.service.RedcoreAccessibilityService
import com.redcore.booster.ui.theme.RedcoreTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Hosts the floating toolkit (bubble + HUD + expanded panel) in a TYPE_APPLICATION_OVERLAY window.
 * Compose needs lifecycle/saved-state/view-model owners, which this service provides.
 */
class OverlayService : LifecycleService(), SavedStateRegistryOwner, ViewModelStoreOwner {
    private val ssrc = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = ssrc.savedStateRegistry
    override val viewModelStore = ViewModelStore()

    private lateinit var wm: WindowManager
    private var view: ComposeView? = null
    private lateinit var params: WindowManager.LayoutParams
    val expanded = MutableStateFlow(false)

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
        // Keep-screen-on follows the active session's mode.
        lifecycleScope.launch {
            app.boost.session.collect { s -> setKeepScreenOn(s?.spec?.keepScreenOn == true) }
        }
    }

    private suspend fun addOverlay() {
        val s = app.settings.settings.first()
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = s.bubbleX; y = s.bubbleY
            if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        val cv = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setViewTreeViewModelStoreOwner(this@OverlayService)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setContent { RedcoreTheme { OverlayRoot(this@OverlayService) } }
        }
        view = cv
        wm.addView(cv, params)
    }

    // ------------------------------------------------------------------ window control used by the UI

    fun dragBy(dx: Float, dy: Float) {
        val v = view ?: return
        if (expanded.value) return
        params.x = (params.x + dx).toInt()
        params.y = (params.y + dy).toInt().coerceAtLeast(0)
        runCatching { wm.updateViewLayout(v, params) }
    }

    fun dragEnd() {
        if (expanded.value) return
        // Snap to the nearest screen edge, like a system bubble.
        val v = view ?: return
        val screenW = resources.displayMetrics.widthPixels
        params.x = if (params.x + v.width / 2 < screenW / 2) 0 else screenW - v.width
        runCatching { wm.updateViewLayout(v, params) }
        lifecycleScope.launch { app.settings.setBubble(params.x, params.y) }
    }

    fun setExpanded(e: Boolean) {
        val v = view ?: return
        expanded.value = e
        if (e) {
            params.width = WindowManager.LayoutParams.MATCH_PARENT
            params.height = WindowManager.LayoutParams.MATCH_PARENT
            savedX = params.x; savedY = params.y
            params.x = 0; params.y = 0
        } else {
            params.width = WindowManager.LayoutParams.WRAP_CONTENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            params.x = savedX; params.y = savedY
        }
        runCatching { wm.updateViewLayout(v, params) }
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
            toast("Enable \"REDCORE screenshot shortcut\" in Accessibility settings first.")
            Permissions.settingsIntent(this, Perm.ACCESSIBILITY)?.let { runCatching { startActivity(it) } }
            setExpanded(false)
            return
        }
        setExpanded(false)
        lifecycleScope.launch {
            view?.visibility = View.INVISIBLE
            delay(450)
            val ok = RedcoreAccessibilityService.takeScreenshot()
            delay(800)
            view?.visibility = View.VISIBLE
            if (!ok) toast("Screenshot needs Android 9 or newer.")
        }
    }

    fun toggleRecording(mic: Boolean) {
        setExpanded(false)
        if (ScreenRecordService.recording.value) ScreenRecordService.stop(this)
        else ScreenRecordService.requestStart(this, mic)
    }

    fun openApp() {
        setExpanded(false)
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        running.value = false
        viewModelStore.clear()
        super.onDestroy()
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val hide = PendingIntent.getService(this, 1, Intent(this, OverlayService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val end = PendingIntent.getService(this, 2, Intent(this, OverlayService::class.java).setAction(ACTION_END), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, RedcoreApp.CH_SESSION)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("REDCORE gaming toolkit")
            .setContentText("Tap the red bubble for quick controls")
            .setOngoing(true)
            .setContentIntent(open)
            .setColor(0xFFFF1E3C.toInt())
            .addAction(0, "Hide toolkit", hide)
            .addAction(0, "End session", end)
            .build()
    }

    companion object {
        private const val NOTIF_ID = 41
        private const val ACTION_STOP = "stop"
        private const val ACTION_END = "end"
        val running = MutableStateFlow(false)

        fun start(c: Context) = ContextCompat.startForegroundService(c, Intent(c, OverlayService::class.java))
        fun stop(c: Context) = c.stopService(Intent(c, OverlayService::class.java))
    }
}
