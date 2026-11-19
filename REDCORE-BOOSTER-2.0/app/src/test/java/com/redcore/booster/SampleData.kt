package com.redcore.booster

import com.redcore.booster.booster.ActionResult
import com.redcore.booster.booster.ActionState
import com.redcore.booster.booster.GameSession
import com.redcore.booster.data.AppSettings
import com.redcore.booster.data.GameMode
import com.redcore.booster.data.GameProfile
import com.redcore.booster.data.LibraryGame
import com.redcore.booster.data.LiveStats
import com.redcore.booster.data.ModeSpec
import com.redcore.booster.data.SysFs

/** Realistic sample values used only for screenshot tests. */
object SampleData {
    fun stats(i: Int = 0) = LiveStats(
        time = 0,
        coreCurMHz = listOf(1785, 1785, 1785, 2419, 2419, 2419, 2419, 3302).map { (it * (0.85 + 0.15 * kotlin.math.sin(i / 4.0))).toInt() },
        coreMaxMHz = listOf(2016, 2016, 2016, 2803, 2803, 2803, 2803, 3302),
        cpuUsage = null,
        ramTotal = 12L * 1024 * 1024 * 1024, ramAvail = ((4.6 + 0.4 * kotlin.math.sin(i / 7.0)) * 1024 * 1024 * 1024).toLong(),
        batteryPct = 78, batteryTempC = 37.2f + 0.9f * kotlin.math.sin(i / 9.0).toFloat() + 0.25f * kotlin.math.sin(i / 2.3).toFloat(), charging = false, plug = "Battery",
        voltageMv = 4012, currentMa = -(612 + (140 * kotlin.math.sin(i / 3.0)).toInt()),
        thermalStatus = 1, thermalHeadroom = 0.62f,
        rxBps = (1_350_000 + 700_000 * kotlin.math.sin(i / 5.0) + 300_000 * kotlin.math.sin(i / 1.7)).toLong().coerceAtLeast(50_000), txBps = 92_000,
        gpuMHz = null, gpuLoad = null, refreshHz = 120,
        zones = listOf(SysFs.ThermalZone("cpu-0-0", 46.2f), SysFs.ThermalZone("gpu", 41.8f), SysFs.ThermalZone("skin", 35.1f)),
    )

    val history = (0 until 90).map { stats(it) }

    val settings = AppSettings(globalMode = GameMode.BEAST, libraryScanned = true, onboarded = true)

    val library = listOf(
        "com.studio.skyraiders" to "Sky Raiders",
        "com.neon.drift" to "Neon Drift",
        "com.arena.legends" to "Arena Legends 5v5",
        "com.pixel.dungeon" to "Pixel Dungeon",
        "com.tactical.ops" to "Tactical Ops: Zero",
        "com.speed.kart" to "Turbo Kart",
    ).mapIndexed { i, (p, l) -> LibraryGame(GameProfile(p, GameMode.entries[i % 3]), l, true) }

    val session = GameSession(
        "com.studio.skyraiders", "Sky Raiders", GameMode.BEAST, ModeSpec.of(GameMode.BEAST), 0,
        listOf(
            ActionResult("Do Not Disturb", ActionState.APPLIED, "Priority-only mode: notifications are silenced."),
            ActionResult("Brightness", ActionState.APPLIED, "Set to 100 % (auto-brightness paused)."),
            ActionResult("Refresh rate", ActionState.BLOCKED, "Android blocks apps from changing refresh rate on this phone unless granted via ADB."),
            ActionResult("Background apps", ActionState.APPLIED, "Asked Android to close 6 cached apps."),
            ActionResult("Keep screen awake", ActionState.APPLIED, "Screen stays on while the toolkit is active."),
        )
    )
}
