package com.jjzk.toolbox.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.jjzk.toolbox.R
import com.jjzk.toolbox.databinding.FragmentItemsBinding
import com.jjzk.toolbox.databinding.ItemItemCardBinding
import com.jjzk.toolbox.items.ItemCatalog
import com.jjzk.toolbox.items.McItem
import com.jjzk.toolbox.world.InventoryEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ItemsFragment : Fragment() {

    private var _b: FragmentItemsBinding? = null
    private val b get() = _b!!
    private var currentCategory: McItem.Category? = null
    private lateinit var adapter: ItemAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _b = FragmentItemsBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = ItemAdapter { onItemTap(it) }
        b.grid.layoutManager = GridLayoutManager(requireContext(), 2)
        b.grid.adapter = adapter

        val all = Chip(requireContext()).apply {
            text = "All"; isCheckable = true; isChecked = true
            setOnClickListener { currentCategory = null; refresh() }
        }
        b.chips.addView(all)
        ItemCatalog.categories.forEach { cat ->
            val chip = Chip(requireContext()).apply {
                text = cat.label; isCheckable = true
                setOnClickListener {
                    currentCategory = cat
                    for (i in 0 until b.chips.childCount) (b.chips.getChildAt(i) as Chip).isChecked = false
                    isChecked = true
                    refresh()
                }
            }
            b.chips.addView(chip)
        }

        b.search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = refresh()
        })

        refresh()
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }

    private fun refresh() {
        val query = b.search.text?.toString().orEmpty()
        adapter.submit(ItemCatalog.search(query, currentCategory))
    }

    private fun onItemTap(item: McItem) {
        val world = CurrentWorld.world ?: run {
            Toast.makeText(requireContext(), "Pick a world from Home first", Toast.LENGTH_SHORT).show(); return
        }
        val counts = (1..item.maxStack).toList()
        AlertDialog.Builder(requireContext())
            .setTitle(item.displayName)
            .setMessage(item.id)
            .setSingleChoiceItems(
                ArrayAdapter(requireContext(), android.R.layout.simple_list_item_single_choice,
                    counts.map { "x$it" }),
                item.maxStack - 1
            ) { d, which ->
                val count = counts[which]
                d.dismiss()
                lifecycleScope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        InventoryEditor.give(world, InventoryEditor.Give(item, count))
                    }
                    Toast.makeText(requireContext(),
                        if (ok) "Added $count × ${item.displayName}. Launch the world to see it."
                        else "Inventory full or no player data yet (open the world once first).",
                        Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private class ItemAdapter(val onTap: (McItem) -> Unit)
        : RecyclerView.Adapter<ItemAdapter.VH>() {

        private val rows = mutableListOf<McItem>()

        fun submit(list: List<McItem>) { rows.clear(); rows += list; notifyDataSetChanged() }

        override fun getItemCount() = rows.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemItemCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(b)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val m = rows[position]
            with(holder.b) {
                name.text = m.displayName
                category.text = m.category.label
                id.text = m.id
                root.setOnClickListener { onTap(m) }
            }
        }

        class VH(val b: ItemItemCardBinding) : RecyclerView.ViewHolder(b.root)
    }
}
