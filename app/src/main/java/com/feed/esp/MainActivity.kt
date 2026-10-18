package com.feed.esp

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.feed.esp.databinding.ActivityMainBinding

// Single-screen control panel. One button: grant permissions, start the ESP,
// then launch Fortnite. Toggles and sliders drive Config live.
class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private val REQ_CAPTURE = 7001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        wireControls()

        b.btnLaunch.setOnClickListener { onLaunch() }
        b.btnStop.setOnClickListener {
            stopService(Intent(this, CaptureService::class.java))
            b.status.text = "ESP stopped"
        }
    }

    private fun wireControls() {
        b.swEsp.setOnCheckedChangeListener { _, v -> Config.espEnabled = v }
        b.swTracers.setOnCheckedChangeListener { _, v -> Config.tracers = v }
        b.swDistance.setOnCheckedChangeListener { _, v -> Config.distanceLabels = v }

        b.seekConf.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, p: Int, u: Boolean) {
                Config.confidence = p / 100f
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
        })

        b.colorGroup.setOnCheckedChangeListener { _, id ->
            Config.boxColor = when (id) {
                R.id.cRed -> Color.RED
                R.id.cCyan -> Color.CYAN
                else -> Color.GREEN
            }
        }
    }

    // Step 1: overlay permission. Step 2: screen-capture consent. The capture
    // result callback starts the service, which then launches the game.
    private fun onLaunch() {
        if (!Settings.canDrawOverlays(this)) {
            b.status.text = "Grant 'Display over other apps', then press Launch again"
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
            return
        }

        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ_CAPTURE)
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req != REQ_CAPTURE) return
        if (res != Activity.RESULT_OK || data == null) {
            b.status.text = "Screen capture denied"
            return
        }

        val svc = Intent(this, CaptureService::class.java)
            .putExtra("code", res)
            .putExtra("data", data)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc) else startService(svc)

        b.status.text = "ESP running — launching Fortnite"
        launchGame()
    }

    private fun launchGame() {
        val intent = packageManager.getLaunchIntentForPackage(Config.GAME_PACKAGE)
        if (intent != null) {
            startActivity(intent)
            moveTaskToBack(true)
        } else {
            Toast.makeText(
                this,
                "Fortnite (${Config.GAME_PACKAGE}) not installed. ESP overlay is running; open the game manually.",
                Toast.LENGTH_LONG
            ).show()
            b.status.text = "ESP running — game not found, open it manually"
        }
    }
}
