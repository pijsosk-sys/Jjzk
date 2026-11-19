package com.redcore.booster.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavStackTest {
    @Test fun tabsResetTheStackToHomePlusTab() {
        val n = NavStack()
        n.go(Dest.NETWORK); n.go(Dest.GAMES)
        assertEquals(listOf(Dest.HOME, Dest.GAMES), n.routes.map { it.dest })
    }

    @Test fun detailScreensPushAndBackPops() {
        val n = NavStack()
        n.go(Dest.GAMES)
        n.go(Dest.GAME_DETAIL, "com.game.a")
        assertEquals(Route(Dest.GAME_DETAIL, "com.game.a"), n.current)
        assertTrue(n.back())
        assertEquals(Dest.GAMES, n.current.dest)
        assertTrue(n.back())
        assertFalse("back at the root closes the app", n.back())
        assertEquals(Dest.HOME, n.current.dest)
    }

    @Test fun pushingTheCurrentScreenAgainIsIgnoredButADifferentArgumentIsNot() {
        val n = NavStack()
        n.go(Dest.TOOLS); n.go(Dest.TOOLS)
        assertEquals(2, n.depth)
        n.go(Dest.GAME_DETAIL, "a"); n.go(Dest.GAME_DETAIL, "b")
        assertEquals(4, n.depth)
    }

    @Test fun settingsFromTheRootBehavesLikeATab() {
        val n = NavStack()
        n.go(Dest.SETTINGS)
        assertEquals(listOf(Dest.HOME, Dest.SETTINGS), n.routes.map { it.dest })
        n.go(Dest.GAMES); n.go(Dest.GAME_DETAIL, "x"); n.go(Dest.SETTINGS)
        assertEquals("deeper in, Settings is pushed", Dest.SETTINGS, n.current.dest)
        assertEquals(4, n.depth)
    }

    @Test fun homeTabClearsEverything() {
        val n = NavStack()
        n.go(Dest.GAMES); n.go(Dest.ADD_GAMES); n.go(Dest.HOME)
        assertEquals(listOf(Dest.HOME), n.routes.map { it.dest })
    }
}
