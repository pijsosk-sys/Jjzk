package com.jjzk.toolbox.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.jjzk.toolbox.BackupService
import com.jjzk.toolbox.WorldRepository
import com.jjzk.toolbox.databinding.FragmentBackupsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class BackupsFragment : Fragment() {

    private var _b: FragmentBackupsBinding? = null
    private val b get() = _b!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _b = FragmentBackupsBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.backupNow.setOnClickListener { backupCurrent() }
        b.snapshotAll.setOnClickListener { snapshotEverything() }
        refresh()
    }

    override fun onResume() { super.onResume(); refresh() }

    override fun onDestroyView() { super.onDestroyView(); _b = null }

    private fun refresh() {
        val exports = WorldRepository.exportDirInApp(requireContext()).listFiles()
            ?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: emptyList()
        b.list.adapter = ArrayAdapter(requireContext(),
            android.R.layout.simple_list_item_1, exports.map { it.name })
        b.list.setOnItemClickListener { _, _, pos, _ ->
            val file = exports[pos]
            AlertDialog.Builder(requireContext())
                .setTitle(file.name)
                .setItems(arrayOf("Share", "Restore to Minecraft", "Delete")) { _, which ->
                    when (which) {
                        0 -> share(file)
                        1 -> restore(file)
                        2 -> { file.delete(); refresh() }
                    }
                }.show()
        }
    }

    private fun backupCurrent() {
        val world = CurrentWorld.world ?: run {
            Toast.makeText(requireContext(), "Pick a world from Home first", Toast.LENGTH_SHORT).show(); return
        }
        lifecycleScope.launch {
            val out = withContext(Dispatchers.IO) { BackupService.backupWorld(requireContext(), world) }
            Toast.makeText(requireContext(), "Backed up: ${out.name}", Toast.LENGTH_LONG).show()
            refresh()
        }
    }

    private fun snapshotEverything() {
        val install = CurrentWorld.install ?: return
        lifecycleScope.launch {
            val out = withContext(Dispatchers.IO) { BackupService.snapshotEverything(requireContext(), install) }
            Toast.makeText(requireContext(), "Snapshot: ${out.name}", Toast.LENGTH_LONG).show()
            refresh()
        }
    }

    private fun share(file: File) {
        val uri = FileProvider.getUriForFile(requireContext(), "com.jjzk.toolbox.fileprovider", file)
        startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).setType("application/zip")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            "Share backup"
        ))
    }

    private fun restore(file: File) {
        val install = CurrentWorld.install ?: return
        lifecycleScope.launch {
            val r = withContext(Dispatchers.IO) { BackupService.restore(install, file) }
            Toast.makeText(requireContext(), "Restored ${r.installed.size} item(s)", Toast.LENGTH_LONG).show()
        }
    }
}
