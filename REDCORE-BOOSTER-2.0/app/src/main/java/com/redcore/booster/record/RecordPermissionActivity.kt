package com.redcore.booster.record

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.ContextCompat

/** Invisible activity that asks Android for screen-capture consent, then hands the token to [ScreenRecordService]. */
class RecordPermissionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only ask once: a recreated activity (e.g. rotation while the dialog is open) must not ask again.
        if (savedInstanceState != null) return
        val mpm = getSystemService(MediaProjectionManager::class.java)
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(mpm.createScreenCaptureIntent(), REQ)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Screen recording isn't available on this phone.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ && resultCode == RESULT_OK && data != null) {
            val i = Intent(this, ScreenRecordService::class.java)
                .putExtra(ScreenRecordService.EXTRA_CODE, resultCode)
                .putExtra(ScreenRecordService.EXTRA_DATA, data)
                .putExtra(ScreenRecordService.EXTRA_MIC, intent.getBooleanExtra(ScreenRecordService.EXTRA_MIC, false))
            // Started while this activity is visible, so Android 14+ allows the mediaProjection/microphone service.
            runCatching { ContextCompat.startForegroundService(this, i) }
                .onFailure { Toast.makeText(this, "Android didn't allow recording to start: ${it.message}", Toast.LENGTH_LONG).show() }
        } else if (requestCode == REQ) {
            Toast.makeText(this, "Recording cancelled", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    companion object { private const val REQ = 7 }
}
