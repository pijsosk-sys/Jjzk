package com.jjzk.toolbox.ui

import com.jjzk.toolbox.GameInstall
import com.jjzk.toolbox.WorldSummary

/**
 * Session-scope holder for the install + world fragments act on.
 * Set by HomeFragment, read by Items / World / Backups.
 */
object CurrentWorld {
    var install: GameInstall? = null
    var world: WorldSummary? = null
    val listeners = mutableListOf<() -> Unit>()
    fun notifyChanged() = listeners.toList().forEach { it() }
}
