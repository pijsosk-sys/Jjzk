package com.redcore.booster.overlay

import android.app.Application
import android.view.View
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class OverlayWindowsTest {
    private val ctx: Application = ApplicationProvider.getApplicationContext()

    private class FakeHost : WindowHost {
        val shown = mutableListOf<View>()
        var rejectAdd: Throwable? = null
        var rejectUpdate: Throwable? = null
        override fun add(v: View, lp: WindowManager.LayoutParams) { rejectAdd?.let { throw it }; shown += v }
        override fun update(v: View, lp: WindowManager.LayoutParams) { rejectUpdate?.let { throw it }; check(v in shown) }
        override fun remove(v: View) { require(shown.remove(v)) { "not attached" } }
    }

    private fun lp() = WindowManager.LayoutParams()

    @Test fun acceptedWindowIsTracked() {
        val host = FakeHost(); val w = OverlayWindows(host)
        assertTrue(w.attach("main", View(ctx), lp()))
        assertTrue(w.isAttached("main"))
        assertEquals(setOf("main"), w.active.value)
    }

    @Test fun rejectedWindowIsNeverReportedAsActive() {
        // The 1.3.1 code returned the view even when addView threw, so the app believed the overlay existed.
        val host = FakeHost().apply { rejectAdd = WindowManager.BadTokenException("permission denied for window type 2038") }
        val failures = mutableListOf<String>()
        val w = OverlayWindows(host) { tag, _ -> failures += tag }
        assertFalse(w.attach("hangup", View(ctx), lp()))
        assertFalse(w.isAttached("hangup"))
        assertTrue(w.active.value.isEmpty())
        assertEquals(listOf("hangup"), failures)
    }

    @Test fun sameTagReplacesInsteadOfDuplicating() {
        val host = FakeHost(); val w = OverlayWindows(host)
        val first = View(ctx); val second = View(ctx)
        w.attach("crosshair", first, lp())
        w.attach("crosshair", second, lp())
        assertEquals(listOf(second), host.shown)
        assertEquals(second, w.view("crosshair"))
    }

    @Test fun reattachingTheSameViewOnlyUpdates() {
        val host = FakeHost(); val w = OverlayWindows(host)
        val v = View(ctx)
        w.attach("main", v, lp()); w.attach("main", v, lp())
        assertEquals(1, host.shown.size)
    }

    @Test fun failedUpdateDropsTheWindow() {
        val host = FakeHost(); val w = OverlayWindows(host)
        w.attach("stopwatch", View(ctx), lp())
        host.rejectUpdate = IllegalArgumentException("View not attached to window manager")
        assertFalse(w.update("stopwatch", lp()))
        assertFalse(w.isAttached("stopwatch"))
    }

    @Test fun detachIsSafeTwiceAndDetachAllClearsEverything() {
        val host = FakeHost(); val w = OverlayWindows(host)
        w.attach("a", View(ctx), lp()); w.attach("b", View(ctx), lp())
        assertTrue(w.detach("a"))
        assertFalse(w.detach("a"))
        w.detachAll()
        assertTrue(host.shown.isEmpty())
        assertTrue(w.active.value.isEmpty())
        assertNull(w.view("b"))
    }
}
