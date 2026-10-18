package com.noroot.esp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.SeekBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.noroot.esp.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var mpm: MediaProjectionManager

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK && res.data != null) {
            val svc = Intent(this, ProjectionService::class.java).apply {
                action = ProjectionService.ACTION_START
                putExtra(ProjectionService.EXTRA_RESULT_CODE, res.resultCode)
                putExtra(ProjectionService.EXTRA_DATA, res.data)
                putExtra(ProjectionService.EXTRA_CONF, b.seekConf.progress / 100f)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc)
            else startService(svc)
            b.tvStatus.text = getString(R.string.status_running)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        b.btnOverlay.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                ))
            }
        }

        b.btnStart.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                b.tvStatus.text = "Grant overlay first"
                return@setOnClickListener
            }
            projectionLauncher.launch(mpm.createScreenCaptureIntent())
        }

        b.btnStop.setOnClickListener {
            stopService(Intent(this, ProjectionService::class.java))
            stopService(Intent(this, OverlayService::class.java))
            b.tvStatus.text = getString(R.string.status_idle)
        }

        b.seekConf.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, u: Boolean) {
                b.tvConf.text = "Confidence: ${"%.2f".format(p / 100f)}"
                sendBroadcast(Intent(ProjectionService.ACTION_CONF).putExtra("c", p / 100f))
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }
}
