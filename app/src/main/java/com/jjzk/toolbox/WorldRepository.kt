package com.jjzk.toolbox

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class WorldSummary(
    val id: String,
    val folder: File,
    val name: String,
    val lastPlayedMs: Long,
    val gameMode: Int,
    val sizeBytes: Long,
    val levelFormatVersion: Int,
    val inferredEra: MinecraftVersion.Era
) {
    fun lastPlayedPretty(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(lastPlayedMs))

    fun sizePretty(): String {
        var b = sizeBytes.toDouble()
        val units = listOf("B", "KB", "MB", "GB")
        var i = 0
        while (b >= 1024.0 && i < units.lastIndex) { b /= 1024.0; i++ }
        return String.format(Locale.US, "%.1f %s", b, units[i])
    }
}

object WorldRepository {

    suspend fun list(install: GameInstall): List<WorldSummary> = withContext(Dispatchers.IO) {
        val root = install.subdir(PackKind.WORLD)
        if (!root.exists()) return@withContext emptyList()
        root.listFiles { f -> f.isDirectory && File(f, "level.dat").exists() }
            ?.map { summarize(it) }
            ?.sortedByDescending { it.lastPlayedMs }
            ?: emptyList()
    }

    private fun summarize(folder: File): WorldSummary {
        val level = LevelDat.read(File(folder, "level.dat"))
        val size = diskUsage(folder)
        return WorldSummary(
            id = folder.name,
            folder = folder,
            name = level.levelName.ifBlank { folder.name },
            lastPlayedMs = level.lastPlayed * 1000L,
            gameMode = level.gameType,
            sizeBytes = size,
            levelFormatVersion = level.storageVersion,
            inferredEra = MinecraftVersion.Era.of(level.lastOpenedWithVersion())
        )
    }

    private fun diskUsage(f: File): Long {
        if (!f.exists()) return 0
        if (f.isFile) return f.length()
        var sum = 0L
        f.listFiles()?.forEach { sum += diskUsage(it) }
        return sum
    }

    suspend fun delete(world: WorldSummary) = withContext(Dispatchers.IO) {
        world.folder.deleteRecursively()
    }

    suspend fun rename(world: WorldSummary, newName: String) = withContext(Dispatchers.IO) {
        // Renames now flow through WorldEditor's full round-trip save; keep a
        // minimal fallback for callers that only have a folder in hand.
        File(world.folder, "levelname.txt").writeText(newName)
    }

    fun exportDirInApp(ctx: Context): File =
        File(ctx.getExternalFilesDir(null), "exports").apply { mkdirs() }
}
