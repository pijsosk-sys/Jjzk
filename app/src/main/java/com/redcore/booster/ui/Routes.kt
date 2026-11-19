package com.redcore.booster.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.redcore.booster.BuildConfig
import com.redcore.booster.app
import com.redcore.booster.booster.Perm
import com.redcore.booster.booster.Permissions
import com.redcore.booster.data.GameMode
import com.redcore.booster.data.Insights
import com.redcore.booster.ui.screens.AddGamesScreen
import com.redcore.booster.ui.screens.BatteryScreen
import com.redcore.booster.ui.screens.BgApp
import com.redcore.booster.ui.screens.BoostScreen
import com.redcore.booster.ui.screens.Capabilities
import com.redcore.booster.ui.screens.CoolingScreen
import com.redcore.booster.ui.screens.DeviceScreen
import com.redcore.booster.ui.screens.GameDetailScreen
import com.redcore.booster.ui.screens.GameSpaceScreen
import com.redcore.booster.ui.screens.HomeScreen
import com.redcore.booster.ui.screens.MonitorScreen
import com.redcore.booster.ui.screens.NetworkScreen
import com.redcore.booster.ui.screens.SettingsActions
import com.redcore.booster.ui.screens.SettingsScreen
import com.redcore.booster.ui.screens.StorageScreen
import com.redcore.booster.ui.screens.TouchScreen
import kotlin.math.roundToInt

object Routes {
    interface Navigator {
        fun go(dest: Dest, arg: String? = null)
        fun back()
    }

    fun fanNote(): Pair<String, String> {
        val maker = Build.MANUFACTURER.lowercase()
        return if (maker.contains("nubia") || maker.contains("zte") || Build.BRAND.lowercase().contains("redmagic")) {
            "Built-in fan: controlled by the phone's own software" to
                "This phone family ships with an active cooling fan, but its controls are private to the manufacturer's game software. Android offers no public API for other apps to switch it, so REDCORE doesn't show a fake toggle."
        } else {
            "No controllable fan detected" to
                "Android has no standard fan interface and this phone doesn't expose one. Clip-on coolers work independently of any app."
        }
    }

