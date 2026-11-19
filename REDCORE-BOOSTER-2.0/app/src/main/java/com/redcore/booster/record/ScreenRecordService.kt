package com.redcore.booster.record

import android.app.Activity
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.redcore.booster.R
import com.redcore.booster.RedcoreApp
import com.redcore.booster.app
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Records the screen to an MP4 in Movies/REDCORE using Android's MediaProjection API. */
class ScreenRecordService : Service() {
    private var projection: MediaProjection? = null
    private var recorder: MediaRecorder? = null
    private var display: VirtualDisplay? = null
    private var pfd: ParcelFileDescriptor? = null
    private var uri: Uri? = null
    private var file: File? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopRecording(); return START_NOT_STICKY }
        val code = intent?.getIntExtra(EXTRA_CODE, 0) ?: 0
        @Suppress("DEPRECATION")
        val data: Intent? = intent?.getParcelableExtra(EXTRA_DATA)
        if (code != Activity.RESULT_OK || data == null) { stopSelf(); return START_NOT_STICKY }
        val mic = intent.getBooleanExtra(EXTRA_MIC, false) &&
            checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED

        var type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0
        if (mic && Build.VERSION.SDK_INT >= 30) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        val fg = runCatching { ServiceCompat.startForeground(this, NOTIF_ID, notification(), type) }
        if (fg.isFailure) {
            // Android refused the foreground service (e.g. the consent expired): report it instead of crashing.
            Toast.makeText(this, "Recording couldn't start: ${fg.exceptionOrNull()?.message ?: "not allowed"}", Toast.LENGTH_LONG).show()
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            start(code, data, mic)
            recording.value = true
        } catch (e: Exception) {
            Toast.makeText(this, "Recording failed: ${e.message}", Toast.LENGTH_LONG).show()
            cleanup(delete = true)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun start(code: Int, data: Intent, mic: Boolean) {
        val mpm = getSystemService(MediaProjectionManager::class.java)
        val p = mpm.getMediaProjection(code, data) ?: error("Projection denied")
        projection = p
        p.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { Handler(Looper.getMainLooper()).post { stopRecording() } }
        }, Handler(Looper.getMainLooper()))

        val wm = getSystemService(WindowManager::class.java)
        val m = DisplayMetrics()
        @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(m)
        // Quality from Game Tools: 720p30 / 1080p60 / native 60. Encoders need even dimensions.
        val q = app.settings.latest.recQuality
        val maxEdge = when (q) { 0 -> 1280f; 1 -> 1920f; else -> Float.MAX_VALUE }
        val scale = minOf(1f, maxEdge / maxOf(m.widthPixels, m.heightPixels))
        val w = ((m.widthPixels * scale).toInt() / 2) * 2
        val h = ((m.heightPixels * scale).toInt() / 2) * 2

        val name = "REDCORE_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".mp4"
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
        recorder = r
        if (mic) r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        if (mic) { r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC); r.setAudioEncodingBitRate(128_000); r.setAudioSamplingRate(44_100) }
        r.setVideoSize(w, h)
        r.setVideoFrameRate(if (q == 0) 30 else 60)
        r.setVideoEncodingBitRate(when (q) { 0 -> 6_000_000; 1 -> 12_000_000; else -> 20_000_000 })

        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/REDCORE")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val u = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: error("Cannot create video file")
            uri = u
            pfd = contentResolver.openFileDescriptor(u, "w")
            r.setOutputFile(pfd!!.fileDescriptor)
        } else {
            val dir = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir
            file = File(dir, name)
            r.setOutputFile(file!!.absolutePath)
        }
        r.prepare()
        display = p.createVirtualDisplay("REDCORE-REC", w, h, m.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, null)
        r.start()
    }

    private fun stopRecording() {
        if (!recording.value && recorder == null) { stopSelf(); return }
        var ok = true
        try { recorder?.stop() } catch (_: Exception) { ok = false }
        cleanup(delete = !ok)
        recording.value = false
        Toast.makeText(
            this,
            if (ok) "Recording saved to ${if (Build.VERSION.SDK_INT >= 29) "Movies/REDCORE" else file?.parent}" else "Recording was too short and was discarded",
            Toast.LENGTH_LONG
        ).show()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun cleanup(delete: Boolean) {
        runCatching { recorder?.release() }; recorder = null
        runCatching { display?.release() }; display = null
        runCatching { projection?.stop() }; projection = null
        runCatching { pfd?.close() }; pfd = null
        uri?.let { u ->
            if (delete) runCatching { contentResolver.delete(u, null, null) }
            else runCatching { contentResolver.update(u, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null) }
        }
        uri = null
        if (delete) file?.delete()
    }

    override fun onDestroy() {
        // Killed mid-recording: keep the file only if MediaRecorder finalised it, otherwise it is corrupt.
        if (recorder != null) { val ok = runCatching { recorder?.stop() }.isSuccess; cleanup(delete = !ok) }
        recording.value = false
        super.onDestroy()
    }

    private fun notification(): Notification {
        val stop = PendingIntent.getService(this, 3, Intent(this, ScreenRecordService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, RedcoreApp.CH_RECORD)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("REDCORE is recording the screen")
            .setContentText("Tap Stop or use the toolkit to finish")
            .setOngoing(true)
            .setColor(0xFFFF1E3C.toInt())
            .addAction(0, "Stop", stop)
            .build()
    }

    companion object {
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        const val EXTRA_MIC = "mic"
        private const val ACTION_STOP = "stop"
        private const val NOTIF_ID = 77
        val recording = MutableStateFlow(false)

        fun requestStart(c: Context, mic: Boolean) = c.startActivity(
            Intent(c, RecordPermissionActivity::class.java).putExtra(EXTRA_MIC, mic).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )

        fun stop(c: Context) = c.startService(Intent(c, ScreenRecordService::class.java).setAction(ACTION_STOP))
    }
}
