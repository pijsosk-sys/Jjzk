package com.boddy.os

import android.content.Context
import android.system.ErrnoException
import android.system.Os
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Downloads Debian, unpacks it, and runs the first-boot setup script. */
class Installer(private val context: Context, private val proot: Proot) {

    private val rootfs = proot.rootfs
    private val unpackedMarker = File(context.filesDir, ".rootfs-ok")

    fun isInstalled() = File(rootfs, "opt/boddy/.setup-done").exists()

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

        Boddy.status.value = "Installing the desktop, Firefox and Claude Code…"
        Boddy.progress.value = -1
        val code = proot.run("/bin/bash", "/opt/boddy/setup.sh") { Boddy.log(it) }
        if (code != 0) throw IOException("Setup failed (exit code $code). Check the log, then tap Install to retry.")
    }

    /** Removes the Linux system so it can be installed again from scratch. */
    fun reset() {
        unpackedMarker.delete()
        deleteTree(rootfs)
    }

    /** The base system is published as a release of this repo by the CI "rootfs" job. */
    private fun resolveRootfs(): Pair<String, String?> {
        Boddy.status.value = "Getting ready to download…"
        val sha = try {
            URL("$ROOTFS_URL.sha256").readText().trim().takeIf { it.length == 64 }
        } catch (_: IOException) {
            null
        }
        return ROOTFS_URL to sha
    }

    private fun download(url: String, dest: File, sha256: String?) {
        Boddy.status.value = "Downloading Debian…"
        Boddy.log("Downloading $url")
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connect()
        if (conn.responseCode !in 200..299) throw IOException("Download failed: HTTP ${conn.responseCode}")
        val total = conn.contentLengthLong
        val digest = MessageDigest.getInstance("SHA-256")
        var done = 0L
        conn.inputStream.use { input ->
            dest.outputStream().use { out ->
                val buf = ByteArray(1 shl 16)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    digest.update(buf, 0, n)
                    done += n
                    if (total > 0) Boddy.progress.value = (done * 100 / total).toInt()
                }
            }
        }
        if (sha256 != null) {
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actual.equals(sha256, ignoreCase = true)) {
                dest.delete()
                throw IOException("Download was corrupted (checksum mismatch). Please try again.")
            }
        }
    }

    private fun unpack(tarball: File) {
        Boddy.status.value = "Unpacking Debian…"
        Boddy.progress.value = -1
        val staging = File(context.filesDir, "rootfs-staging")
        deleteTree(staging)
        deleteTree(rootfs)
        staging.mkdirs()

        val dirModes = mutableListOf<Pair<File, Int>>()
        TarArchiveInputStream(XZCompressorInputStream(BufferedInputStream(tarball.inputStream(), 1 shl 16))).use { tar ->
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
                            target.copyTo(out, overwrite = true)
                        }
                    }
                    entry.isFile -> {
                        out.outputStream().use { tar.copyTo(it) }
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
        private const val ROOTFS_URL =
            "https://github.com/pijsosk-sys/Jjzk/releases/download/rootfs-debian-trixie-1/debian-arm64.tar.xz"
    }
}
