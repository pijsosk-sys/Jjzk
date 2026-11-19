package com.redcore.booster

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.redcore.booster.ui.screens.WelcomeScreen
import com.redcore.booster.ui.theme.RC
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.redcore.booster.service.GameDetectionService
import com.redcore.booster.ui.AppScaffold
import com.redcore.booster.ui.Dest
import com.redcore.booster.ui.MainViewModel
import com.redcore.booster.ui.ResultsPanel
import com.redcore.booster.ui.Route
import com.redcore.booster.ui.Routes
import com.redcore.booster.ui.components.LocalIconLoader
import com.redcore.booster.ui.theme.RedcoreTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private val pendingDest = kotlinx.coroutines.flow.MutableStateFlow<Dest?>(null)

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        readDest(intent)
    }

    private fun readDest(i: android.content.Intent?) {
        i?.getStringExtra(EXTRA_DEST)?.let { runCatching { Dest.valueOf(it) }.getOrNull() }?.let { pendingDest.value = it }
    }

    companion object { const val EXTRA_DEST = "dest" }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        val games = app.games
        readDest(intent)
        setContent {
            RedcoreTheme {
                CompositionLocalProvider(LocalIconLoader provides { pkg -> games.icon(pkg) }) {
                    val stack = remember { mutableStateListOf(Route(Dest.HOME)) }
                    val session by vm.session.collectAsStateWithLifecycle()
                    val sheet by vm.sheet.collectAsStateWithLifecycle()
                    LaunchedEffect(Unit) {
                        vm.toast.collect { Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show() }
                    }
                    val nav = remember {
                        object : Routes.Navigator {
                            override fun go(dest: Dest, arg: String?) {
                                if (dest in Dest.bottom || dest == Dest.SETTINGS && stack.size == 1) {
                                    stack.clear(); if (dest != Dest.HOME) stack.add(Route(Dest.HOME))
                                }
                                if (stack.lastOrNull()?.dest != dest || stack.last().arg != arg) stack.add(Route(dest, arg))
                            }
                            override fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) else finish() }
                        }
                    }
                    LaunchedEffect(Unit) {
                        pendingDest.collect { d -> if (d != null) { nav.go(d); pendingDest.value = null } }
                    }
                    LaunchedEffect(Unit) { vm.navEvents.collect { nav.go(it) } }
                    val boostRun by vm.run.collectAsStateWithLifecycle()
                    val askOpen by vm.ask.collectAsStateWithLifecycle()
                    val reportOpen by vm.report.collectAsStateWithLifecycle()
                    BackHandler(enabled = stack.size > 1 || sheet != null || askOpen != null || reportOpen != null || boostRun != null) {
                        when {
                            boostRun != null -> vm.runFinished()
                            askOpen != null -> vm.askDeclined()
                            reportOpen != null -> vm.dismissReport()
                            sheet != null -> vm.dismissSheet()
                            else -> nav.back()
                        }
                    }
                    val route = stack.last()
                    val loaded by vm.loaded.collectAsStateWithLifecycle()
                    val settings by vm.settings.collectAsStateWithLifecycle()
                    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refresh() }
                    if (!loaded) { Box(Modifier.fillMaxSize().background(RC.Black)); return@CompositionLocalProvider }
                    val granter = Routes.rememberGranter(vm)
                    val ask by vm.ask.collectAsStateWithLifecycle()
                    val perms by vm.perms.collectAsStateWithLifecycle()
                    var onboardStep by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
                    if (!settings.onboarded) {
                        if (onboardStep == 0) WelcomeScreen {
                            onboardStep = 1
                            if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else com.redcore.booster.ui.screens.PermissionSetupScreen(perms, granter) { vm.finishOnboarding() }
                        return@CompositionLocalProvider
                    }
                    AppScaffold(
                        route = route,
                        stackDepth = if (route.dest == Dest.HOME) 1 else stack.size,
                        title = route.arg?.let { games.label(it) } ?: if (route.dest == Dest.HOME) "Control Center" else route.dest.title,
                        session = session,
                        onSelect = { nav.go(it) },
                        onBack = { nav.back() },
                        onSession = { nav.go(Dest.BOOST) },
                        overlay = {
                            ResultsPanel(sheet) { vm.dismissSheet() }
                            val report by vm.report.collectAsStateWithLifecycle()
                            com.redcore.booster.ui.ReportPanel(report) { vm.dismissReport() }
                            boostRun?.let { r ->
                                com.redcore.booster.ui.screens.BoostRunScreen(r, onDone = { vm.runFinished() }, onBatterySaver = {
                                    runCatching { startActivity(android.content.Intent(android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS)) }
                                })
                            }
                            com.redcore.booster.ui.screens.PermissionDialog(ask, onAllow = { ask?.perm?.let(granter); vm.askAccepted() }, onDecline = { vm.askDeclined() })
                        },
                    ) { r -> Routes.Content(r, vm, nav) }
                }
            }
        }
        // Resume auto game detection if the user enabled it earlier.
        val auto = runBlocking { app.settings.settings.first().autoDetect }
        if (auto && app.usage.hasAccess()) GameDetectionService.start(this)
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }
}
