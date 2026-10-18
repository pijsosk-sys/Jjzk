package com.jjzk.toolbox

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.jjzk.toolbox.databinding.ActivitySeedsBinding

class SeedPickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySeedsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySeedsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val install = GameLocator.primary(GameLocator.detect(this))
        val seeds = install?.let { SeedBrowser.forInstall(it) } ?: SeedBrowser.catalog
        binding.header.text = install?.let { "Seeds compatible with v${it.versionName}" }
            ?: "All known seeds (install Minecraft to filter)"
        binding.list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_2,
            android.R.id.text1, seeds.map { it.label }).also { adapter ->
                binding.list.setOnItemClickListener { _, _, pos, _ ->
                    val s = seeds[pos]
                    val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cb.setPrimaryClip(ClipData.newPlainText("seed", s.seed))
                    Toast.makeText(this, "Copied ${s.seed}", Toast.LENGTH_SHORT).show()
                }
            }
    }
}
