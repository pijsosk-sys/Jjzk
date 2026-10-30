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
import kotlinx.coroutines.delay
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
                Boddy.Phase.READY, Boddy.Phase.RUNNING -> openDesktop(null)
                Boddy.Phase.INSTALLING -> Unit
            }
        }
        findViewById<Button>(R.id.signin).setOnClickListener { openDesktop("claude-signin") }
        findViewById<Button>(R.id.claudeCode).setOnClickListener { openDesktop("claude-code") }
        findViewById<Button>(R.id.codex).setOnClickListener { openDesktop("codex") }
        findViewById<Button>(R.id.deepseek).setOnClickListener { openDesktop("deepseek-code") }
        findViewById<Button>(R.id.gemini).setOnClickListener { openDesktop("gemini") }
        findViewById<Button>(R.id.qwen).setOnClickListener { openDesktop("qwen") }
        findViewById<Button>(R.id.opencode).setOnClickListener { openDesktop("opencode") }
        findViewById<TextView>(R.id.agentMode).setOnClickListener { chooseAgentMode(then = null) }

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
        showAgentMode()
        showPhoneApps()
        showKeepAlive()
        showPhoneControl()
        findViewById<Button>(R.id.phoneFiles).apply {
            visibility = if (KeepAlive.phoneFilesAllowed()) View.GONE else View.VISIBLE
            setOnClickListener { KeepAlive.allowPhoneFiles(this@MainActivity) }
        }
        CrashLog.take(this)?.let { report ->
            Boddy.log(report)
            AlertDialog.Builder(this)
                .setTitle("Boddy hit a problem last time")
                .setMessage("It's saved under \"Show details\". Screenshot it and send it to get it fixed.\n\n" +
                    report.lines().take(6).joinToString("\n"))
                .setPositiveButton("OK", null)
                .show()
        }
    }

    /** Settings Android uses to kill Boddy in the background, with one-tap fixes. Hidden when all good. */
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
        text("Keep Boddy running in the background", 17f, R.color.boddy_text)
        text("Without these, Android can close Boddy (and your work) when you switch apps.", 13f, R.color.boddy_text_dim)
        if (!KeepAlive.batteryUnrestricted(this)) {
            fixButton("1. Let Boddy run in the background") { KeepAlive.fixBattery(this) }
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

    /** The switch that lets the AI see and tap other apps (see [PhoneControl]). */
    private fun showPhoneControl() {
        val button = findViewById<Button>(R.id.phoneControl)
        val on = PhoneControl.isEnabled(this)
        button.text = if (on) "AI can control phone apps ✓  ·  settings" else "Let AI control phone apps (play Minecraft…)"
        button.setOnClickListener {
            if (on) return@setOnClickListener PhoneControl.openSettings(this)
            AlertDialog.Builder(this)
                .setTitle("Let AI control phone apps")
                .setMessage("Claude in Boddy will be able to see your screen and tap, swipe and type in your " +
                    "apps, like Minecraft. A bubble at the top shows when it's in control: tap it to stop.\n\n" +
                    "1. Tap Open settings.\n" +
                    "2. Tap Installed apps (or Downloaded apps), then Boddy phone control, and turn it on.\n\n" +
                    "Greyed out or \"Restricted setting\"? Go to Settings → Apps → Boddy, tap ⋮ at the top " +
                    "right, choose \"Allow restricted settings\", then try again.")
                .setPositiveButton("Open settings") { _, _ -> PhoneControl.openSettings(this) }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    /** Asks how the AI should work the first time, then opens the desktop (and an app). */
    private fun openDesktop(app: String?) {
        if (Settings(this).agentMode == null) {
            chooseAgentMode { DesktopActivity.open(this, app) }
        } else {
            DesktopActivity.open(this, app)
        }
    }

    private fun chooseAgentMode(then: (() -> Unit)?) {
        val settings = Settings(this)
        val modes = Settings.AgentMode.entries
        val labels = modes.map { "${it.label}\n${it.description}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("How should Claude, Codex and DeepSeek work?")
            .setSingleChoiceItems(labels, modes.indexOf(settings.agentMode ?: Settings.AgentMode.ASK)) { dialog, which ->
                settings.agentMode = modes[which]
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        AgentConfig.apply(Proot(this@MainActivity).rootfs, modes[which])
                    } catch (e: Exception) {
                        Boddy.log("Couldn't save AI settings: ${e.message}")
                    }
                }
                dialog.dismiss()
                showAgentMode()
                then?.invoke()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAgentMode() {
        val mode = Settings(this).agentMode
        findViewById<TextView>(R.id.agentMode).text =
            "AI mode: ${mode?.label ?: "not chosen yet"}  ·  change"
    }

    /** Open buttons for the phone apps Boddy works with; Play Store links for missing ones. */
    private fun showPhoneApps() {
        val row = findViewById<android.widget.LinearLayout>(R.id.phoneApps)
        row.removeAllViews()
        val installed = PhoneApps.installed(this)
        for (known in PhoneApps.KNOWN) {
            val app = installed[known]
            if (app == null && known.playStore == null) continue
            val button = Button(this).apply {
                text = if (app != null) known.title else "Get ${known.title}"
                isAllCaps = false
                setTextColor(getColor(R.color.boddy_text))
                setBackgroundResource(R.drawable.button_card)
                setPadding(40, 0, 40, 0)
                setOnClickListener {
                    val intent = if (app != null) {
                        packageManager.getLaunchIntentForPackage(app.packageName)
                    } else {
                        PhoneApps.playStoreIntent(known.playStore!!)
                    }
                    intent?.let { startActivity(it) }
                }
            }
            row.addView(button, android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            ).apply { marginEnd = 16 })
        }
    }

    private fun hasSystem() = Installer(this, Proot(this)).hasSystem()

    private fun setLogVisible(visible: Boolean, logScroll: View, toggle: TextView) {
        logScroll.visibility = if (visible) View.VISIBLE else View.GONE
        toggle.text = if (visible) "Hide details" else "Show details"
    }

    private fun defaultStatus(phase: Boddy.Phase) = when (phase) {
        Boddy.Phase.NEEDS_INSTALL -> if (hasSystem()) {
            "A new version of Boddy is ready: Claude can now see your phone screen and tap in your apps, " +
                "like playing Minecraft for you. Updating keeps your files and sign-ins."
        } else {
            "Firefox, Claude, DeepSeek and Antigravity on a real desktop. No root needed.\n\n" +
                "Stay on Wi-Fi: the first install downloads Boddy (under 1 GB) and takes a few minutes."
        }
        Boddy.Phase.READY -> "Ready. Open the desktop, or jump straight into an app."
        Boddy.Phase.RUNNING -> "Boddy is running."
        else -> ""
    }
}
