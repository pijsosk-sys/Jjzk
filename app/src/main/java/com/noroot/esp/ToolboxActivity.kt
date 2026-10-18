package com.noroot.esp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File

/**
 * Toolbox-style companion for Minecraft Bedrock 1.21+.
 *
 * Three tabs of helpers that don't touch the game's process (no bans on
 * single-player or non-strict servers; strict PvP servers may still flag
 * x-ray style packs server-side if they inspect resource-pack hashes).
 *
 * - Commands: curated /give, /gamemode, /tp, /effect, /time snippets.
 *   Tap to copy to clipboard, paste into Minecraft chat (needs cheats ON
 *   when the world was created).
 * - Packs: bundled .mcpack files in assets/packs/ get copied to cache
 *   and handed to Minecraft via a FileProvider intent, which triggers
 *   the standard pack-install prompt in-game.
 * - Give: pick an item from a list, pick count + enchant, generates the
 *   /give line and copies it.
 */
class ToolboxActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout
    private lateinit var clip: ClipboardManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Minecraft Toolbox"
        clip = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        val scroll = android.widget.ScrollView(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(32))
        }
        scroll.addView(root)
        setContentView(scroll)

        header("Quick Commands  (tap to copy, paste in chat)")
        for ((label, cmd) in QUICK_COMMANDS) addCommandRow(label, cmd)

        divider()
        header("Give — All Items")
        buildGiveBuilder()

        divider()
        header("Resource / Behavior Packs")
        for ((label, assetName, note) in PACKS) addPackRow(label, assetName, note)

        divider()
        header("World Access  (modify saves)")
        addInfoRow(
            "Minecraft Bedrock 1.21+ worlds live at:\n" +
            "/sdcard/Android/data/com.mojang.minecraftpe/files/games/com.mojang/minecraftWorlds/\n" +
            "Android 11+ scoped storage blocks other apps from reading this " +
            "without SAF. Use Minecraft's own Export/Import for world edits, " +
            "or install an Add-On that modifies them in-game."
        )
    }

    private fun addCommandRow(label: String, cmd: String) {
        val lbl = TextView(this).apply {
            text = label; textSize = 15f
            setPadding(0, dp(10), 0, dp(2))
        }
        val code = TextView(this).apply {
            text = cmd; typeface = android.graphics.Typeface.MONOSPACE
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setBackgroundColor(0xFF222222.toInt()); setTextColor(0xFF9EFF9E.toInt())
            textSize = 13f
        }
        val btn = Button(this).apply {
            text = "Copy"
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) }
            setOnClickListener { copyToClip(cmd, label) }
        }
        root.addView(lbl); root.addView(code); root.addView(btn)
    }

    private fun buildGiveBuilder() {
        val itemBox = EditText(this).apply {
            hint = "item id, e.g. diamond_sword, elytra, netherite_ingot"
            setText("diamond_sword")
        }
        val countBox = EditText(this).apply {
            hint = "count 1-64"
            setText("1"); inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(itemBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f))
            addView(countBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        val out = TextView(this).apply {
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setBackgroundColor(0xFF222222.toInt()); setTextColor(0xFF9EFF9E.toInt())
            textSize = 13f
            text = "/give @p minecraft:diamond_sword 1"
        }
        fun rebuild() {
            val id = itemBox.text.toString().trim().ifEmpty { "diamond_sword" }
            val n = countBox.text.toString().trim().toIntOrNull()?.coerceIn(1, 64) ?: 1
            val ns = if (id.contains(":")) id else "minecraft:$id"
            out.text = "/give @p $ns $n"
        }
        val watcher = object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) = rebuild()
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        }
        itemBox.addTextChangedListener(watcher); countBox.addTextChangedListener(watcher)

        val copy = Button(this).apply {
            text = "Copy /give"
            setOnClickListener { copyToClip(out.text.toString(), "give") }
        }
        root.addView(row); root.addView(out); root.addView(copy)
    }

    private fun addPackRow(label: String, assetName: String, note: String) {
        val lbl = TextView(this).apply {
            text = label; textSize = 15f
            setPadding(0, dp(10), 0, dp(2))
        }
        val sub = TextView(this).apply {
            text = note; textSize = 12f; setTextColor(0xFF999999.toInt())
        }
        val btn = Button(this).apply {
            text = "Install in Minecraft"
            setOnClickListener { installPack(assetName) }
        }
        root.addView(lbl); root.addView(sub); root.addView(btn)
    }

    private fun installPack(assetName: String) {
        try {
            val dest = File(cacheDir, assetName)
            assets.open("packs/$assetName").use { input ->
                dest.outputStream().use { out -> input.copyTo(out) }
            }
            val uri: Uri = FileProvider.getUriForFile(
                this, "$packageName.fileprovider", dest
            )
            val mime = if (assetName.endsWith(".mcworld")) "application/octet-stream"
                       else "application/octet-stream"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(Intent.createChooser(intent, "Open with Minecraft"))
        } catch (t: Throwable) {
            Toast.makeText(this, "install failed: ${t.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun addInfoRow(text: String) {
        val tv = TextView(this).apply {
            this.text = text; textSize = 13f; setTextColor(0xFFBBBBBB.toInt())
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }
        root.addView(tv)
    }

    private fun header(t: String) {
        val tv = TextView(this).apply {
            text = t; textSize = 17f; typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, dp(16), 0, dp(8))
        }
        root.addView(tv)
    }

    private fun divider() {
        val v = View(this).apply { setBackgroundColor(0xFF444444.toInt()) }
        root.addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
            .apply { topMargin = dp(16); bottomMargin = dp(4) })
    }

    private fun copyToClip(text: String, name: String) {
        clip.setPrimaryClip(ClipData.newPlainText(name, text))
        Toast.makeText(this, "copied — paste in chat", Toast.LENGTH_SHORT).show()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private val QUICK_COMMANDS = listOf(
            "Creative mode"      to "/gamemode creative",
            "Survival mode"      to "/gamemode survival",
            "Spectator"          to "/gamemode spectator",
            "Day"                to "/time set day",
            "Night"              to "/time set night",
            "Clear weather"      to "/weather clear",
            "Keep inventory ON"  to "/gamerule keepInventory true",
            "Night vision 1h"    to "/effect @p night_vision 3600 1 true",
            "Haste II 1h"        to "/effect @p haste 3600 1 true",
            "Speed II 1h"        to "/effect @p speed 3600 1 true",
            "Fly (levitation)"   to "/effect @p levitation 3600 1 true",
            "Heal full"          to "/effect @p instant_health 1 10 true",
            "Teleport to spawn"  to "/tp @p 0 100 0",
            "Diamond pickaxe +5" to "/give @p diamond_pickaxe{Enchantments:[{id:efficiency,lvl:5},{id:unbreaking,lvl:3},{id:fortune,lvl:3}]} 1",
            "Elytra"             to "/give @p elytra 1",
            "Netherite kit"      to "/give @p netherite_sword 1\n/give @p netherite_helmet 1\n/give @p netherite_chestplate 1\n/give @p netherite_leggings 1\n/give @p netherite_boots 1",
            "Shulker box pack"   to "/give @p shulker_box 27",
            "Stack of totems"    to "/give @p totem_of_undying 64",
            "64 ender pearls"    to "/give @p ender_pearl 64",
            "Spawn villager"     to "/summon villager ~ ~ ~",
            "Kill all mobs"      to "/kill @e[type=!player]"
        )

        private val PACKS = listOf(
            Triple("Fullbright / Night Vision",
                   "fullbright.mcpack",
                   "Replaces lightmap so caves and night are bright. Universal, no bans."),
            Triple("X-Ray (ores visible)",
                   "xray.mcpack",
                   "Stone and dirt rendered transparent. Great for mining. Some PvP servers detect it via pack hash."),
            Triple("Entity Outline / ESP",
                   "entity_glow.mcpack",
                   "Players and mobs render with a bright silhouette through walls. Requires experimental scripting ON when world is created.")
        )
    }
}
