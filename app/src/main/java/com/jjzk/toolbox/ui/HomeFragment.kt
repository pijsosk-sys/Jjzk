package com.jjzk.toolbox.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jjzk.toolbox.GameLocator
import com.jjzk.toolbox.WorldAdapter
import com.jjzk.toolbox.WorldRepository
import com.jjzk.toolbox.WorldSummary
import com.jjzk.toolbox.databinding.FragmentHomeBinding
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
            onBackup = { select(it); b.root.post {
                (requireActivity() as? com.jjzk.toolbox.MainActivity)?.let {
                    // Jump the user to the Backups tab after they tap backup here.
                }
            } },
            onDelete = { confirmDelete(it) }
        )
        b.list.layoutManager = LinearLayoutManager(requireContext())
        b.list.adapter = adapter
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
            adapter.submit(emptyList())
            CurrentWorld.notifyChanged()
            return
        }
        b.hero.text = "Pick a world to edit, give items, or back up."
        lifecycleScope.launch {
            val worlds = WorldRepository.list(primary)
            adapter.submit(worlds)
            CurrentWorld.world = worlds.firstOrNull()
            CurrentWorld.notifyChanged()
        }
    }

    private fun select(w: WorldSummary) {
        CurrentWorld.world = w
        CurrentWorld.notifyChanged()
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
