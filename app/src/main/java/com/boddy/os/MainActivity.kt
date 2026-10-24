package com.boddy.os

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
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

/** Setup screen: installs Linux on first run, then opens the desktop. */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val status = findViewById<TextView>(R.id.status)
        val progress = findViewById<ProgressBar>(R.id.progress)
        val action = findViewById<Button>(R.id.action)
        val reset = findViewById<Button>(R.id.reset)
        val log = findViewById<TextView>(R.id.log)
        val logScroll = findViewById<ScrollView>(R.id.logScroll)

        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)

        if (Boddy.phase.value == Boddy.Phase.NEEDS_INSTALL && BoddyService.isInstalled(this)) {
            Boddy.phase.value = Boddy.Phase.READY
        }

        action.setOnClickListener {
            when (Boddy.phase.value) {
                Boddy.Phase.NEEDS_INSTALL, Boddy.Phase.ERROR -> BoddyService.send(this, BoddyService.ACTION_INSTALL)
                Boddy.Phase.READY, Boddy.Phase.RUNNING -> startActivity(Intent(this, DesktopActivity::class.java))
                Boddy.Phase.INSTALLING -> Unit
            }
        }

        reset.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Reinstall Linux?")
                .setMessage("This deletes everything inside Boddy (apps, files, settings) and downloads it again.")
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
                            status.text = text.ifEmpty { defaultStatus(phase) }
                            action.isEnabled = phase != Boddy.Phase.INSTALLING
                            action.text = when (phase) {
                                Boddy.Phase.NEEDS_INSTALL -> "Install Boddy (about 1 GB)"
                                Boddy.Phase.INSTALLING -> "Installing…"
                                Boddy.Phase.ERROR -> "Retry install"
                                Boddy.Phase.READY, Boddy.Phase.RUNNING -> "Open desktop"
                            }
                            reset.visibility =
                                if (phase == Boddy.Phase.READY || phase == Boddy.Phase.ERROR) View.VISIBLE else View.GONE
                            progress.visibility = if (phase == Boddy.Phase.INSTALLING) View.VISIBLE else View.GONE
                            progress.isIndeterminate = pct < 0
                            if (pct >= 0) progress.progress = pct
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

    private fun defaultStatus(phase: Boddy.Phase) = when (phase) {
        Boddy.Phase.NEEDS_INSTALL ->
            "Your own Linux desktop with Firefox, Claude and Antigravity. No root needed. " +
                "Stay on Wi-Fi: the first install downloads about 1 GB and takes 10-20 minutes."
        Boddy.Phase.READY -> "Ready."
        Boddy.Phase.RUNNING -> "The desktop is running."
        else -> ""
    }
}
