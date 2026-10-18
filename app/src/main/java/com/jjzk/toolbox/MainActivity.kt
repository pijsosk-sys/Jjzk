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
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.jjzk.toolbox.ui.BackupsFragment
import com.jjzk.toolbox.ui.CurrentWorld
import com.jjzk.toolbox.ui.HomeFragment
import com.jjzk.toolbox.ui.ItemsFragment
import com.jjzk.toolbox.ui.ModsFragment
import com.jjzk.toolbox.ui.WorldFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private val openPack = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        importFromUri(uri)
    }

    private val requestPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { syncHeader() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById<Toolbar>(R.id.toolbar))

        val nav = findViewById<BottomNavigationView>(R.id.bottomNav)
        nav.setOnItemSelectedListener { show(it.itemId); true }
        if (savedInstanceState == null) {
            nav.selectedItemId = R.id.nav_home
            show(R.id.nav_home)
        }
        CurrentWorld.listeners += ::syncHeader
        ensureStorage()
        handleIncomingIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        CurrentWorld.listeners -= ::syncHeader
    }

    private fun show(itemId: Int) {
        val f: Fragment = when (itemId) {
            R.id.nav_items   -> ItemsFragment()
            R.id.nav_world   -> WorldFragment()
            R.id.nav_mods    -> ModsFragment()
            R.id.nav_backups -> BackupsFragment()
            else             -> HomeFragment()
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, f).commitAllowingStateLoss()
    }

    private fun syncHeader() {
        val install = CurrentWorld.install
        val world = CurrentWorld.world
        supportActionBar?.title = world?.name ?: "Toolbox"
        supportActionBar?.subtitle = install?.let {
            "${it.versionName} · ${it.era.name.lowercase().replace('_', ' ')}"
        } ?: "No Minecraft install"
    }

    override fun onResume() {
        super.onResume()
        syncHeader()
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
            syncHeader(); return
        }
        val perms = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        val missing = perms.any {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing) requestPerms.launch(perms) else syncHeader()
    }

    fun pickFileForImport() = openPack.launch(arrayOf("*/*"))

    private fun importFromUri(uri: Uri) {
        val install = CurrentWorld.install ?: GameLocator.primary(GameLocator.detect(this))
        if (install == null) {
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
            CurrentWorld.notifyChanged()
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
