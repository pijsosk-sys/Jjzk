package com.jjzk.toolbox.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jjzk.toolbox.MainActivity
import com.jjzk.toolbox.PackKind
import com.jjzk.toolbox.databinding.FragmentModsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ModsFragment : Fragment() {

    private var _b: FragmentModsBinding? = null
    private val b get() = _b!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _b = FragmentModsBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.importBtn.setOnClickListener {
            (activity as? MainActivity)?.pickFileForImport()
        }
        b.list.layoutManager = LinearLayoutManager(requireContext())
        refresh()
    }

    override fun onResume() { super.onResume(); refresh() }

    override fun onDestroyView() { super.onDestroyView(); _b = null }

    private fun refresh() {
        val install = CurrentWorld.install ?: run {
            b.empty.visibility = View.VISIBLE
            b.list.visibility = View.GONE
            return
        }
        b.empty.visibility = View.GONE
        b.list.visibility = View.VISIBLE
        lifecycleScope.launch {
            val groups = withContext(Dispatchers.IO) {
                listOf(PackKind.BEHAVIOR, PackKind.RESOURCE, PackKind.SKIN)
                    .map { it to install.subdir(it).listFiles()?.filter { f -> f.isDirectory }.orEmpty() }
            }
            b.list.adapter = PackAdapter(groups) { folder ->
                AlertDialog.Builder(requireContext())
                    .setTitle("Remove ${folder.name}?")
                    .setPositiveButton("Remove") { _, _ -> folder.deleteRecursively(); refresh() }
                    .setNegativeButton("Cancel", null).show()
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
            v.setTextColor(0xFFEEEEEE.toInt())
            return VH(v)
        }

        override fun getItemCount() = rows.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val r = rows[position]
            if (r.heading != null) {
                holder.text.text = r.heading
                holder.text.setOnLongClickListener(null)
            } else {
                holder.text.text = "   ${r.folder!!.name}"
                holder.text.setOnLongClickListener { onLongPress(r.folder); true }
            }
        }
    }
}
