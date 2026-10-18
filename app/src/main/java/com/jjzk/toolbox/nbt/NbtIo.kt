package com.jjzk.toolbox.nbt

import java.io.ByteArrayOutputStream
import java.io.DataOutput
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * Little-endian NBT reader + writer with Bedrock level.dat header
 * ({ versionInt, lengthInt } LE prefix, both little-endian).
 */
object NbtIo {

    data class LevelDatFile(val headerVersion: Int, val root: NbtTag.CompoundTag)

    fun readLevelDat(file: File): LevelDatFile {
        val bytes = file.readBytes()
        require(bytes.size >= 8) { "level.dat too small" }
        val hv = le(bytes, 0)
        val len = le(bytes, 4)
        require(len in 1..(bytes.size - 8)) { "bad level.dat header length $len" }
        val payload = bytes.copyOfRange(8, 8 + len)
        val root = read(payload)
        return LevelDatFile(hv, root)
    }

    fun writeLevelDat(file: File, data: LevelDatFile) {
        val payload = write(data.root)
        val out = ByteArray(8 + payload.size)
        putLe(out, 0, data.headerVersion)
        putLe(out, 4, payload.size)
        System.arraycopy(payload, 0, out, 8, payload.size)
        file.writeBytes(out)
    }

    fun read(payload: ByteArray): NbtTag.CompoundTag {
        val r = Reader(payload)
        val tagId = r.u8()
        require(tagId == 10) { "root tag must be Compound (10), got $tagId" }
        r.readString() // root compound name
        return r.readCompoundBody()
    }

    fun write(root: NbtTag.CompoundTag): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(10)
        writeLString(out, "")
        writeCompoundBody(out, root)
        return out.toByteArray()
    }

    private class Reader(val data: ByteArray) {
        var pos = 0
        private val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        fun u8(): Int { val v = data[pos].toInt() and 0xFF; pos++; return v }
        fun i8(): Byte { val v = data[pos]; pos++; return v }
        fun i16(): Short { val v = buf.getShort(pos); pos += 2; return v }
        fun i32(): Int { val v = buf.getInt(pos); pos += 4; return v }
        fun i64(): Long { val v = buf.getLong(pos); pos += 8; return v }
        fun f32(): Float { val v = buf.getFloat(pos); pos += 4; return v }
        fun f64(): Double { val v = buf.getDouble(pos); pos += 8; return v }

        fun readString(): String {
            val len = buf.getShort(pos).toInt() and 0xFFFF; pos += 2
            val s = String(data, pos, len, StandardCharsets.UTF_8)
            pos += len
            return s
        }

        fun readPayload(id: Int): NbtTag = when (id) {
            1  -> NbtTag.ByteTag(i8())
            2  -> NbtTag.ShortTag(i16())
            3  -> NbtTag.IntTag(i32())
            4  -> NbtTag.LongTag(i64())
            5  -> NbtTag.FloatTag(f32())
            6  -> NbtTag.DoubleTag(f64())
            7  -> {
                val n = i32()
                val arr = data.copyOfRange(pos, pos + n); pos += n
                NbtTag.ByteArrayTag(arr)
            }
            8  -> NbtTag.StringTag(readString())
            9  -> {
                val child = u8()
                val n = i32()
                val items = ArrayList<NbtTag>(n)
                for (i in 0 until n) items += readPayload(child)
                NbtTag.ListTag(child, items)
            }
            10 -> readCompoundBody()
            11 -> {
                val n = i32()
                val arr = IntArray(n) { i32() }
                NbtTag.IntArrayTag(arr)
            }
            12 -> {
                val n = i32()
                val arr = LongArray(n) { i64() }
                NbtTag.LongArrayTag(arr)
            }
            else -> throw IllegalStateException("unknown tag id $id at $pos")
        }

        fun readCompoundBody(): NbtTag.CompoundTag {
            val c = NbtTag.CompoundTag()
            while (pos < data.size) {
                val id = u8()
                if (id == 0) return c
                val name = readString()
                c.map[name] = readPayload(id)
            }
            return c
        }
    }

    private fun writeCompoundBody(out: ByteArrayOutputStream, c: NbtTag.CompoundTag) {
        for ((k, v) in c.map) {
            out.write(v.id)
            writeLString(out, k)
            writePayload(out, v)
        }
        out.write(0)
    }

    private fun writePayload(out: ByteArrayOutputStream, t: NbtTag) {
        when (t) {
            is NbtTag.ByteTag      -> out.write(t.value.toInt() and 0xFF)
            is NbtTag.ShortTag     -> putLe(out, t.value.toInt(), 2)
            is NbtTag.IntTag       -> putLe(out, t.value, 4)
            is NbtTag.LongTag      -> putLe(out, t.value)
            is NbtTag.FloatTag     -> putLe(out, java.lang.Float.floatToRawIntBits(t.value), 4)
            is NbtTag.DoubleTag    -> putLe(out, java.lang.Double.doubleToRawLongBits(t.value))
            is NbtTag.ByteArrayTag -> { putLe(out, t.value.size, 4); out.write(t.value) }
            is NbtTag.StringTag    -> writeLString(out, t.value)
            is NbtTag.ListTag      -> {
                out.write(t.childId)
                putLe(out, t.items.size, 4)
                for (c in t.items) writePayload(out, c)
            }
            is NbtTag.CompoundTag  -> writeCompoundBody(out, t)
            is NbtTag.IntArrayTag  -> {
                putLe(out, t.value.size, 4)
                for (v in t.value) putLe(out, v, 4)
            }
            is NbtTag.LongArrayTag -> {
                putLe(out, t.value.size, 4)
                for (v in t.value) putLe(out, v)
            }
        }
    }

    private fun writeLString(out: ByteArrayOutputStream, s: String) {
        val b = s.toByteArray(StandardCharsets.UTF_8)
        require(b.size <= 0xFFFF)
        out.write(b.size and 0xFF)
        out.write((b.size ushr 8) and 0xFF)
        out.write(b)
    }

    private fun le(b: ByteArray, off: Int): Int =
        ByteBuffer.wrap(b, off, 4).order(ByteOrder.LITTLE_ENDIAN).int

    private fun putLe(dest: ByteArray, off: Int, v: Int) {
        ByteBuffer.wrap(dest, off, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(v)
    }

    private fun putLe(out: ByteArrayOutputStream, v: Int, bytes: Int) {
        for (i in 0 until bytes) out.write((v ushr (i * 8)) and 0xFF)
    }

    private fun putLe(out: ByteArrayOutputStream, v: Long) {
        for (i in 0 until 8) out.write(((v ushr (i * 8)) and 0xFF).toInt())
    }
}
