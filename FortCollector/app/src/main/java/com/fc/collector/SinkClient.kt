package com.fc.collector

import android.util.Log
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Pushes JSON lines to your sink over WebSocket.
 * Non-blocking: queues while disconnected, drains on open, auto-reconnects.
 * ws://10.0.2.2:PORT reaches the host loopback from an emulator/redroid guest.
 */
class SinkClient(private val url: String) {

    private val queue = ConcurrentLinkedQueue<String>()
    private val open = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    @Volatile private var ws: WebSocketClient? = null

    fun connectAsync() {
        if (closed.get()) return
        val client = object : WebSocketClient(URI(url)) {
            override fun onOpen(h: ServerHandshake?) {
                open.set(true)
                drain()
            }
            override fun onMessage(message: String?) {}
            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                open.set(false)
                scheduleReconnect()
            }
            override fun onError(ex: Exception?) {
                Log.w("FortCollector", "ws err: ${ex?.message}")
                open.set(false)
            }
        }
        client.connectionLostTimeout = 15
        ws = client
        try { client.connect() } catch (e: Exception) { scheduleReconnect() }
    }

    private fun scheduleReconnect() {
        if (closed.get()) return
        Thread {
            Thread.sleep(2000)
            if (!closed.get() && !open.get()) connectAsync()
        }.start()
    }

    fun send(line: String) {
        if (closed.get()) return
        queue.add(line)
        if (open.get()) drain()
    }

    private fun drain() {
        val c = ws ?: return
        while (open.get()) {
            val line = queue.poll() ?: break
            try { c.send(line) } catch (e: Exception) { queue.add(line); break }
        }
    }

    fun close() {
        closed.set(true)
        open.set(false)
        try { ws?.close() } catch (_: Exception) {}
        ws = null
    }
}
