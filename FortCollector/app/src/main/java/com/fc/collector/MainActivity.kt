package com.fc.collector

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.fc.collector.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var ui: ActivityMainBinding
    private lateinit var projMgr: MediaProjectionManager

    private val projLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK && res.data != null) {
            val svc = Intent(this, CaptureService::class.java).apply {
                action = CaptureService.ACTION_START
                putExtra(CaptureService.EXTRA_CODE, res.resultCode)
                putExtra(CaptureService.EXTRA_DATA, res.data)
                putExtra(CaptureService.EXTRA_SINK, ui.sinkUrl.text.toString().trim())
            }
            ContextCompat.startForegroundService(this, svc)
            ui.status.text = "capturing"
        } else {
            ui.status.text = "projection denied"
        }
    }

    private val notifLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { startProjection() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = ActivityMainBinding.inflate(layoutInflater)
        setContentView(ui.root)
        projMgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        ui.startBtn.setOnClickListener { ensureNotifThenProject() }
        ui.stopBtn.setOnClickListener {
            startService(Intent(this, CaptureService::class.java).apply {
                action = CaptureService.ACTION_STOP
            })
            ui.status.text = "stopped"
        }
    }

    private fun ensureNotifThenProject() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else startProjection()
    }

    private fun startProjection() {
        projLauncher.launch(projMgr.createScreenCaptureIntent())
    }
}
