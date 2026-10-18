package com.jjzk.toolbox

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.jjzk.toolbox.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var current: GameInstall? = null
    private lateinit var adapter: WorldAdapter

    private val openPack = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        importFromUri(uri)
    }

    private val requestPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = WorldAdapter(
            onOpen   = { openWorld(it) },
            onBackup = { backup(it) },
            onDelete = { confirmDelete(it) }
        )
        binding.worldList.layoutManager = LinearLayoutManager(this)
        binding.worldList.adapter = adapter

        binding.importBtn.setOnClickListener {
            openPack.launch(arrayOf("*/*"))
        }
        binding.addonsBtn.setOnClickListener {
            startActivity(Intent(this, AddonManagerActivity::class.java))
        }
        binding.seedsBtn.setOnClickListener {
            startActivity(Intent(this, SeedPickerActivity::class.java))
        }
        binding.backupAllBtn.setOnClickListener { snapshotAll() }

        ensureStorage()
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val data = intent?.data ?: return
        importFromUri(data)
    }

    private fun ensureStorage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    startActivity(
                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            .setData(Uri.parse("package:$packageName"))
                    )
                } catch (_: Throwable) {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
                return
            }
            refresh(); return
        }
        val perms = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        val missing = perms.any {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing) requestPerms.launch(perms) else refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val installs = GameLocator.detect(this)
        val primary = GameLocator.primary(installs)
        current = primary
        if (primary == null) {
            binding.header.text = "Minecraft Bedrock not detected on this device"
            binding.worldList.visibility = View.GONE
            binding.emptyState.visibility = View.VISIBLE
            adapter.submit(emptyList())
            return
        }
        binding.header.text = "${primary.pkg} · v${primary.versionName} · ${primary.era.name}"
        binding.worldList.visibility = View.VISIBLE
        binding.emptyState.visibility = View.GONE
        lifecycleScope.launch {
            val worlds = WorldRepository.list(primary)
            adapter.submit(worlds)
            if (worlds.isEmpty()) {
                binding.worldList.visibility = View.GONE
                binding.emptyState.visibility = View.VISIBLE
                binding.emptyState.text = "No worlds yet. Launch Minecraft once, then come back."
            }
        }
    }

    private fun openWorld(world: WorldSummary) {
        startActivity(
            Intent(this, WorldDetailActivity::class.java)
                .putExtra(WorldDetailActivity.EXTRA_FOLDER, world.folder.absolutePath)
        )
    }

    private fun backup(world: WorldSummary) {
        val install = current ?: return
        lifecycleScope.launch {
            val out = withContext(Dispatchers.IO) { BackupService.backupWorld(this@MainActivity, world) }
            Toast.makeText(this@MainActivity, "Backed up to ${out.absolutePath}", Toast.LENGTH_LONG).show()
        }
    }

    private fun confirmDelete(world: WorldSummary) {
        AlertDialog.Builder(this)
            .setTitle("Delete ${world.name}?")
            .setMessage("This removes the world folder from Minecraft. Back up first if you aren't sure.")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    WorldRepository.delete(world); refresh()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun importFromUri(uri: Uri) {
        val install = current ?: run {
            Toast.makeText(this, "Install Minecraft first", Toast.LENGTH_SHORT).show(); return
        }
        val name = queryDisplayName(uri)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                PackInstaller.install(install, PackInstaller.Source.ContentUri(this@MainActivity, uri, name))
            }
            val msg = buildString {
                append("Installed ${result.installed.size} item(s)")
                if (result.skipped.isNotEmpty()) append("; skipped ${result.skipped.size}")
            }
            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
            refresh()
        }
    }

    private fun snapshotAll() {
        val install = current ?: return
        lifecycleScope.launch {
            val out = withContext(Dispatchers.IO) { BackupService.snapshotEverything(this@MainActivity, install) }
            Toast.makeText(this@MainActivity, "Snapshot: ${out.absolutePath}", Toast.LENGTH_LONG).show()
        }
    }

    private fun queryDisplayName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0) ?: fallbackName(uri)
        }
        return fallbackName(uri)
    }

    private fun fallbackName(uri: Uri): String {
        val last = uri.lastPathSegment ?: "import.mcpack"
        return if (last.contains('.')) last else "$last.mcpack"
    }
}
