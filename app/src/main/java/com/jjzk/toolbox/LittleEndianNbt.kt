package com.jjzk.toolbox

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * Hand-rolled little-endian NBT walker for Bedrock level.dat.
 * Tag IDs match Mojang's spec: 0=End, 1=Byte, 2=Short, 3=Int, 4=Long,
 * 5=Float, 6=Double, 7=ByteArray, 8=String, 9=List, 10=Compound,
 * 11=IntArray, 12=LongArray.
 */
class LittleEndianNbt(private val data: ByteArray) {

    private var pos = 0
    private val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

    fun expectCompoundRoot() {
        val id = u8(); require(id == 10) { "root tag id $id (expected 10 Compound)" }
        readString() // root name
    }

    fun interface Visitor {
        fun onTag(id: Int, name: String, reader: Reader)
    }

    inner class Reader {
        fun readByte(): Int { val v = data[pos].toInt(); pos++; return v }
        fun readShort(): Int { val v = buf.getShort(pos).toInt(); pos += 2; return v }
        fun readInt(): Int { val v = buf.getInt(pos); pos += 4; return v }
        fun readLong(): Long { val v = buf.getLong(pos); pos += 8; return v }
        fun readFloat(): Float { val v = buf.getFloat(pos); pos += 4; return v }
        fun readDouble(): Double { val v = buf.getDouble(pos); pos += 8; return v }
        fun readString(): String = this@LittleEndianNbt.readString()
        fun readIntList(expectedCount: Int): IntArray {
            val childId = readByte()
            val count = readInt()
            require(childId == 3) { "list of ints expected, got id $childId" }
            val out = IntArray(count)
            for (i in 0 until count) out[i] = readInt()
            return if (count == expectedCount) out else out.copyOf(expectedCount)
        }

        fun skip(id: Int) { skipPayload(id) }
    }

    fun walk(visitor: Visitor) {
        while (pos < data.size) {
            val id = u8()
            if (id == 0) break
            val name = readString()
            visitor.onTag(id, name, Reader())
        }
    }

    /** Return a new payload with the named top-level TAG_String replaced. */
    fun replaceString(target: String, newValue: String): ByteArray? {
        val out = ByteArrayOutputStream()
        pos = 0
        val id0 = u8(); if (id0 != 10) return null
        val rootName = readString()
        writeTagHeader(out, 10, rootName)
        var found = false
        while (pos < data.size) {
            val tagStart = pos
            val id = u8()
            if (id == 0) { out.write(0); break }
            val name = readString()
            if (id == 8 && name == target) {
                readString() // consume old value
                writeTagHeader(out, 8, name)
                writeLString(out, newValue)
                found = true
            } else {
                val payloadStart = pos
                skipPayload(id)
                val payloadEnd = pos
                out.write(data, tagStart, payloadStart - tagStart)
                out.write(data, payloadStart, payloadEnd - payloadStart)
            }
        }
        return if (found) out.toByteArray() else null
    }

    private fun writeTagHeader(out: ByteArrayOutputStream, id: Int, name: String) {
        out.write(id)
        writeLString(out, name)
    }

    private fun writeLString(out: ByteArrayOutputStream, s: String) {
        val encoded = s.toByteArray(StandardCharsets.UTF_8)
        require(encoded.size <= 0xFFFF) { "NBT string overflow" }
        out.write(encoded.size and 0xFF)
        out.write((encoded.size ushr 8) and 0xFF)
        out.write(encoded)
    }

    fun readString(): String {
        val len = buf.getShort(pos).toInt() and 0xFFFF; pos += 2
        val s = String(data, pos, len, StandardCharsets.UTF_8)
        pos += len
        return s
    }

    private fun u8(): Int { val v = data[pos].toInt() and 0xFF; pos++; return v }

    private fun skipPayload(id: Int) {
        when (id) {
            1 -> pos += 1
            2 -> pos += 2
            3 -> pos += 4
            4 -> pos += 8
            5 -> pos += 4
            6 -> pos += 8
            7 -> { val n = buf.getInt(pos); pos += 4 + n }
            8 -> { readString() }
            9 -> {
                val childId = u8()
                val n = buf.getInt(pos); pos += 4
                for (i in 0 until n) skipPayload(childId)
            }
            10 -> {
                while (pos < data.size) {
                    val cid = u8()
                    if (cid == 0) break
                    readString()
                    skipPayload(cid)
                }
            }
            11 -> { val n = buf.getInt(pos); pos += 4 + n * 4 }
            12 -> { val n = buf.getInt(pos); pos += 4 + n * 8 }
            else -> throw IllegalStateException("unknown NBT tag id $id at $pos")
        }
    }
}
