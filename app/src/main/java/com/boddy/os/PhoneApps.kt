package com.boddy.os

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File

/** The phone apps Boddy works with, and finding them on the phone. */
object PhoneApps {

    /** [match] is checked against package names and app labels, lowercase. */
    data class Known(val title: String, val match: List<String>, val playStore: String? = null)

    data class Installed(val title: String, val packageName: String)

    val KNOWN = listOf(
        Known("Minecraft", listOf("com.mojang.minecraftpe"), "com.mojang.minecraftpe"),
        Known("DroidBridge", listOf("droidbridge")),
        Known("Roblox", listOf("com.roblox.client"), "com.roblox.client"),
        Known("Winlator", listOf("winlator")),
    )

    /** Every launchable app on the phone as (label, package). */
    private fun launchable(context: Context): List<Pair<String, String>> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0).map {
            it.loadLabel(pm).toString() to it.activityInfo.packageName
        }
    }

    /** Which of the [KNOWN] apps are installed. */
    fun installed(context: Context): Map<Known, Installed> {
        val apps = launchable(context)
        return KNOWN.mapNotNull { known ->
            apps.firstOrNull { (label, pkg) -> known.match.any { it == pkg || it in label.lowercase() } }
                ?.let { (_, pkg) -> known to Installed(known.title, pkg) }
        }.toMap()
    }

    /** Finds any launchable app whose name or package matches [name]. Known names first. */
    fun find(context: Context, name: String): Installed? {
        val query = name.trim().lowercase()
        installed(context).values.firstOrNull { it.title.lowercase() == query }?.let { return it }
        val apps = launchable(context)
        val hit = apps.firstOrNull { (label, pkg) -> label.lowercase() == query || pkg == query }
            ?: apps.firstOrNull { (label, _) -> query in label.lowercase() }
        return hit?.let { (label, pkg) -> Installed(label, pkg) }
    }

    fun playStoreIntent(pkg: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))

    /** Writes the list Linux-side tools (phone-apps) read. */
    fun writeForLinux(context: Context, rootfs: File) {
        val found = installed(context)
        val text = KNOWN.joinToString("\n") { known ->
            val app = found[known]
            if (app != null) "${known.title}: installed (${app.packageName})" else "${known.title}: not installed"
        }
        File(rootfs, "opt/boddy/phone-apps.txt").apply { parentFile?.mkdirs() }.writeText(text + "\n")
    }
}
