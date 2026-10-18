package com.jjzk.toolbox

import com.jjzk.toolbox.nbt.NbtIo
import java.io.File

/**
 * Thin facade over the little-endian NBT I/O used by UI layers that just
 * need the quick summary fields. Full edits go through WorldEditor.
 */
object LevelDat {

    data class Data(
        val levelName: String,
        val gameType: Int,
        val lastPlayed: Long,
        val storageVersion: Int,
        val lastOpenedMajor: Int,
        val lastOpenedMinor: Int,
        val lastOpenedPatch: Int,
        val lastOpenedBuild: Int
    ) {
        fun lastOpenedWithVersion() =
            MinecraftVersion.V(lastOpenedMajor, lastOpenedMinor, lastOpenedPatch, lastOpenedBuild)
    }

    fun read(file: File): Data {
        if (!file.exists() || file.length() < 12) return empty()
        return try {
            val root = NbtIo.readLevelDat(file).root
            val lo = (root.getList("lastOpenedWithVersion"))?.items
                ?.mapNotNull { (it as? com.jjzk.toolbox.nbt.NbtTag.IntTag)?.value }
                ?: emptyList()
            Data(
                levelName       = root.getString("LevelName") ?: "",
                gameType        = root.getInt("GameType") ?: 0,
                lastPlayed      = root.getLong("LastPlayed") ?: 0L,
                storageVersion  = root.getInt("StorageVersion") ?: 0,
                lastOpenedMajor = lo.getOrElse(0) { 0 },
                lastOpenedMinor = lo.getOrElse(1) { 0 },
                lastOpenedPatch = lo.getOrElse(2) { 0 },
                lastOpenedBuild = lo.getOrElse(3) { 0 }
            )
        } catch (_: Throwable) {
            empty()
        }
    }

    private fun empty() = Data("", 0, 0L, 0, 0, 0, 0, 0)
}
