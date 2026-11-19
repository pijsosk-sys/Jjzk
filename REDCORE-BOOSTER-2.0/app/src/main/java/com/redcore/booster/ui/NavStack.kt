package com.redcore.booster.ui

import androidx.compose.runtime.mutableStateListOf

/**
 * Back stack for the app's screens. Main tabs (and Settings from the root) reset the stack to
 * Home → tab, so Back always returns to the Control Center; pushing the screen already on top is ignored.
 */
class NavStack(initial: Route = Route(Dest.HOME)) {
    val routes = mutableStateListOf(initial)
    val current: Route get() = routes.last()
    val depth: Int get() = routes.size

    fun go(dest: Dest, arg: String? = null) {
        if (dest in Dest.bottom || dest == Dest.SETTINGS && routes.size == 1) {
            routes.clear(); if (dest != Dest.HOME) routes.add(Route(Dest.HOME))
        }
        if (routes.lastOrNull()?.dest != dest || routes.last().arg != arg) routes.add(Route(dest, arg))
    }

    /** Pops one screen. Returns false at the root (the activity should then close). */
    fun back(): Boolean {
        if (routes.size <= 1) return false
        routes.removeAt(routes.lastIndex)
        return true
    }
}
