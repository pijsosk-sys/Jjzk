package com.boddy.os

import android.content.Context
import android.system.ErrnoException
import android.system.Os
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/** Downloads Debian, unpacks it, and runs the first-boot setup script. */
class Installer(private val context: Context, private val proot: Proot) {

    private val rootfs = proot.rootfs
    private val unpackedMarker = File(context.filesDir, ".rootfs-ok")

    private val setupDone = File(rootfs, "opt/boddy/.setup-done")

    /** True when setup finished with the current version of Boddy's scripts. */
    fun isInstalled() = setupDone.exists() && setupDone.readText().trim() == SETUP_VERSION.toString()

    /** True when Linux is already unpacked, so (re)installing only needs to run setup. */
    fun hasSystem() = unpackedMarker.exists()

    fun install() {
        if (!unpackedMarker.exists()) {
            val (url, sha256) = resolveRootfs()
            val tarball = File(context.cacheDir, "rootfs.tar.xz")
            download(url, tarball, sha256)
            unpack(tarball)
            tarball.delete()
            unpackedMarker.createNewFile()
        }
        configure()

        Boddy.status.value = "Step 3 of 4: Starting Linux…"
        Boddy.progress.value = -1
        val code = proot.run("/bin/bash", "/opt/boddy/setup.sh") { line ->
            Boddy.log(line)
            if (line.startsWith(">>> ")) Boddy.status.value = line.removePrefix(">>> ")
        }
        if (code != 0) throw IOException("Setup failed (exit code $code)")
        setupDone.writeText(SETUP_VERSION.toString())
    }

    /** Removes the Linux system so it can be installed again from scratch. */
    fun reset() {
        unpackedMarker.delete()
        deleteTree(rootfs)
    }

    /**
     * The prebuilt system is published as a release of this repo by the CI "rootfs" job, which
     * writes its address into assets/rootfs.txt at build time.
     */
    private fun resolveRootfs(): Pair<String, String?> {
        Boddy.status.value = "Step 1 of 4: Getting ready…"
        val url = try {
            context.assets.open("rootfs.txt").bufferedReader().readText().trim().ifEmpty { null }
        } catch (_: IOException) {
            null
        } ?: FALLBACK_ROOTFS_URL
        val sha = try {
            URL("$url.sha256").readText().trim().takeIf { it.length == 64 }
        } catch (_: IOException) {
            null
        }
        return url to sha
    }

    /**
     * Downloads in [PARTS] parallel ranges when the server supports it (GitHub does), which is
     * much faster on phone connections than one stream. Each part retries on its own.
     */
    private fun download(url: String, dest: File, sha256: String?) {
        Boddy.status.value = "Step 1 of 4: Downloading Boddy"
        Boddy.log("Downloading $url")
        dest.delete() // a leftover from an earlier attempt could leave junk at the end
        val total = contentLength(url)
        val done = AtomicLong(0)
        val report = { Boddy.progress.value = if (total > 0) (done.get() * 100 / total).toInt() else -1 }

        if (total <= 0) {
            downloadRange(url, dest, 0, -1, done, report)
        } else {
            RandomAccessFile(dest, "rw").use { it.setLength(total) }
            val partSize = (total + PARTS - 1) / PARTS
            val errors = mutableListOf<Throwable>()
            val workers = (0 until PARTS).map { i ->
                val start = i * partSize
                val end = minOf(total, start + partSize) - 1
                thread(name = "download-$i") {
                    try {
                        downloadRange(url, dest, start, end, done, report)
                    } catch (t: Throwable) {
                        synchronized(errors) { errors += t }
                    }
                }
            }
            workers.forEach { it.join() }
            errors.firstOrNull()?.let { throw it }
        }

        if (sha256 != null) {
            Boddy.status.value = "Step 1 of 4: Checking the download"
            val digest = MessageDigest.getInstance("SHA-256")
            dest.inputStream().use { input ->
                val buf = ByteArray(1 shl 20)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    digest.update(buf, 0, n)
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actual.equals(sha256, ignoreCase = true)) {
                dest.delete()
                throw IOException("Download was corrupted (checksum mismatch). Please try again.")
            }
        }
    }

    /** Size of the file, or -1 if the server doesn't support range requests. */
    private fun contentLength(url: String): Long {
        val conn = open(url, "bytes=0-0")
        try {
            if (conn.responseCode != 206) return -1
            return conn.getHeaderField("Content-Range")?.substringAfterLast('/')?.toLongOrNull() ?: -1
        } finally {
            conn.disconnect()
        }
    }

