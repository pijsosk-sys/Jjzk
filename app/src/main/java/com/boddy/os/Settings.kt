package com.boddy.os

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** User choices that persist across launches. */
class Settings(context: Context) {

    private val prefs = context.getSharedPreferences("boddy", Context.MODE_PRIVATE)

    /** How big things look on the desktop. Fewer Linux pixels = bigger buttons and text. */
    enum class Size(val label: String, val desktopWidth: Int) {
        BIG("Big", 1100),
        MEDIUM("Medium (best for phones)", 1300),
        SMALL("Small", 1600),
    }

    /** Whether AI agents ask before running commands and editing files. */
    enum class AgentMode(val label: String, val description: String) {
        ASK("Ask me every time", "Safer. The AI asks before it runs commands, clicks or changes files."),
        AUTO("Just do it", "Faster. The AI works on its own while you watch."),
    }

    var size: Size
        get() = Size.entries.firstOrNull { it.name == prefs.getString("size", null) } ?: Size.MEDIUM
        set(value) = prefs.edit().putString("size", value.name).apply()

    /** Null until the user has picked one. */
    var agentMode: AgentMode?
        get() = AgentMode.entries.firstOrNull { it.name == prefs.getString("agentMode", null) }
        set(value) = prefs.edit().putString("agentMode", value?.name).apply()
}

/** Writes the chosen [Settings.AgentMode] into Claude Code's and Codex's own settings files. */
object AgentConfig {

    fun apply(rootfs: File, mode: Settings.AgentMode) {
        val auto = mode == Settings.AgentMode.AUTO
        for (dir in listOf(".claude", ".claude-deepseek")) {
            writeClaudeSettings(File(rootfs, "root/$dir/settings.json"), auto)
        }
        writeCodexConfig(File(rootfs, "root/.codex/config.toml"), auto)
    }

    /** Only touches the "permissions" block, keeping anything else the user set. */
    private fun writeClaudeSettings(file: File, auto: Boolean) {
        val json = try {
            JSONObject(file.readText())
        } catch (_: Exception) {
            JSONObject()
        }
        val permissions = JSONObject()
        if (auto) {
            permissions.put("defaultMode", "acceptEdits")
            permissions.put("allow", JSONArray(listOf("Bash", "Read", "Edit", "Write", "Glob", "Grep", "WebFetch", "WebSearch")))
        } else {
            permissions.put("defaultMode", "default")
            permissions.put("allow", JSONArray())
        }
        json.put("permissions", permissions)
        file.parentFile?.mkdirs()
        file.writeText(json.toString(2))
    }

    /**
     * Codex's own sandbox needs kernel features proot doesn't have, and Boddy is already
     * sandboxed by Android, so Codex always gets full access inside Boddy.
     */
    private fun writeCodexConfig(file: File, auto: Boolean) {
        val managed = mapOf(
            "approval_policy" to if (auto) "\"never\"" else "\"on-request\"",
            "sandbox_mode" to "\"danger-full-access\"",
        )
        val lines = (if (file.exists()) file.readLines() else emptyList()).toMutableList()
        // Top-level keys must come before any [table], so put ours first.
        lines.removeAll { line -> managed.keys.any { line.trimStart().startsWith("$it ") || line.trimStart().startsWith("$it=") } }
        val result = managed.map { (k, v) -> "$k = $v" } + lines
        file.parentFile?.mkdirs()
        file.writeText(result.joinToString("\n") + "\n")
    }
}
