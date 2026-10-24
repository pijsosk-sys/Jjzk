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
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Home screen: installs Linux on first run, then opens the desktop and its apps. */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val status = findViewById<TextView>(R.id.status)
        val progress = findViewById<ProgressBar>(R.id.progress)
        val action = findViewById<Button>(R.id.action)
        val shortcuts = findViewById<View>(R.id.shortcuts)
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
                Boddy.Phase.READY, Boddy.Phase.RUNNING -> DesktopActivity.open(this)
                Boddy.Phase.INSTALLING -> Unit
            }
        }
        findViewById<Button>(R.id.signin).setOnClickListener { DesktopActivity.open(this, "claude-signin") }
        findViewById<Button>(R.id.claudeCode).setOnClickListener { DesktopActivity.open(this, "claude-code") }
        findViewById<Button>(R.id.deepseek).setOnClickListener { DesktopActivity.open(this, "deepseek-code") }

        logToggle.setOnClickListener { setLogVisible(logScroll.visibility != View.VISIBLE, logScroll, logToggle) }

        reset.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Reinstall Linux?")
                .setMessage("This deletes everything inside Boddy (apps, files, sign-ins) and downloads it again.")
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
                                Boddy.Phase.NEEDS_INSTALL -> if (hasSystem()) "Update Boddy" else "Install Boddy"
                                Boddy.Phase.INSTALLING -> "Installing…"
                                Boddy.Phase.ERROR -> "Retry install"
                                Boddy.Phase.READY -> "Open desktop"
                                Boddy.Phase.RUNNING -> "Back to desktop"
                            }
                            shortcuts.visibility = if (ready) View.VISIBLE else View.GONE
                            reset.visibility =
                                if (phase == Boddy.Phase.READY || phase == Boddy.Phase.ERROR) View.VISIBLE else View.GONE
                            progress.visibility = if (phase == Boddy.Phase.INSTALLING) View.VISIBLE else View.GONE
                            progress.isIndeterminate = pct < 0
                            if (pct >= 0) progress.progress = pct
                            // Errors are easier to report with the details open.
                            if (phase == Boddy.Phase.ERROR) setLogVisible(true, logScroll, logToggle)
                            // The install can take a while; don't let the screen sleep mid-way.
                            if (phase == Boddy.Phase.INSTALLING) {
                                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            } else {
                                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            }
                        }
                }
                launch {
                    Boddy.log.collect {
                        log.text = it
                        logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
                    }
                }
            }
        }
    }

    private fun hasSystem() = Installer(this, Proot(this)).hasSystem()

    private fun setLogVisible(visible: Boolean, logScroll: View, toggle: TextView) {
        logScroll.visibility = if (visible) View.VISIBLE else View.INVISIBLE
        toggle.text = if (visible) "Hide details" else "Show details"
    }

    private fun defaultStatus(phase: Boddy.Phase) = when (phase) {
        Boddy.Phase.NEEDS_INSTALL -> if (hasSystem()) {
            "A new version of Boddy is ready: new look, pinch-to-zoom, easy Claude sign-in and DeepSeek. " +
                "Updating keeps your files and takes a few minutes."
        } else {
            "Firefox, Claude, DeepSeek and Antigravity on a real desktop. No root needed.\n\n" +
                "Stay on Wi-Fi: the first install downloads about 1 GB and takes 10–20 minutes."
        }
        Boddy.Phase.READY -> "Ready. Open the desktop, or jump straight into an app."
        Boddy.Phase.RUNNING -> "Boddy is running."
        else -> ""
    }
}
