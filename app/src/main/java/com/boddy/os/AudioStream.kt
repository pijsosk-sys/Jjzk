package com.boddy.os

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Plays Linux's sound on the phone. PulseAudio inside Linux (started by start-desktop.sh)
 * sends raw 44.1 kHz 16-bit stereo audio on 127.0.0.1:[PORT]; this copies it into an
 * [AudioTrack], so the phone's volume buttons control it.
 */
class AudioStream {

    @Volatile private var running = false
    private var worker: Thread? = null

    fun start() {
        if (running) return
        running = true
        worker = thread(name = "boddy-audio", isDaemon = true) {
            while (running) {
                try {
                    play()
                } catch (_: Exception) {
                    // Not up yet, or the desktop restarted: try again shortly.
                }
                if (running) Thread.sleep(1000)
            }
        }
    }

    fun stop() {
        running = false
        worker?.interrupt()
        worker = null
    }

    private fun play() {
        val minBuffer = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(minBuffer * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", PORT), 2000)
                socket.tcpNoDelay = true
                val input = socket.getInputStream()
                val buf = ByteArray(minBuffer)
                var have = 0
                track.play()
                while (running) {
                    val n = input.read(buf, have, buf.size - have)
                    if (n < 0) break
                    have += n
                    // TCP can split anywhere; only write whole 4-byte frames and keep the rest.
                    val whole = have - have % FRAME
                    if (whole > 0) {
                        track.write(buf, 0, whole)
                        System.arraycopy(buf, whole, buf, 0, have - whole)
                        have -= whole
                    }
                }
            }
        } finally {
            track.release()
        }
    }

    companion object {
        const val PORT = 4713
        private const val RATE = 44100
        private const val FRAME = 4 // 16-bit stereo
    }
}
