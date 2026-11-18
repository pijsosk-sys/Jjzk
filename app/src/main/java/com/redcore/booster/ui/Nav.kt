package com.redcore.booster.ui

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.redcore.booster.R

enum class Dest(val title: String, @DrawableRes val iconRes: Int) {
    HOME("Home", R.drawable.ic_rc_home),
    GAMES("Game Space", R.drawable.ic_rc_gamepad),
    BOOST("Performance", R.drawable.ic_rc_gauge),
    MONITOR("Monitor", R.drawable.ic_rc_monitor),
    DEVICE("Device", R.drawable.ic_rc_device),
    COOLING("Cooling", R.drawable.ic_rc_fan),
    NETWORK("Network", R.drawable.ic_rc_network),
    BATTERY("Battery", R.drawable.ic_rc_battery),
    STORAGE("Storage", R.drawable.ic_rc_storage),
    TOUCH("Touch", R.drawable.ic_rc_touch),
    SETTINGS("Settings", R.drawable.ic_rc_settings),
    GAME_DETAIL("Game", R.drawable.ic_rc_gamepad),
    ADD_GAMES("Add Games", R.drawable.ic_rc_gamepad),
    PROCESSOR("Processor", R.drawable.ic_rc_cpu);

    companion object {
        val bottom = listOf(HOME, GAMES, BOOST, MONITOR, DEVICE)
        val rail = listOf(HOME, GAMES, BOOST, MONITOR, COOLING, BATTERY, NETWORK, TOUCH, STORAGE, DEVICE, SETTINGS)
    }
}

@Composable
fun Dest.vector(): ImageVector = ImageVector.vectorResource(iconRes)

@Composable
fun rcIcon(@DrawableRes id: Int): ImageVector = ImageVector.vectorResource(id)

data class Route(val dest: Dest, val arg: String? = null)
