package com.redcore.booster.data

import android.app.usage.StorageStatsManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BigFile(val uri: Uri, val name: String, val size: Long, val mime: String?, val path: String?, val modified: Long)

/** Result of the large-file scan. [partialAccess]: Android 14+ "selected photos only" access, so the list is limited. */
data class FileScan(val files: List<BigFile>, val error: String?, val partialAccess: Boolean = false)

class StorageAnalyzer(private val context: Context, private val games: GameRepository, private val usage: UsageRepository) {

    suspend fun summary(): StorageSummary = withContext(Dispatchers.IO) {
        val fs = StatFs(Environment.getDataDirectory().path)
        var apps: Long? = null; var img: Long? = null; var vid: Long? = null; var aud: Long? = null; var cache: Long? = null
        var total = fs.totalBytes
        var free = fs.availableBytes
        if (usage.hasAccess()) runCatching {
            val ssm = context.getSystemService(StorageStatsManager::class.java)
            val uuid = StorageManager.UUID_DEFAULT
            total = ssm.getTotalBytes(uuid)
            free = ssm.getFreeBytes(uuid)
            val user = Process.myUserHandle()
            val st = ssm.queryStatsForUser(uuid, user)
            apps = st.appBytes + st.dataBytes
            cache = st.cacheBytes
            val ext = ssm.queryExternalStatsForUser(uuid, user)
            img = ext.imageBytes; vid = ext.videoBytes; aud = ext.audioBytes
            // dataBytes for the user includes shared media; subtract so categories don't double count.
            apps = (apps!! - (img ?: 0) - (vid ?: 0) - (aud ?: 0)).coerceAtLeast(st.appBytes)
        }
        StorageSummary(total, free, apps, img, vid, aud, cache)
    }

    suspend fun largestApps(limit: Int = 15): List<AppSize> = withContext(Dispatchers.IO) {
        if (!usage.hasAccess()) return@withContext emptyList()
        val ssm = context.getSystemService(StorageStatsManager::class.java)
        val user = Process.myUserHandle()
        games.installedApps().mapNotNull { a ->
            runCatching {
                val s = ssm.queryStatsForPackage(StorageManager.UUID_DEFAULT, a.pkg, user)
                AppSize(a.pkg, a.label, s.appBytes, s.dataBytes, s.cacheBytes)
            }.getOrNull()
        }.sortedByDescending { it.total }.take(limit)
    }

    /**
     * Largest media files the user has given access to. Read-only: nothing is ever deleted automatically.
     * A failed query is reported as an error, never as "no large files".
     */
    suspend fun largeFiles(minBytes: Long = 20L * 1024 * 1024, limit: Int = 40): FileScan = withContext(Dispatchers.IO) {
        val collection = MediaStore.Files.getContentUri("external")
        val cols = mutableListOf(
            MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE, MediaStore.MediaColumns.DATE_MODIFIED,
        )
        if (Build.VERSION.SDK_INT >= 29) cols += MediaStore.MediaColumns.RELATIVE_PATH
        val out = mutableListOf<BigFile>()
        val partial = Build.VERSION.SDK_INT >= 34 &&
            context.checkSelfPermission("android.permission.READ_MEDIA_VISUAL_USER_SELECTED") == android.content.pm.PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) != android.content.pm.PackageManager.PERMISSION_GRANTED
        val error = runCatching {
            context.contentResolver.query(
                collection, cols.toTypedArray(), "${MediaStore.MediaColumns.SIZE} >= ?", arrayOf(minBytes.toString()),
                "${MediaStore.MediaColumns.SIZE} DESC"
            )?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val iName = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val iSize = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val iMime = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val iMod = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                val iPath = if (Build.VERSION.SDK_INT >= 29) c.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH) else -1
                while (c.moveToNext() && out.size < limit) {
                    val id = c.getLong(iId)
                    val mime = c.getString(iMime)
                    val base = when {
                        mime?.startsWith("image/") == true -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                        mime?.startsWith("video/") == true -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        mime?.startsWith("audio/") == true -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                        else -> collection
                    }
                    out += BigFile(
                        ContentUris.withAppendedId(base, id), c.getString(iName) ?: "Unnamed", c.getLong(iSize), mime,
                        if (iPath >= 0) c.getString(iPath) else null, c.getLong(iMod) * 1000
                    )
                }
            } ?: error("The media library didn't answer.")
        }.exceptionOrNull()
        FileScan(out, error?.let { "Couldn't read the media library: ${it.message ?: it.javaClass.simpleName}" }, partial)
    }

    fun openIntent(f: BigFile): Intent = Intent(Intent.ACTION_VIEW).setDataAndType(f.uri, f.mime ?: "*/*")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)

    /** System "Free up space" / storage manager screen. */
    fun storageManagerIntent(): Intent = Intent(StorageManager.ACTION_MANAGE_STORAGE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    fun internalStorageSettings(): Intent = Intent(android.provider.Settings.ACTION_INTERNAL_STORAGE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    fun appDetails(pkg: String): Intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
