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
            ActionResult("Do Not Disturb", ActionState.APPLIED, "Off → on. Pop-ups silenced; game sound and alarms still play.", verified = true),
            ActionResult("Brightness", ActionState.APPLIED, "43 % (auto) → 100 % locked.", verified = true),
            ActionResult("Refresh rate", ActionState.APPLIED, "60 Hz → Locked to 120 Hz. Display confirms 120 Hz.", verified = true),
            ActionResult("Rotation lock", ActionState.APPLIED, "Auto-rotate on → locked to landscape.", verified = true),
            ActionResult("Auto-sync", ActionState.APPLIED, "On → paused (Gmail, Drive, contacts…). Resumes after the game.", verified = true),
            ActionResult("Background apps", ActionState.SKIPPED, "Android 14 and newer don't let apps close other apps. Android frees memory for the game by itself; close apps you don't need from Recents."),
            ActionResult("Keep screen awake", ActionState.APPLIED, "Screen stays on while the toolkit is showing."),
            ActionResult("Low-latency Wi-Fi", ActionState.SKIPPED, "Android only gives low-latency Wi-Fi to the app on screen, so during play only the game itself can request it."),
        )
    )

    val live = listOf(
        com.redcore.booster.booster.LiveEffect("Do Not Disturb", "On", true),
        com.redcore.booster.booster.LiveEffect("Brightness", "100 %", true),
        com.redcore.booster.booster.LiveEffect("Refresh rate", "120 Hz", true),
        com.redcore.booster.booster.LiveEffect("Rotation lock", "Locked", true),
        com.redcore.booster.booster.LiveEffect("Auto-sync", "Paused", true),
    )

    fun unlock(allButAdb: Boolean = true) = com.redcore.booster.ui.screens.unlockItems(
        com.redcore.booster.booster.Perm.entries.associateWith { allButAdb && it !in com.redcore.booster.booster.Permissions.adbOnly }, 120
    )
}
