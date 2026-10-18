package com.jjzk.toolbox.hacks

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater

/**
 * Minimal pure-Kotlin PNG writer. Produces a 16x16 fully-transparent
 * RGBA image for X-ray textures without pulling in Android's Bitmap
 * (which costs us a context on background threads).
 */
object Png {

    fun transparent16(): ByteArray = rgba(IntArray(16 * 16) { 0 }, 16, 16)

    private fun rgba(pixelsARGB: IntArray, width: Int, height: Int): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))

        val ihdr = ByteArrayOutputStream().apply {
            writeInt(width); writeInt(height)
            write(8)   // bit depth
            write(6)   // color type RGBA
            write(0); write(0); write(0)
        }.toByteArray()
        writeChunk(out, "IHDR", ihdr)

        val raw = ByteArrayOutputStream()
        for (y in 0 until height) {
            raw.write(0) // filter None
            for (x in 0 until width) {
                val argb = pixelsARGB[y * width + x]
                raw.write((argb ushr 16) and 0xFF)
                raw.write((argb ushr 8)  and 0xFF)
                raw.write( argb          and 0xFF)
                raw.write((argb ushr 24) and 0xFF)
            }
        }
        writeChunk(out, "IDAT", deflate(raw.toByteArray()))
        writeChunk(out, "IEND", ByteArray(0))
        return out.toByteArray()
    }

    private fun writeChunk(sink: ByteArrayOutputStream, type: String, data: ByteArray) {
        sink.writeInt(data.size)
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        sink.write(typeBytes); sink.write(data)
        val crc = CRC32()
        crc.update(typeBytes); crc.update(data)
        sink.writeInt(crc.value.toInt())
    }

    private fun deflate(data: ByteArray): ByteArray {
        val d = Deflater(Deflater.BEST_SPEED)
        d.setInput(data); d.finish()
        val buf = ByteArray(64 * 1024)
        val out = ByteArrayOutputStream()
        while (!d.finished()) out.write(buf, 0, d.deflate(buf))
        d.end()
        return out.toByteArray()
    }

    private fun ByteArrayOutputStream.writeInt(v: Int) {
        write((v ushr 24) and 0xFF)
        write((v ushr 16) and 0xFF)
        write((v ushr 8)  and 0xFF)
        write( v          and 0xFF)
    }
}
