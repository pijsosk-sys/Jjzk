package com.jjzk.toolbox

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.jjzk.toolbox.databinding.ActivityWorldDetailBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class WorldDetailActivity : AppCompatActivity() {

    companion object { const val EXTRA_FOLDER = "folder" }

    private lateinit var binding: ActivityWorldDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWorldDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val folder = File(intent.getStringExtra(EXTRA_FOLDER) ?: run { finish(); return })
        load(folder)
        binding.renameBtn.setOnClickListener { commitRename(folder) }
    }

    private fun load(folder: File) {
        lifecycleScope.launch {
            val data = withContext(Dispatchers.IO) { LevelDat.read(File(folder, "level.dat")) }
            binding.name.setText(data.levelName)
            binding.detail.text = buildString {
                appendLine("Folder: ${folder.name}")
                appendLine("Game mode: ${gameMode(data.gameType)}")
                appendLine("Storage version: ${data.storageVersion}")
                appendLine("Last opened with: ${data.lastOpenedMajor}.${data.lastOpenedMinor}.${data.lastOpenedPatch}.${data.lastOpenedBuild}")
                appendLine("Last played: ${data.lastPlayed}")
            }
        }
    }

    private fun commitRename(folder: File) {
        val newName = binding.name.text.toString().trim().ifBlank { return }
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                File(folder, "levelname.txt").writeText(newName)
                LevelDat.writeName(File(folder, "level.dat"), newName)
            }
            finish()
        }
    }

    private fun gameMode(t: Int) = when (t) {
        0 -> "Survival"; 1 -> "Creative"; 2 -> "Adventure"; 5 -> "Spectator"; else -> "Mode $t"
    }
}
