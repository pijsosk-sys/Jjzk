package com.redcore.booster

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.redcore.booster.booster.Perm
import com.redcore.booster.data.AppSize
import com.redcore.booster.data.BigFile
import com.redcore.booster.data.CoreInfo
import com.redcore.booster.data.DeviceReport
import com.redcore.booster.data.GameMode
import com.redcore.booster.data.InfoSection
import com.redcore.booster.data.InstalledApp
import com.redcore.booster.data.Insights
import com.redcore.booster.data.NetInfo
import com.redcore.booster.data.PingResult
import com.redcore.booster.data.PlayStat
import com.redcore.booster.data.PlaySummary
import com.redcore.booster.data.SensorInfo
import com.redcore.booster.data.StorageSummary
import com.redcore.booster.overlay.OverlayActions
import com.redcore.booster.overlay.OverlayState
import com.redcore.booster.overlay.OverlayUi
import com.redcore.booster.ui.AppScaffold
import com.redcore.booster.ui.Dest
import com.redcore.booster.ui.ResultSheet
import com.redcore.booster.ui.ResultsPanel
import com.redcore.booster.ui.Route
import com.redcore.booster.ui.SpeedPhase
import com.redcore.booster.ui.SpeedState
import com.redcore.booster.ui.components.LocalIconLoader
import com.redcore.booster.ui.screens.*
import com.redcore.booster.ui.theme.RedcoreTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val GB = 1024L * 1024 * 1024
private const val MB = 1024L * 1024

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w915dp-h412dp-land-xhdpi", application = android.app.Application::class)
class ScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private val s = SampleData.stats()
    private val h = SampleData.history
    private val st = SampleData.settings
    private val perms = Perm.entries.associateWith { it != Perm.SECURE_SETTINGS && it != Perm.ACCESSIBILITY }

    private fun capture(name: String, ms: Long = 1500, content: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            RedcoreTheme { CompositionLocalProvider(LocalIconLoader provides { null }) { content() } }
        }
        rule.mainClock.advanceTimeBy(ms)
        rule.onRoot().captureRoboImage("../screenshots/$name.png")
    }

    private fun shot(name: String, dest: Dest, title: String = dest.title, depth: Int = 1, sheet: ResultSheet? = null, content: @Composable () -> Unit) =
        capture(name) {
            AppScaffold(Route(dest), depth, title, SampleData.session.takeIf { dest != Dest.HOME }, {}, {}, {}, overlay = { ResultsPanel(sheet) {} }) { content() }
        }

    @Test fun a01_home() = shot("01_home", Dest.HOME, "Control Center") {
        HomeScreen(s, st, null, SampleData.library.take(4), "Nubia Gamer X9", "Snapdragon 8 Gen 3 · Android 15 · 12 GB RAM", false, {}, {}, {}, {}, {}, {})
    }

    @Test @Config(qualifiers = "w915dp-h760dp-land-xhdpi")
    fun a02_homeTall() = shot("02_home_scrolled_session", Dest.HOME, "Control Center") {
        HomeScreen(s, st, SampleData.session, SampleData.library.take(4), "Nubia Gamer X9", "Snapdragon 8 Gen 3 · Android 15 · 12 GB RAM", true, {}, {}, {}, {}, {}, {})
    }

    private val today = PlaySummary(2 * 3600_000L + 14 * 60_000L, listOf(PlayStat("com.studio.skyraiders", 84 * 60_000L, 0), PlayStat("com.neon.drift", 50 * 60_000L, 0)), emptyList())
    private val week = PlaySummary(11 * 3600_000L, today.perGame, listOf(40, 95, 20, 130, 60, 180, 134).map { it * 60_000L })

    @Test @Config(qualifiers = "w915dp-h760dp-land-xhdpi")
    fun a03_games() = shot("03_game_space", Dest.GAMES) {
        GameSpaceScreen(SampleData.library, today, week, true, {}, {}, {}, {}, {})
    }

    @Test fun a04_gamesEmpty() = shot("04_game_space_empty", Dest.GAMES) {
        GameSpaceScreen(emptyList(), null, null, false, {}, {}, {}, {}, {})
    }

    @Test fun a05_detail() = shot("05_game_detail", Dest.GAME_DETAIL, "Sky Raiders", 2) {
        GameDetailScreen(SampleData.library[0].copy(profile = SampleData.library[0].profile.copy(mode = GameMode.BEAST, brightness = 85)), 84 * 60_000L, 6 * 3600_000L, System.currentTimeMillis(),
            Capabilities(true, true, false, true, true, listOf(60, 90, 120)), {}, {}, {})
    }

    @Test fun a06_addGames() = shot("06_add_games", Dest.ADD_GAMES, "Add Games", 2) {
        AddGamesScreen(listOf(
            InstalledApp("com.mythic.quest", "Mythic Quest RPG", true, false),
            InstalledApp("com.road.rage", "Road Rage Racing", true, false),
            InstalledApp("com.chat.app", "Messenger", false, false),
            InstalledApp("com.music.player", "Music Player", false, false),
            InstalledApp("com.video.stream", "StreamBox", false, false),
        ), emptySet()) {}
    }

    @Test @Config(qualifiers = "w915dp-h760dp-land-xhdpi")
    fun a07_boost() = shot("07_boost", Dest.BOOST) {
        BoostScreen(s, GameMode.BEAST, SampleData.session, perms,
            listOf(BgApp("com.chat.app", "Messenger", "3 min ago"), BgApp("com.video.stream", "StreamBox", "25 min ago"), BgApp("com.music.player", "Music Player", "1 hour ago")),
            Insights.performance(s.copy(thermalStatus = 2), batterySaver = false, usageGranted = true), {}, {}, {}, {}, {})
    }

    @Test fun a08_boostSheet() = shot("08_boost_results", Dest.BOOST, sheet = ResultSheet("Beast mode active", SampleData.session.results)) {
        BoostScreen(s, GameMode.BEAST, null, perms, null, emptyList(), {}, {}, {}, {}, {})
    }

    @Test @Config(qualifiers = "w915dp-h760dp-land-xhdpi")
    fun a09_monitor() = shot("09_monitor", Dest.MONITOR) { MonitorScreen(s, h, st, "Adreno (TM) 750") {} }

    private val report = DeviceReport(
        "Nubia Gamer X9", "SM8650 · Android 15 · 12 GB RAM",
        listOf(
            InfoSection("Device", listOf("Manufacturer" to "Nubia", "Brand" to "Nubia", "Model" to "Gamer X9", "Codename" to "NX769J", "Android version" to "Android 15", "SDK level" to "35", "Security patch" to "2026-10-01", "Kernel" to "6.1.75-android14-11", "Bootloader" to null)),
            InfoSection("Processor", listOf("Chipset" to "Qualcomm SM8650", "Architecture" to "aarch64", "Cores" to "8", "Clusters" to "1× 3.30 GHz  +  4× 2.80 GHz  +  3× 2.02 GHz", "Governor" to "walt", "CPU part" to null)),
            InfoSection("Graphics", listOf("GPU renderer" to "Adreno (TM) 750", "Vulkan" to "1.3.284")),
            InfoSection("Battery", listOf("Level" to "78 %", "Health" to "Good", "Technology" to "Li-poly", "Charge cycles" to "214", "Design capacity" to "6000 mAh")),
        ),
        listOf(CoreInfo(0, 364, 2016, "walt"), CoreInfo(1, 364, 2016, "walt"), CoreInfo(2, 364, 2016, "walt"), CoreInfo(3, 499, 2803, "walt"), CoreInfo(4, 499, 2803, "walt"), CoreInfo(5, 499, 2803, "walt"), CoreInfo(6, 499, 2803, "walt"), CoreInfo(7, 480, 3302, "walt")),
        listOf(SensorInfo("LSM6DSV Accelerometer", "STMicro", "Accelerometer", "156.9", 0.17f, false)),
        listOf("Accelerometer" to true, "Gyroscope" to true, "Compass" to true, "Proximity" to true, "Light" to true, "Barometer" to false),
    )

    @Test fun a10_device() = shot("10_device", Dest.DEVICE) { DeviceScreen(report) }

    @Test fun a11_cooling() = shot("11_cooling", Dest.COOLING) { CoolingScreen(s, h, st, com.redcore.booster.ui.Routes.fanNote()) }

    @Test fun a12_battery() = shot("12_battery", Dest.BATTERY) {
        BatteryScreen(s, h, st, report.sections[3], Insights.battery(s, "Good", false), false, {}, {})
    }

    @Test @Config(qualifiers = "w915dp-h760dp-land-xhdpi")
    fun a13_network() = shot("13_network", Dest.NETWORK) {
        NetworkScreen(
            NetInfo(true, "Wi-Fi", true, false, false, 300000, 80000, "Home-5G", -54, 4, 866, 5180, "Wi-Fi 6 (802.11ax)", "192.168.1.24", true, listOf("192.168.1.1"), false),
            PingResult("1.1.1.1", listOf(14L, 16L, 13L, 22L, 15L, 14L, null, 17L, 15L, 13L, 16L, 14L)), false,
            SpeedState(SpeedPhase.DOWNLOAD, 286e6, null, null, 14.0), s, h, {}, {}, {}, {}
        )
    }

    @Test fun a14_storage() = shot("14_storage", Dest.STORAGE) {
        StorageScreen(
            StorageSummary(256 * GB, 71 * GB, 98 * GB, 41 * GB, 22 * GB, 3 * GB, 6 * GB),
            listOf(AppSize("com.studio.skyraiders", "Sky Raiders", 2 * GB, 14 * GB, 900 * MB), AppSize("com.arena.legends", "Arena Legends 5v5", GB, 9 * GB, 400 * MB)),
            listOf(BigFile(Uri.parse("content://media/1"), "REDCORE_20261116_221501.mp4", 2 * GB, "video/mp4", "Movies/REDCORE/", 0)),
            true, true, {}, {}, {}, {}, {}
        )
    }

    @Test fun a15_touch() = shot("15_touch", Dest.TOUCH) { TouchScreen(listOf("Multi-touch" to "5+ independent points", "Display density" to "480 dpi", "Touch sampling rate setting" to null)) }

    @Test @Config(qualifiers = "w915dp-h760dp-land-xhdpi")
    fun a16_settings() = shot("16_settings", Dest.SETTINGS) {
        SettingsScreen(st, perms, true, "1.0.0", object : SettingsActions {
            override fun mode(m: GameMode) {}; override fun autoDetect(v: Boolean) {}; override fun restore(v: Boolean) {}
            override fun hud(v: Boolean) {}; override fun scale(v: Float) {}; override fun fahrenheit(v: Boolean) {}
            override fun toolkit() {}; override fun grant(p: Perm) {}; override fun copyAdb() {}
        })
    }

    @Test fun a17_welcome() = capture("17_welcome") { WelcomeScreen {} }

    @Composable
    private fun FakeGame(content: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF7FB7E8), Color(0xFFCFE3F2), Color(0xFF8C7F6A))))) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Color(0xFF5E7044), Offset(0f, size.height * 0.62f))
                for (i in 0..8) drawRect(Color(0xFFE8EEF2), Offset(size.width * (0.08f + i * 0.1f), size.height * 0.25f), androidx.compose.ui.geometry.Size(18f, size.height * 0.4f))
            }
            content()
        }
    }

    private val ov = OverlayState(s, st.copy(hudEnabled = true), SampleData.session, false, false, true, true, 80, 60, false)

    @Test fun a18_overlayCollapsed() = capture("18_overlay_hud") {
        FakeGame { Box(Modifier.padding(top = 60.dp)) { OverlayUi(ov, object : OverlayActions {}) } }
    }

    @Test fun a19_overlayExpanded() = capture("19_overlay_panel") {
        FakeGame { OverlayUi(ov.copy(expanded = true, recording = true), object : OverlayActions {}) }
    }
}
