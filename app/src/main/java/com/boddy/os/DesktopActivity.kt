package com.boddy.os

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.boddy.os.vnc.KeySyms
import com.boddy.os.vnc.RfbClient
import com.boddy.os.vnc.VncView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Full-screen Linux desktop with a touchpad-style mouse and an extra-keys bar. */
class DesktopActivity : AppCompatActivity() {

    private lateinit var vnc: VncView
    private lateinit var overlay: TextView
    private var client: RfbClient? = null
    private val modifierButtons = mutableMapOf<Int, TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_desktop)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        vnc = findViewById(R.id.vnc)
        overlay = findViewById(R.id.overlay)
        vnc.onStickyConsumed = { refreshModifiers() }
        vnc.onDisconnected = { error ->
            overlay.visibility = View.VISIBLE
            overlay.text = "Desktop closed" + (error?.message?.let { "\n$it" } ?: "")
        }
        buildKeyBar(findViewById(R.id.keys))

        // Linux asked to open a link (e.g. Claude sign-in): use the phone's own browser.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                Boddy.openOnPhone.collect { url ->
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(this@DesktopActivity, "No browser found", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = confirmExit()
        })

        // Size the Linux screen to match the phone's shape once we know the view size.
        vnc.post {
            val desktopWidth = minOf(DESKTOP_WIDTH, vnc.width)
            val desktopHeight = (vnc.height.toFloat() * desktopWidth / vnc.width).roundToInt() and 1.inv()
            BoddyService.send(this, BoddyService.ACTION_START) {
                putExtra(BoddyService.EXTRA_WIDTH, desktopWidth)
                putExtra(BoddyService.EXTRA_HEIGHT, desktopHeight)
            }
            connect()
        }
    }

    private fun connect() {
        lifecycleScope.launch(Dispatchers.IO) {
            // The desktop takes a few seconds to come up the first time.
            repeat(120) {
                val c = RfbClient(vnc)
                try {
                    c.connect("127.0.0.1", 5901)
                    client = c
                    launch(Dispatchers.Main) {
                        vnc.client = c
                        overlay.visibility = View.GONE
                        vnc.requestFocus()
                        runRequestedApp()
                    }
                    c.run()
                    return@launch
                } catch (_: Exception) {
                    c.close()
                    delay(500)
                }
            }
            launch(Dispatchers.Main) { overlay.text = "Couldn't start the desktop. Check the log on the main screen." }
        }
    }

    /** Opens the app MainActivity asked for (e.g. "Sign in to Claude"), once. */
    private fun runRequestedApp() {
        val app = intent.getStringExtra(EXTRA_RUN) ?: return
        intent.removeExtra(EXTRA_RUN)
        BoddyService.send(this, BoddyService.ACTION_RUN) { putExtra(BoddyService.EXTRA_APP, app) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (vnc.client != null) runRequestedApp()
    }

    private fun buildKeyBar(bar: LinearLayout) {
        fun key(label: String, onClick: (TextView) -> Unit): TextView = TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 15f
            gravity = android.view.Gravity.CENTER
            minWidth = (44 * resources.displayMetrics.density).toInt()
            setPadding(24, 0, 24, 0)
            setBackgroundResource(android.R.drawable.list_selector_background)
            setOnClickListener { onClick(this) }
            bar.addView(this, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT))
        }
        fun sendKey(label: String, keysym: Int) = key(label) { vnc.tapKey(keysym) }
        fun modifier(label: String, keysym: Int) {
            modifierButtons[keysym] = key(label) {
                if (!vnc.stickyModifiers.remove(keysym)) vnc.stickyModifiers += keysym
                refreshModifiers()
            }
        }

        key("⌨") { vnc.toggleKeyboard() }
        key("Mouse: touchpad") {
            vnc.touchpadMode = !vnc.touchpadMode
            it.text = if (vnc.touchpadMode) "Mouse: touchpad" else "Mouse: direct touch"
        }
        key("Paste") {
            if (!vnc.pasteFromPhone()) Toast.makeText(this, "Phone clipboard is empty", Toast.LENGTH_SHORT).show()
        }
        sendKey("Esc", KeySyms.ESCAPE)
        sendKey("Tab", KeySyms.TAB)
        modifier("Ctrl", KeySyms.CONTROL_L)
        modifier("Alt", KeySyms.ALT_L)
        modifier("Shift", KeySyms.SHIFT_L)
        sendKey("⊞", KeySyms.SUPER_L)
        sendKey("←", KeySyms.LEFT)
        sendKey("↑", KeySyms.UP)
        sendKey("↓", KeySyms.DOWN)
        sendKey("→", KeySyms.RIGHT)
        sendKey("Home", KeySyms.HOME)
        sendKey("End", KeySyms.END)
        sendKey("PgUp", KeySyms.PAGE_UP)
        sendKey("PgDn", KeySyms.PAGE_DOWN)
        sendKey("Del", KeySyms.DELETE)
        for (i in 1..12) sendKey("F$i", KeySyms.F1 + i - 1)
        key("⏻") { confirmExit() }
    }

    private fun refreshModifiers() {
        modifierButtons.forEach { (sym, view) ->
            view.setTextColor(if (sym in vnc.stickyModifiers) Color.parseColor("#A29BFE") else Color.WHITE)
        }
    }

    private fun confirmExit() {
        AlertDialog.Builder(this)
            .setTitle("Leave Boddy?")
            .setItems(arrayOf("Keep running in background", "Shut down Linux")) { _, which ->
                if (which == 1) BoddyService.send(this, BoddyService.ACTION_STOP)
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroy() {
        client?.close()
        super.onDestroy()
    }

    companion object {
        /** Linux screen width in pixels; the height follows the phone's aspect ratio. */
        private const val DESKTOP_WIDTH = 1600
        private const val EXTRA_RUN = "run"

        fun open(context: Context, runApp: String? = null) {
            context.startActivity(Intent(context, DesktopActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .apply { if (runApp != null) putExtra(EXTRA_RUN, runApp) })
        }
    }
}
