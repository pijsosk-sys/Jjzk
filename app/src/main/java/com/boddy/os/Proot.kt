package com.boddy.os

import android.content.Context
import java.io.File

/** Runs commands inside the Debian rootfs with proot (no root access needed). */
class Proot(private val context: Context) {

    val rootfs = File(context.filesDir, "debian")
    private val fakeProc = File(context.filesDir, "proc")
    private val tmp = File(context.cacheDir, "proot-tmp")
    private val libDir = context.applicationInfo.nativeLibraryDir

    fun process(vararg command: String): ProcessBuilder {
        tmp.mkdirs()
        val args = mutableListOf(
            "$libDir/libproot.so",
            "--kill-on-exit",
            "--link2symlink",
            "--root-id",
            "--sysvipc",
            "--rootfs=${rootfs.absolutePath}",
            "--bind=/dev",
            "--bind=/proc",
            "--bind=/sys",
            "--bind=/dev/urandom:/dev/random",
            "--bind=${rootfs.absolutePath}/tmp:/dev/shm",
        )
        // Android hides some /proc files from apps; substitute harmless fakes.
        for (name in FAKE_PROC.keys) {
            if (!File("/proc/$name").canRead()) args += "--bind=${File(fakeProc, name).absolutePath}:/proc/$name"
        }
        args += listOf(
            "--cwd=/root",
            "/usr/bin/env", "-i",
            "HOME=/root",
            "USER=root",
            "LANG=C.UTF-8",
            "TERM=xterm-256color",
            "TMPDIR=/tmp",
            "PATH=/root/.local/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
        )
        args += command

        return ProcessBuilder(args).redirectErrorStream(true).also {
            it.environment().apply {
                put("PROOT_LOADER", "$libDir/libproot-loader.so")
                put("PROOT_TMP_DIR", tmp.absolutePath)
                put("LD_LIBRARY_PATH", libDir)
            }
        }
    }

    /** Runs a command to completion, sending each output line to [onLine]. Returns the exit code. */
    fun run(vararg command: String, onLine: (String) -> Unit): Int {
        val p = process(*command).start()
        p.inputStream.bufferedReader().forEachLine(onLine)
        return p.waitFor()
    }

    fun writeFakeProc() {
        fakeProc.mkdirs()
        FAKE_PROC.forEach { (name, content) -> File(fakeProc, name).writeText(content) }
    }

    companion object {
        private val FAKE_PROC = mapOf(
            "loadavg" to "0.12 0.07 0.02 2/165 765\n",
            "uptime" to "1000.00 4000.00\n",
            "version" to "Linux version 6.1.0-boddy (boddy@android) #1 SMP PREEMPT\n",
            "stat" to """
                cpu  1957 0 2877 93280 262 342 254 87 0 0
                cpu0 31 0 226 12027 82 10 4 9 0 0
                intr 63361 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
                ctxt 38014093
                btime 1694292441
                processes 26442
                procs_running 1
                procs_blocked 0
                softirq 75663 0 5903 6 25375 10774 0 243 11685 0 21677
            """.trimIndent() + "\n",
            "vmstat" to "nr_free_pages 136341\nnr_inactive_anon 1016\nnr_active_anon 1160\n",
        )
    }
}
