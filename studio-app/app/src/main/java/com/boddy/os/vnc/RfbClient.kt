package com.boddy.os.vnc

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors

/**
 * Minimal RFB (VNC) 3.8 client: no authentication, 32-bit true colour,
 * Raw + CopyRect encodings. Good enough for a server on localhost.
 *
 * Pixels are kept as ARGB ints in [pixels], guarded by [lock].
 */
class RfbClient(private val listener: Listener) {

    interface Listener {
        fun onResize(width: Int, height: Int)
        fun onUpdate(x: Int, y: Int, w: Int, h: Int)
        fun onClipboard(text: String)
        fun onDisconnected(error: Throwable?)
    }

    val lock = Any()
    @Volatile var width = 0; private set
    @Volatile var height = 0; private set
    var pixels = IntArray(0); private set

    private var socket: Socket? = null
    private lateinit var input: DataInputStream
    private lateinit var output: DataOutputStream
    private val sender = Executors.newSingleThreadExecutor()
    @Volatile private var running = false
    private var buffer = ByteArray(0)

    /** Connects and performs the handshake. Blocking; call from a background thread. */
    fun connect(host: String, port: Int, timeoutMs: Int = 3000) {
        val s = Socket()
        s.tcpNoDelay = true
        s.connect(InetSocketAddress(host, port), timeoutMs)
        socket = s
        input = DataInputStream(BufferedInputStream(s.getInputStream(), 1 shl 16))
        output = DataOutputStream(BufferedOutputStream(s.getOutputStream()))
        handshake()
        running = true
    }

    /** Reads server messages until the connection closes. Blocking. */
    fun run() {
        var error: Throwable? = null
        try {
            requestUpdate(incremental = false)
            while (running) {
                when (val type = input.readUnsignedByte()) {
                    0 -> readFramebufferUpdate()
                    1 -> { // SetColourMapEntries: unused with true colour
                        input.skipFully(3)
                        val n = input.readUnsignedShort()
                        input.skipFully(n * 6)
                    }
                    2 -> Unit // Bell
                    3 -> {
                        input.skipFully(3)
                        val len = input.readInt()
                        val bytes = ByteArray(len)
                        input.readFully(bytes)
                        listener.onClipboard(String(bytes, Charsets.ISO_8859_1))
                    }
                    else -> throw IOException("Unknown server message $type")
                }
            }
        } catch (t: Throwable) {
            if (running) error = t
        } finally {
            close()
            listener.onDisconnected(error)
        }
    }

    fun close() {
        running = false
        try { socket?.close() } catch (_: IOException) {}
        sender.shutdownNow()
    }

    fun sendPointer(x: Int, y: Int, buttonMask: Int) = send {
        writeByte(5)
        writeByte(buttonMask)
        writeShort(x.coerceIn(0, maxOf(0, width - 1)))
        writeShort(y.coerceIn(0, maxOf(0, height - 1)))
    }

    fun sendKey(keysym: Int, down: Boolean) = send {
        writeByte(4)
        writeByte(if (down) 1 else 0)
        writeShort(0)
        writeInt(keysym)
    }

    fun sendClipboard(text: String) = send {
        val bytes = text.toByteArray(Charsets.ISO_8859_1)
        writeByte(6)
        write(ByteArray(3))
        writeInt(bytes.size)
        write(bytes)
    }

