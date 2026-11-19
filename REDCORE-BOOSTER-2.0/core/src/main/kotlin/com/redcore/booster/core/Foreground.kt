package com.redcore.booster.core

/** App-transition events, independent of Android's `UsageEvents` so the logic can be unit-tested. */
enum class AppEventType { RESUMED, PAUSED, STOPPED, OTHER }

data class AppEvent(val timeMs: Long, val pkg: String, val cls: String?, val type: AppEventType)

/** What kind of app a package is, from REDCORE's point of view. */
enum class AppKind { SELF, LAUNCHER, GAME, OTHER }

/**
 * What is on screen right now. Unknown is deliberately different from "no game": when REDCORE
 * cannot tell, it must not pretend the old foreground app is still there.
 */
sealed interface Foreground {
    /** No reliable information yet (no events observed). */
    data object Unknown : Foreground

    /** The screen is off. */
    data object ScreenOff : Foreground

    /** The screen is on but the lock screen is showing. */
    data object Locked : Foreground

    /** Events were seen, but no activity is resumed (an app paused and nothing replaced it yet). */
    data object NoActivity : Foreground

    data class App(val pkg: String, val kind: AppKind) : Foreground
}

/**
 * Rebuilds the set of resumed activities from a stream of resume/pause/stop events.
 *
 * Polls are incremental and may overlap; duplicate events are ignored. Split-screen can have more
 * than one resumed activity, so the set is kept and the caller decides which one matters.
 */
class ForegroundTracker(private val maxResumed: Int = 3, private val dedupeMemory: Int = 512) {
    private val resumed = LinkedHashMap<String, Pair<String, Long>>() // "pkg/cls" -> (pkg, resumedAt)
    private val seenKeys = HashSet<String>()
    private val seenOrder = ArrayDeque<String>()

    /** Newest event time processed (ms). Callers query from slightly before this to avoid gaps. */
    var lastEventTimeMs: Long = Long.MIN_VALUE
        private set

    /** True once any app event has been processed, i.e. [Foreground.Unknown] no longer applies. */
    var hasHistory: Boolean = false
        private set

    fun accept(events: Iterable<AppEvent>) {
        for (e in events.sortedBy { it.timeMs }) {
            if (e.type == AppEventType.OTHER) continue
            val key = "${e.timeMs}|${e.type}|${e.pkg}|${e.cls}"
            if (!seenKeys.add(key)) continue
            seenOrder.addLast(key)
            while (seenOrder.size > dedupeMemory) seenKeys.remove(seenOrder.removeFirst())
            hasHistory = true
            if (e.timeMs > lastEventTimeMs) lastEventTimeMs = e.timeMs
            val activity = "${e.pkg}/${e.cls ?: ""}"
            when (e.type) {
                AppEventType.RESUMED -> {
                    resumed.remove(activity)
                    resumed[activity] = e.pkg to e.timeMs
                    // A missed pause must never keep an old app "on screen" forever.
                    while (resumed.size > maxResumed) resumed.remove(resumed.keys.first())
                }
                AppEventType.PAUSED, AppEventType.STOPPED -> {
                    resumed.remove(activity)
                    // Some ROMs report pause with a different/blank class name: drop the package's entries
                    // that were resumed before this event.
                    if (e.cls.isNullOrEmpty()) resumed.entries.removeAll { it.value.first == e.pkg && it.value.second <= e.timeMs }
                }
                AppEventType.OTHER -> Unit
            }
        }
    }

    /** Packages with a resumed activity, most recently resumed first. */
    fun resumedPackages(): List<String> =
        resumed.values.sortedByDescending { it.second }.map { it.first }.distinct()

    /** Forget everything (used when tracking is lost, e.g. Usage access revoked). */
    fun reset() {
        resumed.clear(); seenKeys.clear(); seenOrder.clear()
        hasHistory = false; lastEventTimeMs = Long.MIN_VALUE
    }

    /**
     * Current foreground. A game anywhere in the resumed set wins (split-screen with a chat app still
     * counts as playing); otherwise the most recently resumed app.
     */
    fun current(classify: (String) -> AppKind): Foreground {
        val pkgs = resumedPackages()
        if (pkgs.isEmpty()) return if (hasHistory) Foreground.NoActivity else Foreground.Unknown
        val kinds = pkgs.map { it to classify(it) }
        kinds.firstOrNull { it.second == AppKind.GAME }?.let { return Foreground.App(it.first, AppKind.GAME) }
        val (pkg, kind) = kinds.first()
        return Foreground.App(pkg, kind)
    }

    companion object {
        /** Applies screen state, which always overrides app events. */
        fun withScreenState(appForeground: Foreground, interactive: Boolean, keyguardLocked: Boolean): Foreground = when {
            !interactive -> Foreground.ScreenOff
            keyguardLocked -> Foreground.Locked
            else -> appForeground
        }
    }
}
