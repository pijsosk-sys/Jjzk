package com.jjzk.toolbox

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.jjzk.toolbox.databinding.ActivityBackupBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class BackupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBackupBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBackupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        refresh()
    }

    private fun refresh() {
        val exports = WorldRepository.exportDirInApp(this).listFiles()
            ?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: emptyList()
        binding.list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1,
            exports.map { it.name })
        binding.list.setOnItemClickListener { _, _, pos, _ ->
            val file = exports[pos]
            AlertDialog.Builder(this)
                .setTitle(file.name)
                .setItems(arrayOf("Share", "Restore to Minecraft", "Delete")) { _, which ->
                    when (which) {
                        0 -> share(file)
                        1 -> restore(file)
                        2 -> { file.delete(); refresh() }
                    }
                }
                .show()
        }
    }

    private fun share(file: File) {
        val uri = FileProvider.getUriForFile(this, "com.jjzk.toolbox.fileprovider", file)
        startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            "Share backup"
        ))
    }

    private fun restore(file: File) {
        val install = GameLocator.primary(GameLocator.detect(this)) ?: run {
            Toast.makeText(this, "Install Minecraft first", Toast.LENGTH_SHORT).show(); return
        }
        lifecycleScope.launch {
            val r = withContext(Dispatchers.IO) { BackupService.restore(install, file) }
            Toast.makeText(this@BackupActivity,
                "Restored ${r.installed.size} item(s)", Toast.LENGTH_LONG).show()
        }
    }
}
