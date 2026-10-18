package com.jjzk.toolbox

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.jjzk.toolbox.databinding.ItemWorldBinding

class WorldAdapter(
    private val onOpen: (WorldSummary) -> Unit,
    private val onBackup: (WorldSummary) -> Unit,
    private val onDelete: (WorldSummary) -> Unit
) : RecyclerView.Adapter<WorldAdapter.VH>() {

    private val items = mutableListOf<WorldSummary>()

    fun submit(list: List<WorldSummary>) {
        items.clear(); items.addAll(list); notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemWorldBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val w = items[position]
        with(holder.b) {
            title.text = w.name
            subtitle.text = "${w.inferredEra.name} · ${w.sizePretty()} · ${w.lastPlayedPretty()}"
            root.setOnClickListener { onOpen(w) }
            backupBtn.setOnClickListener { onBackup(w) }
            deleteBtn.setOnClickListener { onDelete(w) }
        }
    }

    class VH(val b: ItemWorldBinding) : RecyclerView.ViewHolder(b.root)
}
