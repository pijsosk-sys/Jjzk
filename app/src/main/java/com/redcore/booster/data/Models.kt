package com.redcore.booster.data

import org.json.JSONArray
import org.json.JSONObject

enum class GameMode(val title: String, val tagline: String) {
    ECO("Eco", "Battery-efficient gaming"),
    BALANCED("Balanced", "Performance and battery in balance"),
    BEAST("Beast", "Maximum performance Android allows");
}

enum class RefreshTarget(val label: String) { LOWEST("Lowest"), DEFAULT("System default"), HIGHEST("Highest") }

/** What a mode actually does. Each action is applied only if Android grants the permission for it. */
data class ModeSpec(
    val dnd: Boolean,
    val brightness: Int?,
    val refresh: RefreshTarget,
    val cleanBackground: Boolean,
    val keepScreenOn: Boolean,
) {
    companion object {
        fun of(mode: GameMode) = when (mode) {
            GameMode.ECO -> ModeSpec(dnd = false, brightness = 40, refresh = RefreshTarget.LOWEST, cleanBackground = false, keepScreenOn = false)
            GameMode.BALANCED -> ModeSpec(dnd = true, brightness = null, refresh = RefreshTarget.DEFAULT, cleanBackground = false, keepScreenOn = true)
            GameMode.BEAST -> ModeSpec(dnd = true, brightness = 100, refresh = RefreshTarget.HIGHEST, cleanBackground = true, keepScreenOn = true)
        }
    }
}

data class GameProfile(
    val pkg: String,
    val mode: GameMode = GameMode.BALANCED,
    val overlay: Boolean = true,
    val dnd: Boolean? = null,
    val brightness: Int? = null,
    val refresh: RefreshTarget? = null,
    val cleanBackground: Boolean? = null,
    val addedAt: Long = System.currentTimeMillis(),
) {
    /** Mode defaults with this game's overrides applied. */
    fun effective(): ModeSpec {
        val base = ModeSpec.of(mode)
        return base.copy(
            dnd = dnd ?: base.dnd,
            brightness = if (brightness == -1) null else brightness ?: base.brightness,
            refresh = refresh ?: base.refresh,
            cleanBackground = cleanBackground ?: base.cleanBackground,
        )
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("pkg", pkg); put("mode", mode.name); put("overlay", overlay); put("addedAt", addedAt)
        dnd?.let { put("dnd", it) }
        brightness?.let { put("brightness", it) }
        refresh?.let { put("refresh", it.name) }
        cleanBackground?.let { put("clean", it) }
    }

    companion object {
        fun fromJson(o: JSONObject) = GameProfile(
            pkg = o.getString("pkg"),
            mode = runCatching { GameMode.valueOf(o.optString("mode")) }.getOrDefault(GameMode.BALANCED),
            overlay = o.optBoolean("overlay", true),
            dnd = if (o.has("dnd")) o.getBoolean("dnd") else null,
            brightness = if (o.has("brightness")) o.getInt("brightness") else null,
            refresh = if (o.has("refresh")) runCatching { RefreshTarget.valueOf(o.getString("refresh")) }.getOrNull() else null,
            cleanBackground = if (o.has("clean")) o.getBoolean("clean") else null,
            addedAt = o.optLong("addedAt", 0),
        )

        fun listToJson(list: List<GameProfile>) = JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()
        fun listFromJson(s: String?): List<GameProfile> = if (s.isNullOrBlank()) emptyList() else try {
            val a = JSONArray(s); (0 until a.length()).map { fromJson(a.getJSONObject(it)) }
        } catch (_: Throwable) { emptyList() }
    }
}

data class AppSettings(
    val globalMode: GameMode = GameMode.BALANCED,
    val autoDetect: Boolean = false,
    val hudEnabled: Boolean = true,
    val restoreOnExit: Boolean = true,
    val fahrenheit: Boolean = false,
    val libraryScanned: Boolean = false,
    val bubbleX: Int = 0,
    val bubbleY: Int = 260,
    val overlayScale: Float = 1f,
    val onboarded: Boolean = false,
    /** Edge touch guard: blocks palm touches on the screen's short edges during game sessions. */
    val edgeGuard: Boolean = false,
    val edgeRange: Int = 1, // 0 small, 1 medium, 2 broad
    /** Volume-key triggers: Vol- = L1, Vol+ = R1, tapping configurable points during game sessions. */
    val triggers: Boolean = false,
    val l1x: Float = 0.18f, val l1y: Float = 0.55f,
    val r1x: Float = 0.82f, val r1y: Float = 0.55f,
) {
    val edgeWidthDp: Int get() = when (edgeRange) { 0 -> 8; 2 -> 26; else -> 16 }
}

fun AppSettings.temp(c: Float?): String = when {
    c == null -> "—"
    fahrenheit -> "%.0f°F".format(c * 9 / 5 + 32)
    else -> "%.1f°C".format(c)
}
