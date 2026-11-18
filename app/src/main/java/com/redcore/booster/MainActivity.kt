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

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        val games = app.games
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
                    BackHandler(enabled = stack.size > 1 || sheet != null) {
                        if (sheet != null) vm.dismissSheet() else nav.back()
                    }
                    val route = stack.last()
                    val loaded by vm.loaded.collectAsStateWithLifecycle()
                    val settings by vm.settings.collectAsStateWithLifecycle()
                    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refresh() }
                    if (!loaded) { Box(Modifier.fillMaxSize().background(RC.Black)); return@CompositionLocalProvider }
                    if (!settings.onboarded) {
                        WelcomeScreen {
                            vm.finishOnboarding()
                            if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
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
                        overlay = { ResultsPanel(sheet) { vm.dismissSheet() } },
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
