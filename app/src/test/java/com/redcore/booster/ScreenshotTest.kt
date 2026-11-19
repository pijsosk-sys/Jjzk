package com.redcore.booster

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
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
import com.redcore.booster.ui.screens.PermAsk
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

    private val fxWarm = com.redcore.booster.booster.EffectsState(
        coolLevel = com.redcore.booster.booster.CoolLevel.WARM, coolNote = "brightness ≤ 70% · 90 Hz", pingMs = 23, fps = 118, fpsSource = "game",
        sessionStart = System.currentTimeMillis() - 1_754_000, wifiLowLatency = true
    )
    private val limitSession = SampleData.session.copy(mode = GameMode.LIMIT, spec = com.redcore.booster.data.ModeSpec.of(GameMode.LIMIT), startedAt = System.currentTimeMillis() - 1_754_000)

    @Test fun a01_home() = shot("01_home", Dest.HOME, "Control Center") {
        HomeScreen(s, st.copy(globalMode = GameMode.LIMIT, coolGuard = true), null, SampleData.library.take(4), "Nubia Gamer X9", "Snapdragon 8 Gen 3 · Android 15 · 12 GB RAM", false, {}, {}, {}, {}, {}, {},
            fx = com.redcore.booster.booster.EffectsState(coolLevel = com.redcore.booster.booster.CoolLevel.NORMAL))
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
        BoostScreen(s, h, "Adreno (TM) 750", st.copy(coolGuard = true, smooth120 = true), GameMode.LIMIT, limitSession, perms,
            listOf(BgApp("com.chat.app", "Messenger", "3 min ago"), BgApp("com.video.stream", "StreamBox", "25 min ago"), BgApp("com.music.player", "Music Player", "1 hour ago")),
            Insights.performance(s.copy(thermalStatus = 2), batterySaver = false, usageGranted = true), {}, {}, {}, {}, {}, fx = fxWarm.copy(fpsAdvice = null), maxHz = 120)
    }

    @Test @Config(qualifiers = "w915dp-h1100dp-land-xhdpi")
    fun a07b_tools() = shot("07b_game_tools", Dest.TOOLS) {
        GameToolsScreen(st.copy(crosshair = true, haptics = true, eq = com.redcore.booster.data.EqPreset.FOOTSTEPS, callMode = com.redcore.booster.data.CallMode.SILENCE),
            perms, listOf(InstalledApp("com.vendor.gamespace", "Game Space", false, true)), object : ToolsActions {
                override fun <T> put(key: androidx.datastore.preferences.core.Preferences.Key<T>, value: T) {}
                override fun haptics(on: Boolean) {}; override fun calls(m: com.redcore.booster.data.CallMode) {}
                override fun notif(m: com.redcore.booster.data.NotifMode) {}; override fun grant(p: Perm) {}
                override fun openApp(pkg: String) {}; override fun copyAdb() {}
            })
    }

    @Test fun a08_boostSheet() = shot("08_boost_results", Dest.BOOST, sheet = ResultSheet("Beast mode active", SampleData.session.results)) {
        BoostScreen(s, h, "Adreno (TM) 750", st, GameMode.BEAST, null, perms, null, emptyList(), {}, {}, {}, {}, {})
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

    private val tf = listOf("Multi-touch" to "5+ independent points", "Display density" to "480 dpi", "Touch sampling rate setting" to null)
    @Test fun a15_touch() = shot("15_touch", Dest.TOUCH) { TouchScreen(tf, st, true, true, object : TouchActions {}) }
    @Test fun a15b_edge() = shot("15b_touch_edge_guard", Dest.TOUCH) { TouchScreen(tf, st.copy(edgeGuard = true, edgeRange = 2), true, true, object : TouchActions {}, initialTab = 1) }
    @Test fun a15c_triggers() = shot("15c_touch_triggers", Dest.TOUCH) { TouchScreen(tf, st.copy(triggers = true, l1x = 0.12f, l1y = 0.7f, r1x = 0.86f, r1y = 0.4f), true, true, object : TouchActions {}, initialTab = 2) }

    @Test @Config(qualifiers = "w915dp-h760dp-land-xhdpi")
    fun a16_settings() = shot("16_settings", Dest.SETTINGS) {
        SettingsScreen(st, perms, true, "1.0.0", object : SettingsActions {
            override fun mode(m: GameMode) {}; override fun autoDetect(v: Boolean) {}; override fun restore(v: Boolean) {}
            override fun hud(v: Boolean) {}; override fun scale(v: Float) {}; override fun fahrenheit(v: Boolean) {}
            override fun toolkit() {}; override fun grant(p: Perm) {}; override fun copyAdb() {}
        })
    }

    @Test fun a17_welcome() = capture("17_welcome") { WelcomeScreen {} }

    @Test fun a17b_permissions() = capture("17b_permission_setup") {
        PermissionSetupScreen(Perm.entries.associateWith { it == Perm.NOTIFICATIONS || it == Perm.DND }, {}, {})
    }

    @Test fun a17c_permissionDialog() = shot("17c_permission_prompt", Dest.GAMES) {
        GameSpaceScreen(SampleData.library, today, week, true, {}, {}, {}, {}, {})
        PermissionDialog(PermAsk(Perm.OVERLAY, "Allow REDCORE to display over other apps so the gaming menu and HUD can appear inside Sky Raiders.", "Play without menu"), {}, {})
    }

    @Composable
    private fun FakeGame(content: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF3B6FB6), Color(0xFF9CC7EE), Color(0xFFE8D9B5))))) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width; val h = size.height
                // distant towers
                for (i in 0..10) {
                    val x = w * (0.05f + i * 0.09f); val th = h * (0.25f + (i % 3) * 0.08f)
                    drawRect(Color(0xFFDDE6EE).copy(alpha = 0.8f), Offset(x, h * 0.62f - th), androidx.compose.ui.geometry.Size(w * 0.025f, th))
                }
                // pillar + platform
                drawRect(Color(0xFFF2F2F2), Offset(w * 0.58f, 0f), androidx.compose.ui.geometry.Size(w * 0.035f, h * 0.75f))
                val path = androidx.compose.ui.graphics.Path().apply { moveTo(w * 0.3f, h); lineTo(w * 0.44f, h * 0.68f); lineTo(w * 0.58f, h * 0.68f); lineTo(w * 0.72f, h); close() }
                drawPath(path, Color(0xFFC9B48E))
                drawRect(Color(0xFF4B5A88), Offset(w * 0.44f, h * 0.62f), androidx.compose.ui.geometry.Size(w * 0.14f, h * 0.06f))
                drawCircle(Color.White.copy(alpha = 0.5f), w * 0.12f, Offset(w * 0.52f, h * 0.2f))
            }
            content()
        }
    }

    private val ov = OverlayState(s, st.copy(hudEnabled = true, coolGuard = true, crosshair = true, haptics = true), limitSession, false, false, true, true, 80, 60, false,
        edgeGuard = true, triggers = true, fx = fxWarm, notifMode = com.redcore.booster.data.NotifMode.BARRAGE, rotationLocked = true)

    @Test fun a18_overlayCollapsed() = capture("18_overlay_hud") {
        FakeGame { Box(Modifier.padding(top = 60.dp)) { OverlayUi(ov, object : OverlayActions {}) } }
    }

    @Test fun a19_overlayExpanded() = capture("19_overlay_panel") {
        FakeGame { OverlayUi(ov.copy(expanded = true, recording = true), object : OverlayActions {}) }
    }

    @Test fun a19b_report() = shot("19b_session_report", Dest.HOME, "Control Center") {
        HomeScreen(s, st, null, SampleData.library.take(4), "Nubia Gamer X9", "Snapdragon 8 Gen 3 · Android 15 · 12 GB RAM", false, {}, {}, {}, {}, {}, {})
        com.redcore.booster.ui.ReportPanel(com.redcore.booster.booster.SessionReport("Sky Raiders", "Limit", 47, 14, 42.6f, 39.8f, 2, 24, 58,
            "Game is running at 60 FPS on a 120 Hz screen — set its Frame rate option to 90/120 if it has one")) {}
    }

    @Test fun a20_hangup() = capture("20_screen_hangup") { com.redcore.booster.overlay.HangupScreen("Sky Raiders", s) {} }

    @Test fun a21_tools_overlay() = capture("21_stopwatch_barrage_crosshair") {
        FakeGame {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.align(Alignment.TopCenter).padding(top = 8.dp).fillMaxWidth().height(40.dp)) { BarrageTickerPreview() }
                Box(Modifier.padding(start = 24.dp, top = 64.dp)) { com.redcore.booster.overlay.StopwatchWidget({ _, _ -> }, {}, {}) }
                Box(Modifier.align(Alignment.Center).size(68.dp)) { com.redcore.booster.overlay.CrosshairGlyph(com.redcore.booster.data.CrossStyle.CROSS, 1) }
                Box(Modifier.align(Alignment.TopStart).padding(start = 40.dp)) {
                    Box(Modifier.size(150.dp, 24.dp)) { androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                        drawRoundRect(androidx.compose.ui.graphics.Color(0x99FF1E3C), androidx.compose.ui.geometry.Offset(size.width * 0.29f, 8f), androidx.compose.ui.geometry.Size(size.width * 0.42f, 9f), androidx.compose.ui.geometry.CornerRadius(8f))
                    } }
                }
                Box(Modifier.align(Alignment.BottomStart).padding(16.dp)) { OverlayUi(ov, object : OverlayActions {}) }
            }
        }
    }

    @Composable
    private fun BarrageTickerPreview() {
        androidx.compose.foundation.layout.Row(
            Modifier.padding(start = 260.dp).background(Color(0xCC0A0B10), androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 5.dp)
        ) {
            androidx.compose.material3.Text("DISCORD", style = com.redcore.booster.ui.theme.RCType.label.copy(color = com.redcore.booster.ui.theme.RC.Red))
            androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
            androidx.compose.material3.Text("Alex: gg, one more match?", style = com.redcore.booster.ui.theme.RCType.body.copy(color = Color.White))
        }
    }
}
