package com.redcore.booster.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.ui.graphics.vector.ImageVector

enum class Dest(val title: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.SpaceDashboard),
    GAMES("Game Space", Icons.Default.SportsEsports),
    BOOST("Boost", Icons.Default.Bolt),
    MONITOR("Monitor", Icons.Default.MonitorHeart),
    DEVICE("Device", Icons.Default.PhoneAndroid),
    COOLING("Cooling", Icons.Default.Thermostat),
    NETWORK("Network", Icons.Default.NetworkCheck),
    BATTERY("Battery", Icons.Default.Battery5Bar),
    STORAGE("Storage", Icons.Default.Storage),
    TOUCH("Touch", Icons.Default.TouchApp),
    SETTINGS("Settings", Icons.Default.Settings),
    GAME_DETAIL("Game", Icons.Default.SportsEsports),
    ADD_GAMES("Add Games", Icons.Default.SportsEsports),
    PROCESSOR("Processor", Icons.Default.Memory);

    companion object {
        val bottom = listOf(HOME, GAMES, BOOST, MONITOR, DEVICE)
        val rail = listOf(HOME, GAMES, BOOST, MONITOR, COOLING, NETWORK, BATTERY, STORAGE, TOUCH, DEVICE, SETTINGS)
    }
}

data class Route(val dest: Dest, val arg: String? = null)
