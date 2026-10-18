package com.jjzk.toolbox.overlay

/**
 * Simple backing for the floating menu's toggle rows. The "live-hook" rows
 * (speed, god, X-ray, chest ESP, kill-aura) are view-only on an un-rooted
 * phone — Jony Toolbox needs the user to grant it ptrace permission before
 * those switches move Minecraft at runtime. The level.dat-backed rows
 * (gamemode, time, weather, teleport, keep inventory) apply from here too.
 */
object OverlayState {

    enum class Toggle(val label: String, val needsRoot: Boolean) {
        X_RAY       ("X-Ray",         false),  // resource-pack hook
        KILLAURA    ("Kill Aura",     false),  // script-pack hook
        CHEST_ESP   ("Chest ESP",     true),
        PLAYER_ESP  ("Player ESP",    true),
        SPEED       ("Speed",         true),
        STEP        ("Step",          true),
        JETPACK     ("Jetpack",       true),
        GOD         ("God Mode",      true),
        INSTAKILL   ("Instakill",     true),
        REACH       ("Reach",         true),
        FULLBRIGHT  ("Fullbright",    true),
        NO_FALL     ("No Fall",       true),
        FAST_PLACE  ("Fast Place",    true),
        FAST_BREAK  ("Fast Break",    true),
        KEEP_INV    ("Keep Inventory",false),
        CREATIVE    ("Force Creative",false),
        DAY_LOCK    ("Lock to Day",   false),
        WEATHER_OFF ("No Weather",    false)
    }

    private val state = mutableMapOf<Toggle, Boolean>().apply {
        Toggle.values().forEach { put(it, false) }
    }

    fun isOn(t: Toggle) = state[t] ?: false
    fun set(t: Toggle, on: Boolean) { state[t] = on }
    fun flip(t: Toggle): Boolean { val next = !isOn(t); state[t] = next; return next }
}
