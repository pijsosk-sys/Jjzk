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
            "split" -> PhoneRequest.LaunchApp(arg, split = true)
            "screen", "ui", "do", "key", "type", "status" -> return control(verb, arg)
            else -> return "error: unknown command $verb"
        }
        // Phone actions need the Boddy screen in front (Android blocks background apps from them).
        if (Boddy.phoneRequests.subscriptionCount.value == 0) return "error: open the Boddy desktop on the phone first"
        Boddy.phoneRequests.tryEmit(request)
        return "ok: sent to the phone"
    }

    /** Seeing and touching other apps, through the [PhoneControl] accessibility helper. */
    private fun control(verb: String, arg: String): String {
        val hands = PhoneControl.instance
            ?: return "error: phone control is off. If the Boddy app's home screen shows " +
                "\"Let AI control phone apps\", ask the user to tap it and turn on Boddy in " +
                "Accessibility settings. If it does not, this build was made without phone control."
        return try {
            when (verb) {
                "status" -> "ok: phone control on${if (hands.paused) " (PAUSED by the user)" else ""}, " +
                    "screen ${hands.sizeText()}, app on screen: ${hands.foregroundApp()}"
                "screen" -> {
                    val (path, mode) = (arg.split(" ", limit = 2) + "").let { it[0] to it[1] }
                    val out = phone.linuxOutput(path) ?: return "error: bad path $path"
                    hands.screenshot(out, grid = mode == "grid")
                }
                "ui" -> {
                    val out = phone.linuxOutput(arg) ?: return "error: bad path $arg"
                    hands.dumpUi(out)
                }
                "key" -> hands.globalAction(arg).also { hands.showActivity(arg) }
                "type" -> hands.type(arg).also { hands.showActivity("typing") }
                else -> runScript(hands, arg)
            }
        } catch (e: Exception) {
            "error: ${e.message}"
        }
    }

    /**
     * Runs a burst of moves without a round trip per move. Steps are separated by ";" and run
     * one after another; "wait MS" pauses. A step can be several fingers at once joined by "+":
     *   tap X Y | hold X Y MS | swipe X1 Y1 X2 Y2 MS   (each may end with @MS: start delay)
     * e.g. "hold 200 600 3000 + swipe 1200 400 1400 400 600@500; wait 300; tap 1400 500"
     */
    private fun runScript(hands: PhoneControl, script: String): String {
        val steps = script.split(";").map { it.trim() }.filter { it.isNotEmpty() }
        if (steps.isEmpty()) return "error: nothing to do"
        hands.showActivity(script.take(40))
        var total = 0L
        for ((i, step) in steps.withIndex()) {
            if (hands.paused) return PhoneControl.PAUSED
            val words = step.split(Regex("\\s+"))
            if (words[0] == "wait") {
                val ms = words.getOrNull(1)?.toLongOrNull()?.coerceIn(0, 10_000)
                    ?: return "error: step ${i + 1}: use \"wait MS\""
                total += ms
                Thread.sleep(ms)
                continue
            }
            val strokes = step.split("+").map { finger ->
                parseFinger(finger.trim()) ?: return "error: step ${i + 1}: can't read \"${finger.trim()}\" " +
                    "(use tap X Y, hold X Y MS, swipe X1 Y1 X2 Y2 MS, wait MS)"
            }
            total += strokes.maxOf { it.startMs + it.durationMs }
            if (total > 60_000) return "error: bursts can be at most 60 seconds long"
            hands.gesture(strokes)?.let { return "$it (at step ${i + 1})" }
        }
        return "ok: done ${steps.size} step(s)"
    }

    private fun parseFinger(text: String): PhoneControl.Stroke? {
        var words = text.split(Regex("\\s+"))
        var start = 0L
        words.last().takeIf { it.startsWith("@") }?.let {
            start = it.drop(1).toLongOrNull()?.coerceIn(0, 30_000) ?: return null
            words = words.dropLast(1)
        }
        val n = words.drop(1).map { it.toFloatOrNull() ?: return null }
        return when {
            words[0] == "tap" && n.size == 2 -> PhoneControl.Stroke(listOf(n[0] to n[1]), start, 60)
            words[0] == "hold" && n.size == 3 -> PhoneControl.Stroke(listOf(n[0] to n[1]), start, n[2].toLong())
            words[0] == "swipe" && n.size == 5 ->
                PhoneControl.Stroke(listOf(n[0] to n[1], n[2] to n[3]), start, n[4].toLong())
            else -> null
        }
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
    data class LaunchApp(val name: String, val split: Boolean = false) : PhoneRequest()
}
