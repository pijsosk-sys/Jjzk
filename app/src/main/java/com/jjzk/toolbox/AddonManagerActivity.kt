package com.jjzk.toolbox

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jjzk.toolbox.databinding.ActivityAddonsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AddonManagerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddonsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddonsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.list.layoutManager = LinearLayoutManager(this)
        refresh()
    }

    private fun refresh() {
        val install = GameLocator.primary(GameLocator.detect(this)) ?: run {
            binding.header.text = "Minecraft not installed"; return
        }
        binding.header.text = "Installed packs · ${install.versionName}"
        lifecycleScope.launch {
            val groups = withContext(Dispatchers.IO) {
                PackKind.values().filter { it != PackKind.WORLD && it != PackKind.TEMPLATE }
                    .map { it to install.subdir(it).listFiles()?.toList().orEmpty().filter { d -> d.isDirectory } }
            }
            binding.list.adapter = PackAdapter(groups) { folder ->
                AlertDialog.Builder(this@AddonManagerActivity)
                    .setTitle("Remove ${folder.name}?")
                    .setPositiveButton("Remove") { _, _ ->
                        folder.deleteRecursively(); refresh()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    private class PackAdapter(
        private val groups: List<Pair<PackKind, List<File>>>,
        private val onLongPress: (File) -> Unit
    ) : RecyclerView.Adapter<PackAdapter.VH>() {

        private data class Row(val heading: String?, val folder: File?)

        private val rows: List<Row> = buildList {
            groups.forEach { (kind, folders) ->
                add(Row("${kind.name} (${folders.size})", null))
                folders.forEach { add(Row(null, it)) }
            }
        }

        class VH(val text: TextView) : RecyclerView.ViewHolder(text)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_1, parent, false) as TextView
            return VH(v)
        }

        override fun getItemCount() = rows.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val row = rows[position]
            if (row.heading != null) {
                holder.text.text = row.heading
                holder.text.setTextAppearance(android.R.style.TextAppearance_Medium)
                holder.text.setOnLongClickListener(null)
            } else {
                holder.text.text = "   ${row.folder!!.name}"
                holder.text.setOnLongClickListener { onLongPress(row.folder); true }
            }
        }
    }
}
