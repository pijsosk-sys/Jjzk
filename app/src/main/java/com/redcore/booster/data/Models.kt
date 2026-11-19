package com.redcore.booster.data

import org.json.JSONArray
import org.json.JSONObject

enum class GameMode(val title: String, val tagline: String) {
    ECO("Eco", "Battery-efficient gaming"),
    BALANCED("Balance", "Performance and battery in balance"),
    BEAST("Beast", "High performance for intense games"),
    LIMIT("Limit", "Every optimization Android allows, all at once");
}

enum class CallMode(val title: String) { ALLOW("Allow"), SILENCE("Silence"), DECLINE("Decline") }

/** How notifications behave while a game session has Do Not Disturb on. */
enum class NotifMode(val title: String) { ALL("All alerts"), BARRAGE("Barrage"), BLOCK("Block") }

enum class EqPreset(val title: String) { OFF("Off"), FOOTSTEPS("Footsteps"), BASS("Bass"), VOICE("Voice"), LOUD("Loud") }

enum class CrossStyle(val title: String) { CROSS("Cross"), DOT("Dot"), CIRCLE("Circle"), LINES("Lines") }

enum class RefreshTarget(val label: String) { LOWEST("Lowest"), DEFAULT("System default"), HIGHEST("Highest") }

/** What a mode actually does. Each action is applied only if Android grants the permission for it. */
data class ModeSpec(
    val dnd: Boolean,
    val brightness: Int?,
    val refresh: RefreshTarget,
    val cleanBackground: Boolean,
    val keepScreenOn: Boolean,
    val rotationLock: Boolean = true,
    val pauseSync: Boolean = false,
    val lowLatencyWifi: Boolean = false,
    val fastAnimations: Boolean = false,
    /** Null = use the user's "Calls during games" preference. */
    val calls: CallMode? = null,
    /** Close apps used in the last 24 h instead of the last 3 h. */
    val deepClean: Boolean = false,
) {
    companion object {
        fun of(mode: GameMode) = when (mode) {
            GameMode.ECO -> ModeSpec(dnd = false, brightness = 40, refresh = RefreshTarget.LOWEST, cleanBackground = false, keepScreenOn = false)
            GameMode.BALANCED -> ModeSpec(dnd = true, brightness = null, refresh = RefreshTarget.DEFAULT, cleanBackground = false, keepScreenOn = true,
                pauseSync = true, lowLatencyWifi = true)
            GameMode.BEAST -> ModeSpec(dnd = true, brightness = 100, refresh = RefreshTarget.HIGHEST, cleanBackground = true, keepScreenOn = true,
                pauseSync = true, lowLatencyWifi = true)
            GameMode.LIMIT -> ModeSpec(dnd = true, brightness = 100, refresh = RefreshTarget.HIGHEST, cleanBackground = true, keepScreenOn = true,
                pauseSync = true, lowLatencyWifi = true, fastAnimations = true, calls = CallMode.DECLINE, deepClean = true)
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
    /** Trigger behaviour: false = tap, true = hold while the key is held. */
    val triggerHold: Boolean = false,
    val triggerVibrate: Boolean = true,
    // ---- Cool guard: eases off when the phone gets hot, works with every mode
    val coolGuard: Boolean = false,
    val coolTarget: Int = 41,
    // ---- In-game menu
    val swipeTop: Boolean = true,
    val swipeSide: Boolean = true,
    val showBubble: Boolean = false,
    val handleHint: Boolean = true,
    // ---- Game tools
    val notifMode: NotifMode = NotifMode.BARRAGE,
    val callMode: CallMode = CallMode.ALLOW,
    val haptics: Boolean = false,
    val hapticStrength: Float = 0.7f,
    val eq: EqPreset = EqPreset.OFF,
    val crosshair: Boolean = false,
    val crossStyle: CrossStyle = CrossStyle.CROSS,
    val crossColor: Int = 0,
    val crossSize: Float = 0.5f,
    val breakMinutes: Int = 60,
    val recQuality: Int = 1, // 0 = 720p30, 1 = 1080p60, 2 = native 60
    val showFps: Boolean = true,
    val showPing: Boolean = true,
    /** Smooth 120: hold the display at its highest refresh rate during games (needs the ADB grant). */
    val smooth120: Boolean = false,
) {
    val edgeWidthDp: Int get() = when (edgeRange) { 0 -> 8; 2 -> 26; else -> 16 }
}

fun AppSettings.temp(c: Float?): String = when {
    c == null -> "—"
    fahrenheit -> "%.0f°F".format(c * 9 / 5 + 32)
    else -> "%.1f°C".format(c)
}
