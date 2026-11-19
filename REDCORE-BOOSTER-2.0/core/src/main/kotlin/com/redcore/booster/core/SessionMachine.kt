package com.redcore.booster.core

/** How long REDCORE waits before it decides the player really left the game. */
data class GraceConfig(
    /** Another app in front (system dialogs, a quick reply, the maker's game panel). */
    val otherAppMs: Long = 10_000,
    /** The home screen. */
    val launcherMs: Long = 4_000,
    /** Nothing resumed (an app paused and nothing replaced it, e.g. the game process died). */
    val noActivityMs: Long = 8_000,
    /** Screen off or lock screen: a short lock shouldn't end the session, a long one should. */
    val screenOffMs: Long = 120_000,
)

enum class EndReason(val description: String) {
    LEFT_TO_LAUNCHER("You returned to the home screen"),
    LEFT_TO_OTHER_APP("You switched to another app"),
    SWITCHED_GAME("You opened another game"),
    SCREEN_OFF("The screen stayed off"),
    TRACKING_LOST("REDCORE can no longer see which app is open (Usage access was turned off)"),
    USER_ENDED("You ended the session"),
}

sealed interface SessionCommand {
    data class Start(val pkg: String) : SessionCommand
    data class End(val pkg: String, val reason: EndReason) : SessionCommand
}

/**
 * Turns a sequence of [Foreground] observations into session start/end commands.
 *
 * Guarantees: at most one active session; a game switch always ends the old session before starting
 * the new one; a game the user ended by hand is not restarted until it leaves the screen; unknown
 * foreground never ends or starts anything (no guessing in either direction).
 */
class SessionMachine(private val grace: GraceConfig = GraceConfig()) {
    var active: String? = null
        private set

    /** Game the user ended manually while it was still on screen. */
    var suppressed: String? = null
        private set

    private var awaySince: Long? = null
    private var awayReason: EndReason? = null

    /** Milliseconds until the pending away-timer ends the session, or null if none is pending. */
    fun pendingEndInMs(now: Long): Long? {
        val since = awaySince ?: return null
        return (graceFor(awayReason) - (now - since)).coerceAtLeast(0)
    }

    /**
     * @param autoStart true when "Auto game boost" is on; false in watch-only mode, where sessions are
     * started by the app (Play button) and this machine only ends them.
     */
    fun onForeground(fg: Foreground, now: Long, autoStart: Boolean): List<SessionCommand> {
        val out = mutableListOf<SessionCommand>()
        when (fg) {
            is Foreground.App -> when (fg.kind) {
                AppKind.GAME -> {
                    if (suppressed != null && suppressed != fg.pkg) suppressed = null
                    if (suppressed == fg.pkg) return out
                    if (active == fg.pkg) { clearAway(); return out }
                    active?.let { out += SessionCommand.End(it, EndReason.SWITCHED_GAME); active = null; clearAway() }
                    if (autoStart) { out += SessionCommand.Start(fg.pkg); active = fg.pkg }
                }
                AppKind.SELF -> {
                    // REDCORE itself is neutral: managing the session from the app must not end it.
                    suppressed = null
                    clearAway()
                }
                AppKind.LAUNCHER -> { suppressed = null; away(EndReason.LEFT_TO_LAUNCHER, now, out) }
                AppKind.OTHER -> { suppressed = null; away(EndReason.LEFT_TO_OTHER_APP, now, out) }
            }
            Foreground.NoActivity -> away(EndReason.LEFT_TO_OTHER_APP, now, out, grace.noActivityMs)
            Foreground.ScreenOff, Foreground.Locked -> away(EndReason.SCREEN_OFF, now, out)
            Foreground.Unknown -> Unit
        }
        return out
    }

    /** The app started a session itself (Play / Boost). */
    fun onExternalStart(pkg: String?) {
        active = pkg
        suppressed = null
        clearAway()
    }

    /** The session ended outside this machine. [byUser] while the game is still open suppresses auto-restart. */
    fun onExternalEnd(byUser: Boolean) {
        if (byUser) suppressed = active
        active = null
        clearAway()
    }

    /** Tracking is impossible (e.g. Usage access revoked): end immediately rather than keep a stale session. */
    fun onTrackingLost(): List<SessionCommand> {
        val a = active ?: return emptyList()
        active = null; clearAway()
        return listOf(SessionCommand.End(a, EndReason.TRACKING_LOST))
    }

    private fun away(reason: EndReason, now: Long, out: MutableList<SessionCommand>, graceOverride: Long? = null) {
        val a = active ?: return
        if (awaySince == null) awaySince = now
        awayReason = reason
        val limit = graceOverride ?: graceFor(reason)
        if (now - awaySince!! >= limit) {
            out += SessionCommand.End(a, reason)
            active = null
            clearAway()
        }
    }

    private fun graceFor(r: EndReason?) = when (r) {
        EndReason.LEFT_TO_LAUNCHER -> grace.launcherMs
        EndReason.SCREEN_OFF -> grace.screenOffMs
        else -> grace.otherAppMs
    }

    private fun clearAway() { awaySince = null; awayReason = null }
}
