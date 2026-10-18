package com.jjzk.toolbox.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.jjzk.toolbox.GameLocator
import com.jjzk.toolbox.WorldAdapter
import com.jjzk.toolbox.WorldRepository
import com.jjzk.toolbox.WorldSummary
import com.jjzk.toolbox.databinding.FragmentHomeBinding
import com.jjzk.toolbox.overlay.OverlayService
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _b: FragmentHomeBinding? = null
    private val b get() = _b!!
    private lateinit var adapter: WorldAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _b = FragmentHomeBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = WorldAdapter(
            onOpen = { select(it) },
            onBackup = { select(it) },
            onDelete = { confirmDelete(it) }
        )
        b.list.layoutManager = LinearLayoutManager(requireContext())
        b.list.adapter = adapter
        b.launchMc.setOnClickListener { launchMinecraft() }
        refresh()
    }

    override fun onResume() { super.onResume(); refresh() }

    override fun onDestroyView() { super.onDestroyView(); _b = null }

    private fun refresh() {
        val installs = GameLocator.detect(requireContext())
        val primary = GameLocator.primary(installs)
        CurrentWorld.install = primary
        if (primary == null) {
            b.hero.text = "Install Minecraft Bedrock, then reopen Toolbox."
            b.launchMc.isEnabled = false
            adapter.submit(emptyList())
            CurrentWorld.notifyChanged()
            return
        }
        b.hero.text = "Pick a world first, then tap Launch — the overlay drops in on top."
        b.launchMc.isEnabled = true
        lifecycleScope.launch {
            val worlds = WorldRepository.list(primary)
            adapter.submit(worlds)
            CurrentWorld.world = CurrentWorld.world ?: worlds.firstOrNull()
            CurrentWorld.notifyChanged()
        }
    }

    private fun launchMinecraft() {
        val install = CurrentWorld.install ?: return
        val launch = requireContext().packageManager.getLaunchIntentForPackage(install.pkg)
        if (launch == null) {
            Toast.makeText(requireContext(), "Minecraft launch intent unavailable", Toast.LENGTH_SHORT).show()
            return
        }
        if (!canDrawOverlays()) { requestOverlayPermission(); return }
        OverlayService.start(requireContext())
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launch)
    }

    private fun canDrawOverlays(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            Settings.canDrawOverlays(requireContext())

    private fun requestOverlayPermission() {
        AlertDialog.Builder(requireContext())
            .setTitle("Enable overlay")
            .setMessage("Grant 'Display over other apps' so the Toolbox menu floats above Minecraft, then tap Launch again.")
            .setPositiveButton("Open settings") { _, _ ->
                startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${requireContext().packageName}"))
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun select(w: WorldSummary) {
        CurrentWorld.world = w; CurrentWorld.notifyChanged()
        Toast.makeText(requireContext(), "Selected ${w.name}", Toast.LENGTH_SHORT).show()
    }

    private fun confirmDelete(w: WorldSummary) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete ${w.name}?")
            .setMessage("The world folder is removed from Minecraft. Back it up first if unsure.")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch { WorldRepository.delete(w); refresh() }
            }
            .setNegativeButton("Cancel", null).show()
    }
}
