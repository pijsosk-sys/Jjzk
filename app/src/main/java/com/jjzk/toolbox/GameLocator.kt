package com.jjzk.toolbox

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File

data class GameInstall(
    val pkg: String,
    val versionName: String,
    val version: MinecraftVersion.V,
    val era: MinecraftVersion.Era,
    val root: File
) {
    fun subdir(kind: PackKind) = File(root, MinecraftVersion.subdir(kind)).apply { mkdirs() }
}

object GameLocator {

    private val KNOWN = listOf(
        MinecraftVersion.PKG_RETAIL,
        MinecraftVersion.PKG_PREVIEW,
        MinecraftVersion.PKG_EDU
    )

    fun detect(ctx: Context): List<GameInstall> {
        val pm = ctx.packageManager
        val out = mutableListOf<GameInstall>()
        for (pkg in KNOWN) {
            try {
                val info = pm.getPackageInfo(pkg, 0)
                val name = info.versionName ?: "0.0.0"
                val v = MinecraftVersion.parse(name)
                val root = File(MinecraftVersion.installRoot(pkg, Build.VERSION.SDK_INT))
                out += GameInstall(pkg, name, v, MinecraftVersion.Era.of(v), root)
            } catch (_: PackageManager.NameNotFoundException) {
                // Not installed; skip.
            }
        }
        return out
    }

    /** Pick the newest installed, preferring retail over preview over edu. */
    fun primary(installs: List<GameInstall>): GameInstall? {
        if (installs.isEmpty()) return null
        return installs.sortedWith(
            compareByDescending<GameInstall> { it.version }
                .thenBy { KNOWN.indexOf(it.pkg) }
        ).first()
    }
}
