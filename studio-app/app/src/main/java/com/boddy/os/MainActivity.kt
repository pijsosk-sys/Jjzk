package com.boddy.os

import android.Manifest
import android.app.AlertDialog
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Launch menu: installs Linux on first run, then launches Roblox Studio; copies the logs when it fails. */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val status = findViewById<TextView>(R.id.status)
        val progress = findViewById<ProgressBar>(R.id.progress)
        val action = findViewById<Button>(R.id.action)
        val menu = findViewById<View>(R.id.menu)
        val reset = findViewById<TextView>(R.id.reset)
        val logToggle = findViewById<TextView>(R.id.logToggle)
        val log = findViewById<TextView>(R.id.log)
        val logScroll = findViewById<ScrollView>(R.id.logScroll)

        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)

        if (Boddy.phase.value == Boddy.Phase.NEEDS_INSTALL && BoddyService.isInstalled(this)) {
            Boddy.phase.value = Boddy.Phase.READY
        }

        action.setOnClickListener {
            when (Boddy.phase.value) {
                Boddy.Phase.NEEDS_INSTALL, Boddy.Phase.ERROR -> BoddyService.send(this, BoddyService.ACTION_INSTALL)
                Boddy.Phase.READY, Boddy.Phase.RUNNING -> DesktopActivity.open(this, "studio-open")
                Boddy.Phase.INSTALLING -> Unit
            }
        }
        findViewById<Button>(R.id.studioMenu).setOnClickListener { DesktopActivity.open(this, "studio") }
        findViewById<Button>(R.id.desktop).setOnClickListener { DesktopActivity.open(this, null) }
        findViewById<Button>(R.id.copyLog).setOnClickListener { copyLog() }
        findViewById<Button>(R.id.shareLog).setOnClickListener {
            lifecycleScope.launch {
                try {
                    val text = withContext(Dispatchers.IO) { LogReport.build(this@MainActivity) }
                    LogReport.share(this@MainActivity, text)
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Couldn't share the log: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        logToggle.setOnClickListener { setLogVisible(logScroll.visibility != View.VISIBLE, logScroll, logToggle) }

        reset.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Reinstall Linux?")
                .setMessage("This deletes Linux and the Roblox Studio install inside it, then downloads everything again.")
                .setPositiveButton("Reinstall") { _, _ ->
                    BoddyService.send(this, BoddyService.ACTION_STOP)
                    lifecycleScope.launch {
                        Boddy.status.value = "Removing the old system…"
                        withContext(Dispatchers.IO) { Installer(this@MainActivity, Proot(this@MainActivity)).reset() }
                        Boddy.phase.value = Boddy.Phase.NEEDS_INSTALL
                        BoddyService.send(this@MainActivity, BoddyService.ACTION_INSTALL)
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(Boddy.phase, Boddy.status, Boddy.progress) { p, s, pr -> Triple(p, s, pr) }
                        .collect { (phase, text, pct) ->
                            val ready = phase == Boddy.Phase.READY || phase == Boddy.Phase.RUNNING
                            status.text = if (ready && text.isEmpty() || phase == Boddy.Phase.RUNNING) {
                                defaultStatus(phase)
                            } else {
                                text.ifEmpty { defaultStatus(phase) }
                            }
                            action.isEnabled = phase != Boddy.Phase.INSTALLING
                            action.text = when (phase) {
                                Boddy.Phase.NEEDS_INSTALL -> if (hasSystem()) "Update" else "Install"
                                Boddy.Phase.INSTALLING -> "Installing…"
                                Boddy.Phase.ERROR -> "Retry install"
                                Boddy.Phase.READY -> "Launch Roblox Studio"
                                Boddy.Phase.RUNNING -> "Back to Roblox Studio"
                            }
                            menu.visibility = if (ready) View.VISIBLE else View.GONE
                            reset.visibility =
                                if (phase == Boddy.Phase.READY || phase == Boddy.Phase.ERROR) View.VISIBLE else View.GONE
                            progress.visibility = if (phase == Boddy.Phase.INSTALLING) View.VISIBLE else View.GONE
                            progress.isIndeterminate = pct < 0
                            if (pct >= 0) progress.progress = pct
                            if (phase == Boddy.Phase.ERROR) setLogVisible(true, logScroll, logToggle)
                            if (phase == Boddy.Phase.INSTALLING) {
                                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            } else {
                                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            }
                        }
                }
                launch {
                    Boddy.logVersion.collect { // StateFlow skips values while we wait below
                        log.text = Boddy.logText()
                        logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
                        delay(200)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        showKeepAlive()
        CrashLog.peek(this)?.let { report ->
            AlertDialog.Builder(this)
                .setTitle("Studio Launcher hit a problem last time")
                .setMessage("Tap Copy log, then paste it into the chat to get it fixed.\n\n" +
                    report.lines().take(6).joinToString("\n"))
                .setPositiveButton("Copy log") { _, _ ->
                    copyLog()
                    CrashLog.take(this)
                }
                .setNegativeButton("Close") { _, _ -> CrashLog.take(this)?.let { Boddy.log(it) } }
                .show()
        }
    }

    private fun copyLog() {
        lifecycleScope.launch {
            try {
                val text = withContext(Dispatchers.IO) { LogReport.build(this@MainActivity) }
                LogReport.copy(this@MainActivity, text)
                Toast.makeText(this@MainActivity, "Log copied (${text.lines().size} lines). Paste it in the chat.",
                    Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Couldn't copy the log: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Settings Android uses to kill the app in the background, with one-tap fixes. Hidden when all good. */
    private fun showKeepAlive() {
        val card = findViewById<android.widget.LinearLayout>(R.id.keepAlive)
        card.removeAllViews()
        if (KeepAlive.allGood(this)) {
            card.visibility = View.GONE
            return
        }
        card.visibility = View.VISIBLE
        fun text(value: String, size: Float, color: Int) = TextView(this).apply {
            text = value
            textSize = size
            setTextColor(getColor(color))
            card.addView(this)
        }
        fun fixButton(label: String, onClick: () -> Unit) = Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(getColor(R.color.boddy_text))
            setBackgroundResource(R.drawable.button_primary)
            setOnClickListener { onClick() }
            card.addView(this, android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, (48 * resources.displayMetrics.density).toInt(),
            ).apply { topMargin = 12 })
        }
        text("Stop Android closing Roblox Studio", 17f, R.color.boddy_text)
        text("Studio runs many processes. Without these, Android stops it after a few minutes.", 13f, R.color.boddy_text_dim)
        if (!KeepAlive.batteryUnrestricted(this)) {
            fixButton("1. Let Studio Launcher run in the background") { KeepAlive.fixBattery(this) }
        }
        if (KeepAlive.childProcessLimitOff(this) == false) {
            fixButton("2. Turn off the background process limit") {
                AlertDialog.Builder(this)
                    .setTitle("Turn off the process limit")
                    .setMessage("In Developer options, turn ON \"Disable child process restrictions\".\n\n" +
                        "No Developer options? Go to Settings → About phone → Software information and tap " +
                        "Build number 7 times, then come back here.")
                    .setPositiveButton("Open settings") { _, _ -> KeepAlive.openDeveloperOptions(this) }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    private fun hasSystem() = Installer(this, Proot(this)).hasSystem()

    private fun setLogVisible(visible: Boolean, logScroll: View, toggle: TextView) {
        logScroll.visibility = if (visible) View.VISIBLE else View.GONE
        toggle.text = if (visible) "Hide details" else "Show details"
    }

    private fun defaultStatus(phase: Boddy.Phase) = when (phase) {
        Boddy.Phase.NEEDS_INSTALL -> if (hasSystem()) {
            "A new version is ready. Updating keeps your Roblox Studio install."
        } else {
            "Runs the real Windows Roblox Studio on your phone through compatibility software. " +
                "This is experimental: it may not start yet.\n\n" +
                "Stay on Wi-Fi: the first install downloads Linux (under 1 GB), then Studio's first launch " +
                "downloads several GB more."
        }
        Boddy.Phase.READY -> "Ready. Close the Boddy app first if it's running."
        Boddy.Phase.RUNNING -> "Running. If Studio stops, come back here and tap Copy log."
        else -> ""
    }
}