    /** Downloads bytes [start]..[end] (or everything if [end] < 0) into [dest], resuming on errors. */
    private fun downloadRange(url: String, dest: File, start: Long, end: Long, done: AtomicLong, report: () -> Unit) {
        var pos = start
        var attempts = 0
        while (end < 0 || pos <= end) {
            try {
                val conn = open(url, if (end < 0) null else "bytes=$pos-$end")
                if (conn.responseCode !in 200..299) throw IOException("Download failed: HTTP ${conn.responseCode}")
                RandomAccessFile(dest, "rw").use { out ->
                    out.seek(pos)
                    conn.inputStream.use { input ->
                        val buf = ByteArray(1 shl 16)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            pos += n
                            done.addAndGet(n.toLong())
                            report()
                        }
                    }
                }
                if (end < 0) return
                if (pos <= end) throw IOException("Connection ended early")
            } catch (e: IOException) {
                if (++attempts > 5 || end < 0) throw e
                Boddy.log("Download hiccup (${e.message}), resuming…")
                Thread.sleep(2000L * attempts)
            }
        }
    }

    private fun open(url: String, range: String?): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 30_000
            readTimeout = 60_000
            if (range != null) setRequestProperty("Range", range)
            connect()
        }

    private fun unpack(tarball: File) {
        Boddy.status.value = "Step 2 of 4: Unpacking"
        Boddy.progress.value = 0
        val staging = File(context.filesDir, "rootfs-staging")
        deleteTree(staging)
        deleteTree(rootfs)
        staging.mkdirs()

        val dirModes = mutableListOf<Pair<File, Int>>()
        // Progress = how much of the compressed file has been read.
        val compressed = ProgressInputStream(tarball.inputStream(), tarball.length())
        TarArchiveInputStream(BufferedInputStream(XZCompressorInputStream(BufferedInputStream(compressed, 1 shl 20)), 1 shl 20)).use { tar ->
            while (true) {
                val entry = tar.nextEntry ?: break
                val out = File(staging, entry.name)
                if (entry.name.split('/').contains("..")) continue
                out.parentFile?.mkdirs()
                when {
                    entry.isDirectory -> {
                        out.mkdirs()
                        dirModes += out to entry.mode
                    }
                    entry.isSymbolicLink -> {
                        out.delete()
                        Os.symlink(entry.linkName, out.path)
                    }
                    entry.isLink -> {
                        val target = File(staging, entry.linkName)
                        out.delete()
                        try {
                            Os.link(target.path, out.path)
                        } catch (_: ErrnoException) {
                            if (target.isFile) target.copyTo(out, overwrite = true)
                        }
                    }
                    entry.isFile -> {
                        out.outputStream().use { tar.copyTo(it, 1 shl 16) }
                        Os.chmod(out.path, (entry.mode and 0x1ff) or 0x180) // keep u+rw
                    }
                }
            }
        }
        // Apply directory permissions last so we could write into them while unpacking.
        dirModes.forEach { (dir, mode) -> Os.chmod(dir.path, (mode and 0x1ff) or 0x1c0) }

        // Some images wrap everything in a single top-level folder.
        val children = staging.listFiles().orEmpty()
        val top = if (children.size == 1 && children[0].isDirectory && !File(staging, "usr").exists()) children[0] else staging
        if (!top.renameTo(rootfs)) throw IOException("Couldn't move the unpacked system into place")
        deleteTree(staging)
    }

    /** Reports [Boddy.progress] as the underlying file is read. */
    private class ProgressInputStream(input: java.io.InputStream, private val total: Long) :
        java.io.FilterInputStream(input) {
        private var read = 0L

        override fun read(): Int = super.read().also { if (it >= 0) advance(1) }

        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) advance(it.toLong()) }

        private fun advance(n: Long) {
            read += n
            if (total > 0) Boddy.progress.value = (read * 100 / total).toInt()
        }
    }

    /** Files Boddy controls inside the rootfs; rewritten on every install so updates apply. */
    private fun configure() {
        proot.writeFakeProc()
        File(rootfs, "tmp").apply { mkdirs(); Os.chmod(path, 0x3ff) } // 1777

        File(rootfs, "etc/resolv.conf").apply {
            delete()
            writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n")
        }
        File(rootfs, "etc/hosts").writeText("127.0.0.1 localhost\n::1 localhost ip6-localhost\n")
        // apt drops to the _apt user for downloads, which can't open sockets under Android.
        File(rootfs, "etc/apt/apt.conf.d/99boddy").apply {
            parentFile?.mkdirs()
            writeText("APT::Sandbox::User \"root\";\nAcquire::Retries \"3\";\n")
        }

        copyScripts()
    }

    /** Copies Boddy's scripts into Linux. Also run on every desktop start so they match the app. */
    fun copyScripts() {
        val dir = File(rootfs, "opt/boddy").apply { mkdirs() }
        context.assets.list("boddy").orEmpty().forEach { name ->
            val out = File(dir, name)
            context.assets.open("boddy/$name").use { input -> out.outputStream().use { input.copyTo(it) } }
            Os.chmod(out.path, 0x1ed) // 755
        }
    }

    private fun deleteTree(file: File) {
        // Don't follow symlinks out of the rootfs (e.g. links to /proc).
        if (file.isDirectory && !isSymlink(file)) file.listFiles()?.forEach { deleteTree(it) }
        file.delete()
    }

    private fun isSymlink(file: File) = try {
        android.system.OsConstants.S_ISLNK(Os.lstat(file.path).st_mode)
    } catch (_: ErrnoException) {
        false
    }

    companion object {
        /** Bump when setup.sh or the theme changes so existing installs offer an update. */
        const val SETUP_VERSION = 10
        /** Used when the APK was built without CI (plain Debian; setup.sh installs the rest). */
        private const val FALLBACK_ROOTFS_URL =
            "https://github.com/pijsosk-sys/Jjzk/releases/download/rootfs-debian-trixie-1/debian-arm64.tar.xz"
        private const val PARTS = 4
    }
}
