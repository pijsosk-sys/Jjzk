package com.boddy.os

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Everything needed to see why Studio didn't start, as one text: this app's log, a saved crash,
 * and the Studio launcher's own logs, read straight from the Linux files (so it works even when
 * Linux or the desktop is no longer running).
 */
object LogReport {

    /** Clipboards on some phones refuse very large texts; the ends of the logs matter most. */
    private const val MAX_CHARS = 90_000

    fun build(context: Context): String {
        val rootfs = Proot(context).rootfs
        val studioDir = File(rootfs, "root/.cache/roblox-studio")
        val version = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (_: Exception) {
            "?"
        }
        val out = StringBuilder()
        out.append("Studio Launcher $version log, ")
            .append(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()))
            .append(" (Android ${Build.VERSION.RELEASE}, ${Build.MANUFACTURER} ${Build.MODEL})\n")

        CrashLog.peek(context)?.let { section(out, "last app crash", it, 60) }
        section(out, "app log", Boddy.logText(), 120)

        // latest.log is a symlink with a Linux path (/root/...): resolve it inside the rootfs.
        val latest = File(studioDir, "latest.log")
        val launcherLog = linuxTarget(rootfs, latest)
            ?: studioDir.listFiles { f -> f.name.startsWith("launcher-") && f.name.endsWith(".log") }
                ?.maxByOrNull { it.lastModified() }
        if (launcherLog != null) {
            section(out, "Studio launcher log (${launcherLog.name})", read(launcherLog), 250)
        } else {
            out.append("\n=== Studio launcher log ===\n(none yet: Roblox Studio hasn't been launched)\n")
        }
        studioDir.listFiles { f -> f.name.startsWith("helper-") && f.name.endsWith(".log") }
            ?.maxByOrNull { it.lastModified() }
            ?.let { section(out, "sign-in helper log (${it.name})", read(it), 40) }
        listOf(
            File(rootfs, "root/Phone/Download/Boddy-Studio-monitor.log"),
            File(studioDir, "Boddy-Studio-monitor.log"),
        ).filter { it.exists() }.maxByOrNull { it.lastModified() }
            ?.let { section(out, "session monitor", read(it), 25) }
        File(studioDir, "firefox.log").takeIf { it.exists() }
            ?.let { section(out, "Firefox log", read(it), 30) }

        return if (out.length <= MAX_CHARS) out.toString() else {
            out.substring(0, 2_000) + "\n…(cut)…\n" + out.substring(out.length - (MAX_CHARS - 2_100))
        }
    }

    fun copy(context: Context, text: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("Studio Launcher log", text))
    }

    fun share(context: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "Studio Launcher log")
            .putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, "Send the log with…"))
    }

    private fun section(out: StringBuilder, title: String, text: String, lastLines: Int) {
        val lines = text.lines().dropLastWhile { it.isBlank() }
        out.append("\n=== $title")
        if (lines.size > lastLines) out.append(" (last $lastLines of ${lines.size} lines)")
        out.append(" ===\n")
        lines.takeLast(lastLines).forEach { out.append(it.take(400)).append('\n') }
    }

    private fun read(file: File): String = try {
        file.readText()
    } catch (e: Exception) {
        "(couldn't read ${file.name}: ${e.message})"
    }

    /** Where a symlink inside the rootfs points, mapped back into the rootfs; null if it doesn't resolve. */
    private fun linuxTarget(rootfs: File, link: File): File? = try {
        val target = android.system.Os.readlink(link.path)
        val file = if (target.startsWith("/")) File(rootfs, target.removePrefix("/")) else File(link.parentFile, target)
        file.takeIf { it.isFile && it.canonicalPath.startsWith(rootfs.canonicalPath) }
    } catch (_: Exception) {
        link.takeIf { it.isFile }
    }
}
