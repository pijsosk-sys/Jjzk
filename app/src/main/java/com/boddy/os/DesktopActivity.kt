package com.boddy.os

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
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
    /** True while restarting at a new size, so the old connection closing isn't shown as an error. */
    private var resizing = false

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
            if (!resizing && !isFinishing) {
                // Boddy's watchdog restarts a desktop that died, so just reconnect.
                overlay.visibility = View.VISIBLE
                overlay.text = "Reconnecting to the desktop…" + (error?.message?.let { "\n($it)" } ?: "")
                vnc.client = null
                connect(initialDelayMs = 2000)
            }
        }
        buildKeyBar(findViewById(R.id.keys))

        // Linux asked the phone to do something (open a sign-in link, a Minecraft add-on, an app...).
        val phone = PhoneActions(this)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                Boddy.phoneRequests.collect { request ->
                    // A bad request (missing file, odd link) must never take the desktop down.
                    try {
                        when (request) {
                            is PhoneRequest.OpenLink -> phone.openLink(this@DesktopActivity, request.url)
                            is PhoneRequest.OpenFile -> phone.openFile(this@DesktopActivity, request.path)
                            is PhoneRequest.LaunchApp -> phone.launchApp(this@DesktopActivity, request.name, request.split)
                        }
                    } catch (e: Exception) {
                        CrashLog.record(this@DesktopActivity, e, fatal = false)
                        Toast.makeText(this@DesktopActivity, "Couldn't do that: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = confirmExit()
        })

        vnc.post { startDesktop(BoddyService.ACTION_START) }
    }

    /** Starts (or restarts) Linux at the chosen size, shaped like the phone's screen, then connects. */
    private fun startDesktop(action: String) {
        val desktopWidth = minOf(Settings(this).size.desktopWidth, vnc.width)
        val desktopHeight = (vnc.height.toFloat() * desktopWidth / vnc.width).roundToInt() and 1.inv()
        BoddyService.send(this, action) {
            putExtra(BoddyService.EXTRA_WIDTH, desktopWidth)
            putExtra(BoddyService.EXTRA_HEIGHT, desktopHeight)
        }
        connect(initialDelayMs = if (action == BoddyService.ACTION_RESTART) 1500 else 0)
    }

    private fun chooseSize() {
        val settings = Settings(this)
        val sizes = Settings.Size.entries
        AlertDialog.Builder(this)
            .setTitle("Desktop size")
            .setSingleChoiceItems(sizes.map { it.label }.toTypedArray(), sizes.indexOf(settings.size)) { dialog, which ->
                dialog.dismiss()
                if (sizes[which] == settings.size) return@setSingleChoiceItems
                AlertDialog.Builder(this)
                    .setMessage("Changing the size restarts the desktop. Save your work first.")
                    .setPositiveButton("Restart") { _, _ ->
                        settings.size = sizes[which]
                        client?.close()
                        vnc.client = null
                        overlay.visibility = View.VISIBLE
                        overlay.text = "Resizing…"
                        resizing = true
                        startDesktop(BoddyService.ACTION_RESTART)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .show()
    }

    private fun connect(initialDelayMs: Long = 0) {
        lifecycleScope.launch(Dispatchers.IO) {
            delay(initialDelayMs)
            // The desktop takes a few seconds to come up the first time.
            repeat(120) {
                val c = RfbClient(vnc)
                try {
                    c.connect("127.0.0.1", 5901)
                    client = c
                    launch(Dispatchers.Main) {
                        vnc.client = c
                        resizing = false
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
            launch(Dispatchers.Main) {
                overlay.text = "The desktop isn't running.\n\nTap to start it again"
                overlay.setOnClickListener {
                    overlay.setOnClickListener(null)
                    overlay.text = "Starting Boddy…"
                    startDesktop(BoddyService.ACTION_START)
                }
            }
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
        key("Size") { chooseSize() }
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
        private const val EXTRA_RUN = "run"

        fun open(context: Context, runApp: String? = null) {
            context.startActivity(Intent(context, DesktopActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .apply { if (runApp != null) putExtra(EXTRA_RUN, runApp) })
        }
    }
}
