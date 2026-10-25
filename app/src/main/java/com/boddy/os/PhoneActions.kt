package com.boddy.os

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File

/** Phone-side actions Linux can ask for: saving and opening files, launching phone apps. */
class PhoneActions(private val context: Context) {

    private val rootfs = Proot(context).rootfs

    /** A file inside the Linux system, or null if the path escapes it or isn't a file. */
    private fun linuxFile(path: String): File? {
        val root = rootfs.canonicalFile
        val file = File(root, path.removePrefix("/")).canonicalFile
        return file.takeIf { it.path.startsWith(root.path + "/") && it.isFile }
    }

    /** Where to write a new file at a Linux path, or null if the path escapes the Linux system. */
    fun linuxOutput(path: String): File? {
        val root = rootfs.canonicalFile
        val file = File(root, path.removePrefix("/")).canonicalFile
        return file.takeIf { it.path.startsWith(root.path + "/") && !it.isDirectory }
    }

    /** Copies a Linux file to Downloads/Boddy. Returns the bridge reply. */
    fun saveToDownloads(path: String): String {
        val file = linuxFile(path) ?: return "error: no such file in Boddy: $path"
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType(file))
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Boddy")
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return "error: couldn't create the file in Downloads"
                context.contentResolver.openOutputStream(uri).use { out -> file.inputStream().use { it.copyTo(out!!) } }
                "ok: saved to Downloads/Boddy/${file.name}"
            } else {
                val dir = File(context.getExternalFilesDir(null), "Boddy").apply { mkdirs() }
                file.copyTo(File(dir, file.name), overwrite = true)
                "ok: saved to ${dir.path}/${file.name}"
            }
        } catch (e: Exception) {
            "error: ${e.message}"
        }
    }

    /** Opens a Linux file with a phone app; Minecraft files go straight to Minecraft. */
    fun openFile(activity: Activity, path: String) {
        val file = linuxFile(path) ?: return toast(activity, "File not found: $path")
        val shared = File(context.cacheDir, "share").apply { mkdirs() }.let { File(it, file.name) }
        file.copyTo(shared, overwrite = true)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", shared)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mimeType(file))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (file.extension.lowercase() in MINECRAFT_FILES && isInstalled(MINECRAFT)) {
            intent.setPackage(MINECRAFT)
            start(activity, intent)
        } else {
            start(activity, Intent.createChooser(intent, "Open ${file.name} with"))
        }
    }

    fun openLink(activity: Activity, url: String) =
        start(activity, Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))

    /** Names of every app on the phone that has a launcher icon, sorted. */
    fun listApps(): List<String> = PhoneApps.allLabels(context)

    /**
     * Opens a phone app by name, e.g. "Minecraft" or "DroidBridge". With [split], it opens next
     * to Boddy in split screen, so the AI can play it while the desktop stays visible.
     */
    fun launchApp(activity: Activity, name: String, split: Boolean = false) {
        val app = PhoneApps.find(context, name) ?: return toast(activity, "Couldn't find \"$name\" on this phone")
        val intent = context.packageManager.getLaunchIntentForPackage(app.packageName) ?: return
        if (!split) return start(activity, intent)
        intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_NEW_TASK)
        val hands = PhoneControl.instance
        if (activity.isInMultiWindowMode || hands == null) return start(activity, intent)
        // Put Boddy into split screen first; the app then fills the other half.
        hands.globalAction("split")
        activity.window.decorView.postDelayed({ start(activity, intent) }, 900)
    }

    private fun start(activity: Activity, intent: Intent) {
        try {
            activity.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            toast(activity, "No app on this phone can open that")
        }
    }

    private fun toast(activity: Activity, text: String) =
        android.widget.Toast.makeText(activity, text, android.widget.Toast.LENGTH_LONG).show()

    private fun isInstalled(pkg: String) = try {
        context.packageManager.getPackageInfo(pkg, 0); true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun mimeType(file: File): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"

    companion object {
        const val MINECRAFT = "com.mojang.minecraftpe"
        private val MINECRAFT_FILES = setOf("mcaddon", "mcpack", "mcworld", "mctemplate")
    }
}
