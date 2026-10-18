package com.jjzk.toolbox.world

import com.jjzk.toolbox.WorldSummary
import com.jjzk.toolbox.nbt.NbtIo
import com.jjzk.toolbox.nbt.NbtTag
import java.io.File

/**
 * Safe editor for Bedrock worlds. Everything mutates level.dat in place
 * through a full NBT round-trip — no live memory hooks, no root.
 * Covers the knobs the classic Toolbox exposed: game mode, time of day,
 * weather, difficulty, cheats toggle, keep inventory, spawn point.
 */
object WorldEditor {

    enum class GameMode(val code: Int) { SURVIVAL(0), CREATIVE(1), ADVENTURE(2), SPECTATOR(6) }
    enum class Difficulty(val code: Int) { PEACEFUL(0), EASY(1), NORMAL(2), HARD(3) }

    data class Snapshot(
        val name: String,
        val mode: GameMode,
        val difficulty: Difficulty,
        val time: Long,
        val cheats: Boolean,
        val keepInventory: Boolean,
        val mobGriefing: Boolean,
        val spawnX: Int, val spawnY: Int, val spawnZ: Int,
        val weather: Weather
    )

    enum class Weather { CLEAR, RAIN, THUNDER }

    fun read(world: WorldSummary): Snapshot {
        val file = NbtIo.readLevelDat(File(world.folder, "level.dat"))
        val r = file.root
        val mode = GameMode.values().firstOrNull { it.code == (r.getInt("GameType") ?: 0) } ?: GameMode.SURVIVAL
        val diff = Difficulty.values().firstOrNull { it.code == (r.getInt("Difficulty") ?: 2) } ?: Difficulty.NORMAL
        val weather = when {
            (r.getInt("lightningLevel") ?: 0) > 0 -> Weather.THUNDER
            (r.getInt("rainLevel") ?: 0) > 0      -> Weather.RAIN
            else                                   -> Weather.CLEAR
        }
        return Snapshot(
            name          = r.getString("LevelName") ?: world.folder.name,
            mode          = mode,
            difficulty    = diff,
            time          = r.getLong("Time") ?: 0L,
            cheats        = (r.getByte("commandsEnabled") ?: 0).toInt() != 0,
            keepInventory = (r.getByte("keepinventory") ?: 0).toInt() != 0,
            mobGriefing   = (r.getByte("mobgriefing") ?: 1).toInt() != 0,
            spawnX        = r.getInt("SpawnX") ?: 0,
            spawnY        = r.getInt("SpawnY") ?: 64,
            spawnZ        = r.getInt("SpawnZ") ?: 0,
            weather       = weather
        )
    }

    fun apply(world: WorldSummary, s: Snapshot) {
        val datFile = File(world.folder, "level.dat")
        val file = NbtIo.readLevelDat(datFile)
        val r = file.root
        setStr(r, "LevelName", s.name)
        setInt(r, "GameType", s.mode.code)
        setInt(r, "Difficulty", s.difficulty.code)
        setLong(r, "Time", s.time)
        setByte(r, "commandsEnabled", if (s.cheats) 1 else 0)
        setByte(r, "keepinventory", if (s.keepInventory) 1 else 0)
        setByte(r, "mobgriefing", if (s.mobGriefing) 1 else 0)
        setInt(r, "SpawnX", s.spawnX)
        setInt(r, "SpawnY", s.spawnY)
        setInt(r, "SpawnZ", s.spawnZ)
        when (s.weather) {
            Weather.CLEAR   -> { setInt(r, "rainLevel", 0); setInt(r, "lightningLevel", 0) }
            Weather.RAIN    -> { setInt(r, "rainLevel", 1); setInt(r, "lightningLevel", 0) }
            Weather.THUNDER -> { setInt(r, "rainLevel", 1); setInt(r, "lightningLevel", 1) }
        }
        NbtIo.writeLevelDat(datFile, file)
        // Keep levelname.txt in sync so the game picks up the rename.
        File(world.folder, "levelname.txt").writeText(s.name)
    }

    private fun setStr(c: NbtTag.CompoundTag, k: String, v: String)   { c.map[k] = NbtTag.StringTag(v) }
    private fun setInt(c: NbtTag.CompoundTag, k: String, v: Int)      { c.map[k] = NbtTag.IntTag(v) }
    private fun setLong(c: NbtTag.CompoundTag, k: String, v: Long)    { c.map[k] = NbtTag.LongTag(v) }
    private fun setByte(c: NbtTag.CompoundTag, k: String, v: Int)     { c.map[k] = NbtTag.ByteTag(v.toByte()) }
}
