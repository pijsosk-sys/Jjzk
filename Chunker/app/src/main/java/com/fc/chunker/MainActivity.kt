package com.fc.chunker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.fc.chunker.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream

/**
 * Chunker: pick any file, gzip it, split into N-MB parts you can upload
 * one per chat message. Parts go to app external files dir /chunks and can be
 * shared out. Reassemble on the receiving side:
 *     cat <name>.gz.part* > <name>.gz && gunzip <name>.gz
 */
class MainActivity : AppCompatActivity() {

    private lateinit var ui: ActivityMainBinding
    private var src: Uri? = null
    private var srcName: String = "file.bin"
    private val io = CoroutineScope(Dispatchers.IO)
    private var lastParts: List<File> = emptyList()

    private val pick = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            src = uri
            srcName = queryName(uri)
            ui.picked.text = "selected: $srcName"
            ui.goBtn.isEnabled = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = ActivityMainBinding.inflate(layoutInflater)
        setContentView(ui.root)

        ui.pickBtn.setOnClickListener { pick.launch(arrayOf("*/*")) }
        ui.goBtn.setOnClickListener { startChunk() }
        ui.shareBtn.setOnClickListener { shareParts() }
    }

    private fun startChunk() {
        val uri = src ?: return
        val mb = ui.chunkMb.text.toString().toDoubleOrNull() ?: 7.0
        val chunkBytes = (mb * 1024 * 1024).toLong().coerceAtLeast(1024)
        ui.goBtn.isEnabled = false
        ui.pickBtn.isEnabled = false
        ui.bar.visibility = android.view.View.VISIBLE
        ui.bar.isIndeterminate = true
        ui.log.text = ""
        ui.shareBtn.visibility = android.view.View.GONE

        io.launch {
            try {
                val parts = chunk(uri, srcName, chunkBytes)
                lastParts = parts
                withContext(Dispatchers.Main) {
                    log("done: ${parts.size} parts in\n${parts.first().parentFile?.absolutePath}")
                    log("\nreassemble on receiver:")
                    log("  cat $srcName.gz.part* > $srcName.gz && gunzip $srcName.gz")
                    ui.bar.visibility = android.view.View.GONE
                    ui.shareBtn.visibility = android.view.View.VISIBLE
                    ui.goBtn.isEnabled = true
                    ui.pickBtn.isEnabled = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    log("ERROR: ${e.message}")
                    ui.bar.visibility = android.view.View.GONE
                    ui.goBtn.isEnabled = true
                    ui.pickBtn.isEnabled = true
                }
            }
        }
    }

    /** gzip the input stream, splitting the compressed stream into fixed-size parts. */
    private fun chunk(uri: Uri, name: String, chunkBytes: Long): List<File> {
        val outDir = File(getExternalFilesDir(null), "chunks")
        outDir.listFiles()?.forEach { it.delete() }
        outDir.mkdirs()

        val md = MessageDigest.getInstance("SHA-256")
        val parts = ArrayList<File>()
        var index = 0
        var written = 0L
        var part = partFile(outDir, name, index)
        var out = part.outputStream().also { parts.add(part) }

        // split happens on the GZIP output by wrapping a splitting stream
        val splitting = object : java.io.OutputStream() {
            override fun write(b: Int) { write(byteArrayOf(b.toByte()), 0, 1) }
            override fun write(b: ByteArray, off: Int, len: Int) {
                var o = off; var remaining = len
                while (remaining > 0) {
                    if (written >= chunkBytes) {
                        out.flush(); out.close()
                        index++
                        part = partFile(outDir, name, index)
                        out = part.outputStream(); parts.add(part)
                        written = 0
                    }
                    val canWrite = (chunkBytes - written).coerceAtMost(remaining.toLong()).toInt()
                    out.write(b, o, canWrite)
                    o += canWrite; remaining -= canWrite; written += canWrite
                }
            }
        }

        contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input)
            val gz = GZIPOutputStream(splitting)
            val buf = ByteArray(64 * 1024)
            while (true) {
                val r = input.read(buf)
                if (r < 0) break
                md.update(buf, 0, r)
                gz.write(buf, 0, r)
            }
            gz.finish(); gz.flush()
        }
        out.flush(); out.close()

        val sha = md.digest().joinToString("") { "%02x".format(it) }
        File(outDir, "$name.manifest.txt").writeText(
            "original=$name\nsha256=$sha\nparts=${parts.size}\n" +
            "reassemble: cat $name.gz.part* > $name.gz && gunzip $name.gz\n"
        )
        return parts
    }

    private fun partFile(dir: File, name: String, idx: Int): File =
        File(dir, "%s.gz.part%03d".format(name, idx))

    private fun shareParts() {
        if (lastParts.isEmpty()) return
        val uris = ArrayList<Uri>()
        val auth = "$packageName.fileprovider"
        for (f in lastParts) uris.add(FileProvider.getUriForFile(this, auth, f))
        val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "application/octet-stream"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, "Share parts"))
    }

    private fun queryName(uri: Uri): String {
        var n = "file.bin"
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) n = c.getString(i)
        }
        return n
    }

    private fun log(s: String) { ui.log.append(s + "\n") }
}
