package com.redcore.booster.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class InstalledApp(val pkg: String, val label: String, val isGame: Boolean, val system: Boolean)

data class LibraryGame(val profile: GameProfile, val label: String, val installed: Boolean)

class GameRepository(private val context: Context, private val settings: SettingsRepository) {
    private val pm = context.packageManager
    private val iconCache = LruCache<String, ImageBitmap>(80)
    private val labelCache = HashMap<String, String>()

    val library: Flow<List<LibraryGame>> = settings.library.combine(settings.removed) { lib, _ ->
        lib.map { LibraryGame(it, label(it.pkg), isInstalled(it.pkg)) }.sortedBy { it.label.lowercase() }
    }

    /** Every app with a launcher entry. Category metadata is supplied by the developer of each app. */
    suspend fun installedApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val acts = pm.queryIntentActivities(main, 0)
        acts.map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .map { ai ->
                InstalledApp(ai.packageName, label(ai.packageName, ai), isGame(ai), (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0)
            }
            .sortedWith(compareByDescending<InstalledApp> { it.isGame }.thenBy { it.label.lowercase() })
    }

    @Suppress("DEPRECATION")
    fun isGame(ai: ApplicationInfo): Boolean =
        (Build.VERSION.SDK_INT >= 26 && ai.category == ApplicationInfo.CATEGORY_GAME) ||
            (ai.flags and ApplicationInfo.FLAG_IS_GAME) != 0

    fun isGamePackage(pkg: String): Boolean = try { isGame(pm.getApplicationInfo(pkg, 0)) } catch (_: Exception) { false }

    /** Adds games that declare themselves as games and have not been removed by the user. Returns how many were added. */
    suspend fun scanForGames(): Int {
        val removed = settings.removed.first()
        val existing = settings.library.first().map { it.pkg }.toSet()
        val found = installedApps().filter { it.isGame && it.pkg !in removed && it.pkg !in existing }
        if (found.isNotEmpty()) {
            val mode = settings.current().globalMode
            settings.updateLibrary { it + found.map { a -> GameProfile(a.pkg, mode) } }
        }
        settings.setScanned()
        return found.size
    }

    suspend fun add(pkgs: Collection<String>) {
        val mode = settings.current().globalMode
        pkgs.forEach { settings.markRemoved(it, false) }
        settings.updateLibrary { lib -> lib + pkgs.filter { p -> lib.none { it.pkg == p } }.map { GameProfile(it, mode) } }
    }

    suspend fun remove(pkg: String) {
        settings.updateLibrary { lib -> lib.filterNot { it.pkg == pkg } }
        settings.markRemoved(pkg, true)
    }

    suspend fun updateProfile(p: GameProfile) = settings.updateLibrary { lib -> lib.map { if (it.pkg == p.pkg) p else it } }

    suspend fun profile(pkg: String): GameProfile? = settings.library.first().firstOrNull { it.pkg == pkg }

    fun launchIntent(pkg: String): Intent? = pm.getLaunchIntentForPackage(pkg)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun isInstalled(pkg: String) = try { pm.getApplicationInfo(pkg, 0); true } catch (_: PackageManager.NameNotFoundException) { false }

    fun label(pkg: String, ai: ApplicationInfo? = null): String = labelCache.getOrPut(pkg) {
        try { (ai ?: pm.getApplicationInfo(pkg, 0)).loadLabel(pm).toString() } catch (_: Exception) { pkg }
    }

    fun icon(pkg: String, sizePx: Int = 144): ImageBitmap? {
        iconCache.get(pkg)?.let { return it }
        return try {
            val d = pm.getApplicationIcon(pkg)
            d.toBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).asImageBitmap().also { iconCache.put(pkg, it) }
        } catch (_: Exception) {
            null
        }
    }
}
