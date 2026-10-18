package com.jjzk.toolbox

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Bedrock level.dat is an 8-byte little-endian header
 * (version:int32, length:int32) followed by a little-endian NBT compound tag.
 * Classic PE (<= 0.9) wrote headerless little-endian NBT; we accept both.
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
        val bytes = file.readBytes()
        val payload = stripHeader(bytes)
        return try {
            parse(payload)
        } catch (_: Throwable) {
            empty()
        }
    }

    private fun stripHeader(b: ByteArray): ByteArray {
        if (b.size < 8) return b
        val headerLen = ByteBuffer.wrap(b, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
        return if (headerLen in 1..(b.size - 8))
            b.copyOfRange(8, 8 + headerLen)
        else b
    }

    private fun empty() = Data("", 0, 0L, 0, 0, 0, 0, 0)

    // Minimal little-endian NBT reader — only pulls the fields we need.
    private fun parse(data: ByteArray): Data {
        val nbt = LittleEndianNbt(data)
        nbt.expectCompoundRoot()

        var name = ""
        var gameType = 0
        var lastPlayed = 0L
        var storageVersion = 0
        var lo = IntArray(4)

        nbt.walk { tagId, tagName, reader ->
            when (tagName) {
                "LevelName"       -> if (tagId == 8) name = reader.readString()
                "GameType"        -> if (tagId == 3) gameType = reader.readInt()
                "LastPlayed"      -> if (tagId == 4) lastPlayed = reader.readLong()
                "StorageVersion"  -> if (tagId == 3) storageVersion = reader.readInt()
                "lastOpenedWithVersion" -> if (tagId == 9) lo = reader.readIntList(4)
                else -> reader.skip(tagId)
            }
        }
        return Data(name, gameType, lastPlayed, storageVersion,
            lo.getOrElse(0) { 0 }, lo.getOrElse(1) { 0 },
            lo.getOrElse(2) { 0 }, lo.getOrElse(3) { 0 })
    }

    /** Rewrite LevelName in-place; keeps the 8-byte header intact. */
    fun writeName(file: File, newName: String) {
        val bytes = file.readBytes()
        if (bytes.size < 12) return
        val headerLen = ByteBuffer.wrap(bytes, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
        if (headerLen <= 0 || headerLen > bytes.size - 8) return
        val payload = bytes.copyOfRange(8, 8 + headerLen)
        val rewritten = LittleEndianNbt(payload).replaceString("LevelName", newName) ?: return
        val out = ByteArrayOutputStream()
        out.write(bytes, 0, 4)
        val lenBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(rewritten.size).array()
        out.write(lenBuf)
        out.write(rewritten)
        file.writeBytes(out.toByteArray())
    }
}
