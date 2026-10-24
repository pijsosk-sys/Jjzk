package com.boddy.os

import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.security.SecureRandom
import kotlin.concurrent.thread

/**
 * Lets Linux ask the phone to do things, e.g. open a sign-in link in the phone's browser.
 *
 * Listens on 127.0.0.1:[PORT]. Requests are one line: "<token> open <url>". The token is
 * regenerated on every start and written inside the app's private storage, so other apps
 * on the phone can't use the bridge.
 */
class PhoneBridge(private val tokenFile: File) {

    private var server: ServerSocket? = null

    fun start() {
        if (server != null) return
        val token = ByteArray(24).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        tokenFile.parentFile?.mkdirs()
        tokenFile.writeText(token)

        val s = try {
            ServerSocket(PORT, 4, InetAddress.getByName("127.0.0.1"))
        } catch (e: Exception) {
            Boddy.log("Phone bridge unavailable: ${e.message}")
            return
        }
        server = s
        thread(name = "phone-bridge", isDaemon = true) {
            while (!s.isClosed) {
                try {
                    s.accept().use { client ->
                        client.soTimeout = 5000
                        val line = client.getInputStream().bufferedReader().readLine() ?: return@use
                        val parts = line.trim().split(" ", limit = 3)
                        if (parts.size == 3 && parts[0] == token && parts[1] == "open" && isWebLink(parts[2])) {
                            Boddy.openOnPhone.tryEmit(parts[2])
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    fun stop() {
        try { server?.close() } catch (_: Exception) {}
        server = null
    }

    private fun isWebLink(url: String) = url.startsWith("https://") || url.startsWith("http://")

    companion object {
        const val PORT = 7311
    }
}
