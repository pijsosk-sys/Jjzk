package com.redcore.booster.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundTrackerTest {
    private val games = setOf("com.game.a", "com.game.b")
    private val classify: (String) -> AppKind = {
        when (it) {
            "com.redcore.booster" -> AppKind.SELF
            "com.sec.android.app.launcher" -> AppKind.LAUNCHER
            in games -> AppKind.GAME
            else -> AppKind.OTHER
        }
    }

    private fun ev(t: Long, pkg: String, type: AppEventType, cls: String = "Main") = AppEvent(t, pkg, cls, type)

    @Test fun unknownBeforeAnyEvent() {
        val t = ForegroundTracker()
        assertEquals(Foreground.Unknown, t.current(classify))
    }

    @Test fun resumedGameIsForeground() {
        val t = ForegroundTracker()
        t.accept(listOf(ev(1, "com.sec.android.app.launcher", AppEventType.RESUMED), ev(2, "com.sec.android.app.launcher", AppEventType.PAUSED), ev(3, "com.game.a", AppEventType.RESUMED)))
        assertEquals(Foreground.App("com.game.a", AppKind.GAME), t.current(classify))
    }

    @Test fun returningHomeIsSeen() {
        val t = ForegroundTracker()
        t.accept(listOf(ev(1, "com.game.a", AppEventType.RESUMED)))
        t.accept(listOf(ev(10, "com.game.a", AppEventType.PAUSED), ev(11, "com.sec.android.app.launcher", AppEventType.RESUMED)))
        assertEquals(Foreground.App("com.sec.android.app.launcher", AppKind.LAUNCHER), t.current(classify))
    }

    @Test fun pauseWithoutReplacementIsNoActivityNotTheOldGame() {
        // The old code fell back to the last known package here and kept a stale session alive.
        val t = ForegroundTracker()
        t.accept(listOf(ev(1, "com.game.a", AppEventType.RESUMED)))
        t.accept(listOf(ev(5, "com.game.a", AppEventType.PAUSED), ev(6, "com.game.a", AppEventType.STOPPED)))
        assertEquals(Foreground.NoActivity, t.current(classify))
    }

    @Test fun overlappingPollsAreDeduplicated() {
        val t = ForegroundTracker()
        val batch1 = listOf(ev(1, "com.game.a", AppEventType.RESUMED), ev(5, "com.game.a", AppEventType.PAUSED))
        t.accept(batch1)
        // Second poll overlaps the first one and adds the launcher.
        t.accept(batch1 + ev(6, "com.sec.android.app.launcher", AppEventType.RESUMED))
        assertEquals(Foreground.App("com.sec.android.app.launcher", AppKind.LAUNCHER), t.current(classify))
        assertEquals(6L, t.lastEventTimeMs)
    }

    @Test fun splitScreenKeepsTheGame() {
        val t = ForegroundTracker()
        t.accept(listOf(ev(1, "com.game.a", AppEventType.RESUMED), ev(2, "com.chat.app", AppEventType.RESUMED)))
        assertEquals(Foreground.App("com.game.a", AppKind.GAME), t.current(classify))
    }

    @Test fun missedPausesCannotPinAnOldApp() {
        val t = ForegroundTracker(maxResumed = 3)
        t.accept(listOf(
            ev(1, "com.game.a", AppEventType.RESUMED),
            ev(2, "x.one", AppEventType.RESUMED), ev(3, "x.two", AppEventType.RESUMED), ev(4, "x.three", AppEventType.RESUMED),
        ))
        assertTrue("oldest resumed entry is evicted", "com.game.a" !in t.resumedPackages())
    }

    @Test fun pauseWithBlankClassClearsThePackage() {
        val t = ForegroundTracker()
        t.accept(listOf(ev(1, "com.game.a", AppEventType.RESUMED, "UnityPlayerActivity")))
        t.accept(listOf(AppEvent(2, "com.game.a", null, AppEventType.PAUSED)))
        assertEquals(Foreground.NoActivity, t.current(classify))
    }

    @Test fun screenStateOverridesApps() {
        val game = Foreground.App("com.game.a", AppKind.GAME)
        assertEquals(Foreground.ScreenOff, ForegroundTracker.withScreenState(game, interactive = false, keyguardLocked = true))
        assertEquals(Foreground.Locked, ForegroundTracker.withScreenState(game, interactive = true, keyguardLocked = true))
        assertEquals(game, ForegroundTracker.withScreenState(game, interactive = true, keyguardLocked = false))
    }

    @Test fun resetForgetsHistory() {
        val t = ForegroundTracker()
        t.accept(listOf(ev(1, "com.game.a", AppEventType.RESUMED)))
        t.reset()
        assertEquals(Foreground.Unknown, t.current(classify))
    }
}
