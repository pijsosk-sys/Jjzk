package com.boddy.os

import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.security.SecureRandom
import kotlin.concurrent.thread

/**
 * Lets Linux ask the phone to do things: open a link in the phone's browser, save or open a
 * file, or launch a phone app. Used by /opt/boddy/boddy-ctl (phone-open, phone-save, ...).
 *
 * Listens on 127.0.0.1:[PORT]. Requests are one line, "<token> <verb> <argument>", and get a
 * one-line reply starting with "ok" or "error". The token is regenerated on every start and
 * written inside the app's private storage, so other apps on the phone can't use the bridge.
 */
class PhoneBridge(private val tokenFile: File, private val phone: PhoneActions) {

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
                        val reply = handle(line.trim().split(" ", limit = 3), token)
                        client.getOutputStream().write("$reply\n".toByteArray())
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

    private fun handle(parts: List<String>, token: String): String {
        if (parts.size != 3 || parts[0] != token) return "error: bad request"
        val (_, verb, arg) = parts
        val request = when (verb) {
            "open" -> if (isWebLink(arg)) PhoneRequest.OpenLink(arg) else return "error: not a web link"
            "save" -> return phone.saveToDownloads(arg)
            "apps" -> return "ok: " + phone.listApps().joinToString("|")
            "openfile" -> PhoneRequest.OpenFile(arg)
            "launch" -> PhoneRequest.LaunchApp(arg)
            else -> return "error: unknown command $verb"
        }
        // Phone actions need the Boddy screen in front (Android blocks background apps from them).
        if (Boddy.phoneRequests.subscriptionCount.value == 0) return "error: open the Boddy desktop on the phone first"
        Boddy.phoneRequests.tryEmit(request)
        return "ok: sent to the phone"
    }

    private fun isWebLink(url: String) = url.startsWith("https://") || url.startsWith("http://")

    companion object {
        const val PORT = 7311
    }
}

/** Something Linux asked the phone to do. Paths are inside the Linux system. */
sealed class PhoneRequest {
    data class OpenLink(val url: String) : PhoneRequest()
    data class OpenFile(val path: String) : PhoneRequest()
    data class LaunchApp(val name: String) : PhoneRequest()
}