    private fun send(block: DataOutputStream.() -> Unit) {
        if (!running) return
        try {
            sender.execute {
                try {
                    synchronized(output) {
                        output.block()
                        output.flush()
                    }
                } catch (_: IOException) {
                }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
        }
    }

    private fun handshake() {
        val version = ByteArray(12)
        input.readFully(version)
        if (!String(version, Charsets.US_ASCII).startsWith("RFB ")) throw IOException("Not a VNC server")
        output.write("RFB 003.008\n".toByteArray(Charsets.US_ASCII))
        output.flush()

        val count = input.readUnsignedByte()
        if (count == 0) throw IOException("Server refused connection: ${readReason()}")
        val types = ByteArray(count)
        input.readFully(types)
        if (types.none { it.toInt() == 1 }) throw IOException("Server requires a password")
        output.writeByte(1)
        output.flush()
        if (input.readInt() != 0) throw IOException("Security handshake failed: ${readReason()}")

        output.writeByte(1) // ClientInit: shared session
        output.flush()

        val w = input.readUnsignedShort()
        val h = input.readUnsignedShort()
        input.skipFully(16) // server pixel format; we override it below
        input.skipFully(input.readInt()) // desktop name
        resize(w, h)

        output.apply {
            // SetPixelFormat: 32bpp little-endian, 0x00RRGGBB
            writeByte(0); write(ByteArray(3))
            writeByte(32); writeByte(24); writeByte(0); writeByte(1)
            writeShort(255); writeShort(255); writeShort(255)
            writeByte(16); writeByte(8); writeByte(0)
            write(ByteArray(3))
            // SetEncodings
            val encodings = intArrayOf(ENC_COPYRECT, ENC_RAW, ENC_CURSOR, ENC_DESKTOP_SIZE)
            writeByte(2); writeByte(0)
            writeShort(encodings.size)
            encodings.forEach { writeInt(it) }
            flush()
        }
    }

    private fun readReason(): String {
        val bytes = ByteArray(input.readInt())
        input.readFully(bytes)
        return String(bytes)
    }

    private fun requestUpdate(incremental: Boolean) {
        synchronized(output) {
            output.writeByte(3)
            output.writeByte(if (incremental) 1 else 0)
            output.writeShort(0); output.writeShort(0)
            output.writeShort(width); output.writeShort(height)
            output.flush()
        }
    }

    private fun readFramebufferUpdate() {
        input.skipFully(1)
        val rects = input.readUnsignedShort()
        repeat(rects) {
            val x = input.readUnsignedShort()
            val y = input.readUnsignedShort()
            val w = input.readUnsignedShort()
            val h = input.readUnsignedShort()
            when (val enc = input.readInt()) {
                ENC_RAW -> readRaw(x, y, w, h)
                ENC_COPYRECT -> copyRect(input.readUnsignedShort(), input.readUnsignedShort(), x, y, w, h)
                ENC_CURSOR -> input.skipFully(w * h * 4 + (w + 7) / 8 * h) // we draw our own cursor
                ENC_DESKTOP_SIZE -> resize(w, h)
                else -> throw IOException("Unsupported encoding $enc")
            }
        }
        requestUpdate(incremental = true)
    }

    private fun readRaw(x: Int, y: Int, w: Int, h: Int) {
        val rowBytes = w * 4
        if (buffer.size < rowBytes) buffer = ByteArray(rowBytes)
        val buf = buffer
        for (row in 0 until h) {
            input.readFully(buf, 0, rowBytes)
            synchronized(lock) {
                if (y + row >= height || x + w > width) return@synchronized
                var o = (y + row) * width + x
                var i = 0
                while (i < rowBytes) {
                    pixels[o++] = -0x1000000 or
                        ((buf[i + 2].toInt() and 0xff) shl 16) or
                        ((buf[i + 1].toInt() and 0xff) shl 8) or
                        (buf[i].toInt() and 0xff)
                    i += 4
                }
            }
        }
        listener.onUpdate(x, y, w, h)
    }

    private fun copyRect(sx: Int, sy: Int, x: Int, y: Int, w: Int, h: Int) {
        synchronized(lock) {
            if (sx + w > width || sy + h > height || x + w > width || y + h > height) return
            val tmp = IntArray(w * h)
            for (row in 0 until h) System.arraycopy(pixels, (sy + row) * width + sx, tmp, row * w, w)
            for (row in 0 until h) System.arraycopy(tmp, row * w, pixels, (y + row) * width + x, w)
        }
        listener.onUpdate(x, y, w, h)
    }

    private fun resize(w: Int, h: Int) {
        synchronized(lock) {
            width = w
            height = h
            pixels = IntArray(w * h)
        }
        listener.onResize(w, h)
    }

    private fun DataInputStream.skipFully(n: Int) {
        var left = n
        while (left > 0) {
            val skipped = skipBytes(left)
            if (skipped <= 0) { readByte(); left-- } else left -= skipped
        }
    }

    companion object {
        const val ENC_RAW = 0
        const val ENC_COPYRECT = 1
        const val ENC_CURSOR = -239
        const val ENC_DESKTOP_SIZE = -223

        const val BUTTON_LEFT = 1
        const val BUTTON_MIDDLE = 2
        const val BUTTON_RIGHT = 4
        const val WHEEL_UP = 8
        const val WHEEL_DOWN = 16
    }
}
