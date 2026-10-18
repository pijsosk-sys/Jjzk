package com.feed.esp

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast

// No UI. Tapping the icon: ensure permissions, start the ESP overlay, launch
// Fortnite, then disappear. The only screens the user sees are the two
// OS-mandated permission prompts (overlay + screen capture) on first run.
class MainActivity : Activity() {

    private val REQ_CAPTURE = 7001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        step()
    }

    private fun step() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Allow 'Display over other apps', then tap the app again", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            finish()
            return
        }
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ_CAPTURE)
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req != REQ_CAPTURE) { finish(); return }
        if (res != Activity.RESULT_OK || data == null) {
            Toast.makeText(this, "Screen capture denied", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val svc = Intent(this, CaptureService::class.java)
            .putExtra("code", res)
            .putExtra("data", data)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc) else startService(svc)

        launchGame()
        finish()
    }

    private fun launchGame() {
        val intent = packageManager.getLaunchIntentForPackage(Config.GAME_PACKAGE)
        if (intent != null) startActivity(intent)
        else Toast.makeText(this, "Fortnite not found — ESP is running, open the game yourself", Toast.LENGTH_LONG).show()
    }
}