    fun touchFeatures(c: Context): List<Pair<String, String?>> {
        val pm = c.packageManager
        val dm = c.resources.displayMetrics
        return listOf(
            "Multi-touch" to when {
                pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_JAZZHAND) -> "5+ independent points"
                pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_DISTINCT) -> "2+ independent points"
                pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH) -> "Basic multi-touch"
                pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN) -> "Single touch"
                else -> null
            },
            "Display density" to "${dm.densityDpi} dpi",
            "Pointer speed (system)" to runCatching { Settings.System.getInt(c.contentResolver, "pointer_speed").toString() }.getOrNull(),
            "Long-press delay" to runCatching { "${Settings.Secure.getInt(c.contentResolver, "long_press_timeout")} ms" }.getOrNull(),
            "Touch sampling rate setting" to null,
        )
    }

    /** Opens the right system screen (special permissions) or runtime prompt for a permission. */
    @Composable
    fun rememberGranter(vm: MainViewModel): (Perm) -> Unit {
        val ctx = LocalContext.current
        val runtimeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.refresh() }
        val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { vm.refresh() }
        return remember {
            { p ->
                when (p) {
                    Perm.CALL_SCREENING -> if (Build.VERSION.SDK_INT >= 29) {
                        val rm = ctx.getSystemService(android.app.role.RoleManager::class.java)
                        if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_CALL_SCREENING))
                            roleLauncher.launch(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_CALL_SCREENING))
                        else vm.toast("This phone doesn't support call screening apps.")
                    }
                    Perm.MEDIA -> runtimeLauncher.launch(Permissions.mediaPermissions())
                    Perm.MICROPHONE -> runtimeLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                    Perm.NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= 33) runtimeLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                    else -> Permissions.settingsIntent(ctx, p)?.let { runCatching { ctx.startActivity(it) }.onFailure { vm.toast("Open Settings › Apps › REDCORE to grant this.") } }
                }
            }
        }
    }

    @Composable
    fun Content(r: Route, vm: MainViewModel, nav: Navigator) {
        val ctx = LocalContext.current
        val a = ctx.app
        val stats by vm.stats.collectAsStateWithLifecycle()
        val history by vm.history.collectAsStateWithLifecycle()
        val settings by vm.settings.collectAsStateWithLifecycle()
        val session by vm.session.collectAsStateWithLifecycle()
        val library by vm.library.collectAsStateWithLifecycle()
        val recent by vm.recent.collectAsStateWithLifecycle()
        val device by vm.device.collectAsStateWithLifecycle()
        val toolkit by vm.toolkitRunning.collectAsStateWithLifecycle()
        val perms by vm.perms.collectAsStateWithLifecycle()
        val fxState by vm.fx.collectAsStateWithLifecycle()

        val grant = rememberGranter(vm)
        fun open(i: Intent) = runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure { vm.toast("Not available on this phone") }

        when (r.dest) {
            Dest.HOME -> {
                val lib = library.orEmpty()
                val recentGames = recent.mapNotNull { p -> lib.firstOrNull { it.profile.pkg == p.pkg } }
                HomeScreen(
                    stats, settings, session, recentGames,
                    device?.headline ?: Build.MODEL, device?.subline ?: "",
                    toolkit, vm::setMode, { nav.go(it) }, vm::launchGame, vm::boostNow, vm::endSession, vm::toggleToolkit,
                    fx = fxState, onCool = { vm.setCool(it) }
                )
            }
            Dest.GAMES -> {
                val today by vm.today.collectAsStateWithLifecycle()
                val week by vm.week.collectAsStateWithLifecycle()
                GameSpaceScreen(library, today, week, perms[Perm.USAGE] == true, vm::launchGame, { nav.go(Dest.GAME_DETAIL, it) }, { nav.go(Dest.ADD_GAMES) }, vm::scan) { grant(Perm.USAGE) }
            }
            Dest.GAME_DETAIL -> {
                val g = library?.firstOrNull { it.profile.pkg == r.arg }
                val today by vm.today.collectAsStateWithLifecycle()
                val week by vm.week.collectAsStateWithLifecycle()
                if (g == null) { LaunchedEffect(Unit) { nav.back() }; return }
                val caps = remember(perms) {
                    Capabilities(perms[Perm.DND] == true, perms[Perm.WRITE_SETTINGS] == true, perms[Perm.SECURE_SETTINGS] == true,
                        perms[Perm.OVERLAY] == true, perms[Perm.USAGE] == true, a.boost.supportedRefreshRates().map { it.roundToInt() })
                }
                GameDetailScreen(
                    g, today?.perGame?.firstOrNull { it.pkg == g.profile.pkg }?.foregroundMs,
                    week?.perGame?.firstOrNull { it.pkg == g.profile.pkg }?.foregroundMs,
                    recent.firstOrNull { it.pkg == g.profile.pkg }?.lastUsed, caps,
                    { vm.launchGame(g.profile.pkg) }, vm::updateProfile, { vm.removeGame(g.profile.pkg); nav.back() }
                )
            }
            Dest.ADD_GAMES -> {
                LaunchedEffect(Unit) { vm.loadInstalled() }
                val apps by vm.installed.collectAsStateWithLifecycle()
                AddGamesScreen(apps, library.orEmpty().map { it.profile.pkg }.toSet()) { vm.addGames(it); nav.back() }
            }
            Dest.BOOST -> {
                val bg by vm.background.collectAsStateWithLifecycle()
                val saver = remember(stats.time / 10000) { ctx.getSystemService(PowerManager::class.java).isPowerSaveMode }
                BoostScreen(
                    stats, history, device?.sections?.firstOrNull { it.title == "Graphics" }?.rows?.firstOrNull()?.second, settings,
                    settings.globalMode, session, perms,
                    bg?.map { BgApp(it.pkg, a.games.label(it.pkg), DateUtils.getRelativeTimeSpanString(it.lastUsed).toString()) },
                    Insights.performance(stats, saver, perms[Perm.USAGE] == true),
                    vm::setMode, vm::boostNow, vm::endSession, vm::cleanBackground, grant,
                    fx = fxState, onCool = { vm.setCool(it) }, onCoolTarget = { vm.setCoolTarget(it) },
                    onSmooth = { vm.setSmooth120(it) }, maxHz = remember { a.boost.supportedRefreshRates().maxOrNull()?.roundToInt() }
                )
            }
            Dest.TOOLS -> {
                LaunchedEffect(Unit) { vm.loadVendorTools() }
                val vendor by vm.vendorTools.collectAsStateWithLifecycle()
                com.redcore.booster.ui.screens.GameToolsScreen(settings, perms, vendor, object : com.redcore.booster.ui.screens.ToolsActions {
                    override fun <T> put(key: androidx.datastore.preferences.core.Preferences.Key<T>, value: T) { vm.put(key, value) }
                    override fun haptics(on: Boolean) { vm.setHaptics(on) }
                    override fun calls(m: com.redcore.booster.data.CallMode) { vm.setCalls(m) }
                    override fun notif(m: com.redcore.booster.data.NotifMode) { vm.setNotif(m) }
                    override fun grant(p: Perm) = grant(p)
                    override fun openApp(pkg: String) { a.games.launchIntent(pkg)?.let { open(it) } ?: vm.toast("Can't open that app") }
                    override fun copyAdb() {
                        ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("adb", Permissions.ADB_GRANT_ALL))
                        vm.toast("ADB command copied")
                    }
                })
            }
            Dest.MONITOR -> MonitorScreen(stats, history, settings, device?.sections?.firstOrNull { it.title == "Graphics" }?.rows?.firstOrNull()?.second) { nav.go(it) }
            Dest.DEVICE, Dest.PROCESSOR -> DeviceScreen(device)
            Dest.COOLING -> CoolingScreen(stats, history, settings, remember { fanNote() })
            Dest.BATTERY -> {
                val saver = remember(stats.time / 10000) { ctx.getSystemService(PowerManager::class.java).isPowerSaveMode }
                val info = device?.sections?.firstOrNull { it.title == "Battery" }
                val health = info?.rows?.firstOrNull { it.first == "Health" }?.second
                BatteryScreen(stats, history, settings, info, Insights.battery(stats, health, saver), saver,
                    { open(Intent(Intent.ACTION_POWER_USAGE_SUMMARY)) }, { open(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)) })
            }
            Dest.NETWORK -> {
                val nvm: NetworkViewModel = viewModel()
                val info by nvm.info.collectAsStateWithLifecycle()
                val ping by nvm.ping.collectAsStateWithLifecycle()
                val pinging by nvm.pinging.collectAsStateWithLifecycle()
                val speed by nvm.speed.collectAsStateWithLifecycle()
                val loc = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { nvm.refresh() }
                NetworkScreen(info, ping, pinging, speed, stats, history, nvm::runPing, nvm::runSpeedTest,
                    { loc.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
                    { open(Intent(Settings.ACTION_WIFI_SETTINGS)) })
            }
            Dest.STORAGE -> {
                val svm: StorageViewModel = viewModel()
                LaunchedEffect(perms[Perm.USAGE], perms[Perm.MEDIA]) { svm.load() }
                val sum by svm.summary.collectAsStateWithLifecycle()
                val apps by svm.apps.collectAsStateWithLifecycle()
                val files by svm.files.collectAsStateWithLifecycle()
                StorageScreen(sum, apps, files, perms[Perm.USAGE] == true, perms[Perm.MEDIA] == true,
                    { grant(Perm.USAGE) }, { grant(Perm.MEDIA) },
                    { open(a.storage.openIntent(it)) }, { open(a.storage.appDetails(it)) },
                    { runCatching { ctx.startActivity(a.storage.storageManagerIntent()) }.onFailure { open(a.storage.internalStorageSettings()) } })
            }
            Dest.TOUCH -> TouchScreen(remember { touchFeatures(ctx) }, settings, perms[Perm.OVERLAY] == true, perms[Perm.ACCESSIBILITY] == true, object : com.redcore.booster.ui.screens.TouchActions {
                override fun edgeGuard(on: Boolean) { vm.setEdgeGuard(on) }
                override fun edgeRange(r: Int) { vm.setEdgeRange(r) }
                override fun previewEdge() { vm.previewEdge() }
                override fun triggers(on: Boolean) { vm.setTriggers(on) }
                override fun points(l1x: Float, l1y: Float, r1x: Float, r1y: Float) { vm.setTriggerPoints(l1x, l1y, r1x, r1y) }
                override fun grantOverlay() = grant(Perm.OVERLAY)
                override fun grantAccessibility() = grant(Perm.ACCESSIBILITY)
            })
            Dest.SETTINGS -> SettingsScreen(settings, perms, toolkit, BuildConfig.VERSION_NAME, object : SettingsActions {
                override fun mode(m: GameMode) { vm.setMode(m) }
                override fun autoDetect(v: Boolean) { vm.setAutoDetect(v) }
                override fun restore(v: Boolean) { vm.setRestore(v) }
                override fun hud(v: Boolean) { vm.setHud(v) }
                override fun scale(v: Float) { vm.setOverlayScale(v) }
                override fun fahrenheit(v: Boolean) { vm.setFahrenheit(v) }
                override fun toolkit() { vm.toggleToolkit() }
                override fun grant(p: Perm) = grant(p)
                override fun copyAdb() {
                    ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("adb", Permissions.ADB_GRANT_ALL))
                    vm.toast("ADB command copied")
                }
            })
        }
    }
}
