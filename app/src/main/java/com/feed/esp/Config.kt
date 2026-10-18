package com.feed.esp

import android.graphics.Color

// Live settings shared between the UI and the running overlay/capture service.
object Config {
    @Volatile var espEnabled = true
    @Volatile var tracers = false
    @Volatile var distanceLabels = true
    @Volatile var confidence = 0.45f
    @Volatile var boxColor = Color.GREEN

    // Package of the local, sideloaded Fortnite build to launch.
    // Cloud clients (GeForce NOW / Xbox) render server-side and expose no frames here.
    const val GAME_PACKAGE = "com.epicgames.fortnite"
}
