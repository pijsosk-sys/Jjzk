package com.redcore.booster.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionMachineTest {
    private val g = GraceConfig(otherAppMs = 10_000, launcherMs = 4_000, noActivityMs = 8_000, screenOffMs = 120_000)
    private fun game(p: String) = Foreground.App(p, AppKind.GAME)
    private val launcher = Foreground.App("launcher", AppKind.LAUNCHER)
    private val other = Foreground.App("chat", AppKind.OTHER)
    private val self = Foreground.App("com.redcore.booster", AppKind.SELF)

    @Test fun startsOnceForAGame() {
        val m = SessionMachine(g)
        assertEquals(listOf(SessionCommand.Start("a")), m.onForeground(game("a"), 0, autoStart = true))
        assertEquals("no duplicate session", emptyList<SessionCommand>(), m.onForeground(game("a"), 2_000, autoStart = true))
        assertEquals("a", m.active)
    }

    @Test fun homeScreenEndsAfterGrace() {
        val m = SessionMachine(g)
        m.onForeground(game("a"), 0, true)
        assertTrue(m.onForeground(launcher, 1_000, true).isEmpty())
        assertTrue(m.onForeground(launcher, 4_000, true).isEmpty())
        assertEquals(listOf(SessionCommand.End("a", EndReason.LEFT_TO_LAUNCHER)), m.onForeground(launcher, 5_000, true))
        assertNull(m.active)
    }

    @Test fun quickReturnCancelsTheEnd() {
        val m = SessionMachine(g)
        m.onForeground(game("a"), 0, true)
        m.onForeground(other, 1_000, true)
        assertTrue(m.onForeground(game("a"), 3_000, true).isEmpty())
        assertTrue("timer was reset", m.onForeground(other, 12_000, true).isEmpty())
        assertEquals(listOf(SessionCommand.End("a", EndReason.LEFT_TO_OTHER_APP)), m.onForeground(other, 22_000, true))
    }

    @Test fun switchingGamesEndsTheOldSessionFirst() {
        val m = SessionMachine(g)
        m.onForeground(game("a"), 0, true)
        assertEquals(
            listOf(SessionCommand.End("a", EndReason.SWITCHED_GAME), SessionCommand.Start("b")),
            m.onForeground(game("b"), 1_000, true)
        )
        assertEquals("b", m.active)
    }

    @Test fun screenOffUsesTheLongGrace() {
        val m = SessionMachine(g)
        m.onForeground(game("a"), 0, true)
        // The grace period counts from the first time the screen was seen off (60 s), not from the session start.
        assertTrue(m.onForeground(Foreground.ScreenOff, 60_000, true).isEmpty())
        assertTrue(m.onForeground(Foreground.Locked, 100_000, true).isEmpty())
        assertTrue(m.onForeground(Foreground.ScreenOff, 179_000, true).isEmpty())
        assertEquals(listOf(SessionCommand.End("a", EndReason.SCREEN_OFF)), m.onForeground(Foreground.ScreenOff, 180_000, true))
    }

    @Test fun briefLockKeepsTheSession() {
        val m = SessionMachine(g)
        m.onForeground(game("a"), 0, true)
        m.onForeground(Foreground.ScreenOff, 5_000, true)
        assertTrue(m.onForeground(game("a"), 30_000, true).isEmpty())
        assertEquals("a", m.active)
    }

    @Test fun unknownNeverStartsOrEnds() {
        val m = SessionMachine(g)
        assertTrue(m.onForeground(Foreground.Unknown, 0, true).isEmpty())
        m.onForeground(game("a"), 1, true)
        assertTrue(m.onForeground(Foreground.Unknown, 999_999, true).isEmpty())
        assertEquals("a", m.active)
    }

    @Test fun deadGameProcessEndsTheSession() {
        val m = SessionMachine(g)
        m.onForeground(game("a"), 0, true)
        m.onForeground(Foreground.NoActivity, 1_000, true)
        assertEquals(listOf(SessionCommand.End("a", EndReason.LEFT_TO_OTHER_APP)), m.onForeground(Foreground.NoActivity, 9_000, true))
    }

    @Test fun redcoreItselfIsNeutral() {
        val m = SessionMachine(g)
        m.onForeground(game("a"), 0, true)
        m.onForeground(other, 1_000, true)
        assertTrue(m.onForeground(self, 2_000, true).isEmpty())
        assertTrue("away timer cleared by REDCORE", m.onForeground(self, 600_000, true).isEmpty())
        assertEquals("a", m.active)
    }

    @Test fun watchModeOnlyEnds() {
        val m = SessionMachine(g)
        assertTrue("no auto start", m.onForeground(game("a"), 0, autoStart = false).isEmpty())
        m.onExternalStart("a")
        assertEquals(listOf(SessionCommand.End("a", EndReason.SWITCHED_GAME)), m.onForeground(game("b"), 1_000, autoStart = false))
        assertNull(m.active)
    }

    @Test fun userEndedSessionIsNotRestartedUntilTheGameIsLeft() {
        val m = SessionMachine(g)
        m.onForeground(game("a"), 0, true)
        m.onExternalEnd(byUser = true)
        assertTrue(m.onForeground(game("a"), 1_000, true).isEmpty())
        m.onForeground(launcher, 2_000, true)
        assertEquals(listOf(SessionCommand.Start("a")), m.onForeground(game("a"), 3_000, true))
    }

    @Test fun trackingLostEndsImmediately() {
        val m = SessionMachine(g)
        m.onForeground(game("a"), 0, true)
        assertEquals(listOf(SessionCommand.End("a", EndReason.TRACKING_LOST)), m.onTrackingLost())
        assertTrue(m.onTrackingLost().isEmpty())
    }

    @Test fun pendingEndCountsDown() {
        val m = SessionMachine(g)
        m.onForeground(game("a"), 0, true)
        assertNull(m.pendingEndInMs(0))
        m.onForeground(launcher, 1_000, true)
        assertEquals(3_000L, m.pendingEndInMs(2_000))
    }
}
