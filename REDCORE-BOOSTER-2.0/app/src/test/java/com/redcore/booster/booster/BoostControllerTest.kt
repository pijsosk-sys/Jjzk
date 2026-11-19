package com.redcore.booster.booster

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.redcore.booster.FakeSettingsBackend
import com.redcore.booster.core.Tweak
import com.redcore.booster.data.GameMode
import com.redcore.booster.data.GameRepository
import com.redcore.booster.data.ModeSpec
import com.redcore.booster.data.SettingsRepository
import com.redcore.booster.data.SettingsRepository.K
import com.redcore.booster.data.UsageRepository
import com.redcore.booster.testDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BoostControllerTest {
    private val ctx: Application = ApplicationProvider.getApplicationContext()
    private val original = mapOf(
        Tweak.DND_FILTER to "1", Tweak.DND_POLICY to "160,2,2,0,2",
        Tweak.BRIGHTNESS_MODE to "1", Tweak.BRIGHTNESS to "102",
        Tweak.AUTO_ROTATE to "1", Tweak.USER_ROTATION to "0",
        Tweak.MASTER_SYNC to "true",
        Tweak.ANIM_ANIMATOR to "1.0", Tweak.ANIM_TRANSITION to "1.0", Tweak.ANIM_WINDOW to "1.0",
        Tweak.PEAK_REFRESH to null, Tweak.MIN_REFRESH to null,
    )
    private lateinit var backend: FakeSettingsBackend
    private lateinit var settings: SettingsRepository
    private lateinit var controllers: MutableList<BoostController>

    private fun controller(): BoostController {
        val games = GameRepository(ctx, settings)
        return BoostController(ctx, settings, UsageRepository(ctx), games, backend, JournalStore(ctx)).also { controllers += it }
    }

    @Before fun setUp() = runBlocking<Unit> {
        Dispatchers.setMain(Dispatchers.Unconfined)
        backend = FakeSettingsBackend(original)
        settings = SettingsRepository(ctx, testDataStore(ctx))
        // No background sampling in unit tests.
        settings.put(K.fps, false); settings.put(K.ping, false)
        controllers = mutableListOf()
    }

    @After fun tearDown() {
        controllers.forEach { it.effects.stop() }
        Dispatchers.resetMain()
    }

    private fun assertOriginal() = original.forEach { (t, v) -> assertTrue("$t should be back to $v but is ${backend.values[t]}", backend.same(t, v)) }

    @Test fun sessionAppliesVerifiesAndRestoresEverything() = runBlocking<Unit> {
        val b = controller()
        val s = b.startSession("com.game.a", GameMode.BEAST, ModeSpec.of(GameMode.BEAST))
        assertEquals("2", backend.values[Tweak.DND_FILTER])
        assertEquals("255", backend.values[Tweak.BRIGHTNESS])
        assertEquals("0", backend.values[Tweak.BRIGHTNESS_MODE])
        assertEquals("0", backend.values[Tweak.AUTO_ROTATE])
        assertEquals("false", backend.values[Tweak.MASTER_SYNC])
        val bri = s.results.first { it.name == "Brightness" }
        assertEquals(ActionState.APPLIED, bri.state)
        assertTrue("before → after is shown", bri.detail.contains("→"))
        assertTrue(bri.verified)

        val restored = b.endSession()
        assertOriginal()
        assertTrue(restored.any { it.name == "Brightness" && it.state == ActionState.APPLIED })
        assertNull(b.session.value)
        assertTrue("journal is cleared after a full restore", JournalStore(ctx).load().isEmpty)
    }

    @Test fun userChangesDuringTheGameAreKept() = runBlocking<Unit> {
        val b = controller()
        b.startSession("com.game.a", GameMode.BEAST, ModeSpec.of(GameMode.BEAST))
        backend.values[Tweak.BRIGHTNESS] = "180" // the user dragged brightness in quick settings
        val out = b.endSession()
        assertEquals("180", backend.values[Tweak.BRIGHTNESS])
        assertTrue(out.any { it.name == Tweak.BRIGHTNESS.label && it.state == ActionState.SKIPPED && it.detail.startsWith("Kept your change") })
        assertEquals("other settings are still restored", "1", backend.values[Tweak.DND_FILTER])
    }

    @Test fun settingsRedcoreNeverChangedAreNotTouched() = runBlocking<Unit> {
        backend.values[Tweak.DND_FILTER] = "2" // the user already had Do Not Disturb on
        val b = controller()
        b.startSession("com.game.a", GameMode.ECO, ModeSpec.of(GameMode.ECO)) // Eco doesn't use DND
        b.endSession()
        assertEquals("2", backend.values[Tweak.DND_FILTER])
        assertTrue(backend.writes.none { it.first == Tweak.DND_FILTER })
    }

    @Test fun crashOrRebootIsRecoveredOnNextStart() = runBlocking<Unit> {
        val first = controller()
        first.startSession("com.game.a", GameMode.LIMIT, ModeSpec.of(GameMode.LIMIT))
        first.effects.stop()
        // The process dies here. A new process creates a new controller that reads the persisted journal.
        val second = controller()
        val report = second.recoverIfNeeded()
        assertNotNull(report)
        assertOriginal()
        assertTrue(JournalStore(ctx).load().isEmpty)
        assertNotNull(second.recovery.value)
        assertNull("a second recovery does nothing", second.recoverIfNeeded())
    }

    @Test fun newSessionAfterCrashDoesNotOverwriteTheTrueOriginals() = runBlocking<Unit> {
        // 1.3.1 bug: after a crash the next session saved the *boosted* values as the restore point.
        val first = controller()
        first.startSession("com.game.a", GameMode.BEAST, ModeSpec.of(GameMode.BEAST))
        first.effects.stop()
        val second = controller()
        second.startSession("com.game.a", GameMode.BEAST, ModeSpec.of(GameMode.BEAST))
        second.endSession()
        assertOriginal()
    }

    @Test fun revokedPermissionLeavesAPendingRestoreThatIsRetried() = runBlocking<Unit> {
        val b = controller()
        b.startSession("com.game.a", GameMode.BEAST, ModeSpec.of(GameMode.BEAST))
        backend.revoke(Tweak.BRIGHTNESS, Tweak.BRIGHTNESS_MODE, Tweak.AUTO_ROTATE, Tweak.USER_ROTATION)
        val out = b.endSession()
        assertTrue(out.any { it.name == Tweak.BRIGHTNESS.label && it.state == ActionState.BLOCKED })
        assertEquals("255", backend.values[Tweak.BRIGHTNESS])
        assertTrue(b.pendingRestore.value.any { it.tweak == Tweak.BRIGHTNESS })
        assertFalse("the original is kept, not thrown away", JournalStore(ctx).load().isEmpty)

        backend.grant(Tweak.BRIGHTNESS, Tweak.BRIGHTNESS_MODE, Tweak.AUTO_ROTATE, Tweak.USER_ROTATION)
        val retried = b.retryPendingRestore()
        assertTrue(retried.any { it.state == ActionState.APPLIED })
        assertOriginal()
        assertTrue(b.pendingRestore.value.isEmpty())
    }

    @Test fun restoreOffKeepsGameSettingsAndLeavesNoStaleRestorePoint() = runBlocking<Unit> {
        settings.setRestore(false)
        val b = controller()
        b.startSession("com.game.a", GameMode.BEAST, ModeSpec.of(GameMode.BEAST))
        val out = b.endSession()
        assertEquals("255", backend.values[Tweak.BRIGHTNESS])
        assertTrue(out.single().detail.contains("Restore settings after play"))
        assertTrue(JournalStore(ctx).load().isEmpty)
        assertNull(controller().recoverIfNeeded())
    }

    @Test fun modeSwitchPutsBackSettingsTheNewModeDoesNotUse() = runBlocking<Unit> {
        val b = controller()
        b.startSession("com.game.a", GameMode.BEAST, ModeSpec.of(GameMode.BEAST)) // brightness 100 %
        b.startSession("com.game.a", GameMode.BALANCED, ModeSpec.of(GameMode.BALANCED)) // brightness unchanged
        assertEquals("brightness released right away", "102", backend.values[Tweak.BRIGHTNESS])
        assertEquals("1", backend.values[Tweak.BRIGHTNESS_MODE])
        assertEquals("DND still on in Balance", "2", backend.values[Tweak.DND_FILTER])
        b.endSession()
        assertOriginal()
    }

    @Test fun switchingGamesKeepsTheOriginalsFromBeforeTheFirstGame() = runBlocking<Unit> {
        val b = controller()
        val a = b.startSession("com.game.a", GameMode.BEAST, ModeSpec.of(GameMode.BEAST))
        val second = b.startSession("com.game.b", GameMode.BEAST, ModeSpec.of(GameMode.BEAST))
        assertEquals("com.game.b", second.pkg)
        assertTrue("the new game gets its own start time", second.startedAt >= a.startedAt)
        assertEquals("102", JournalStore(ctx).load().entries.getValue(Tweak.BRIGHTNESS).original)
        b.endSession()
        assertOriginal()
    }

    @Test fun startingTheSameSessionTwiceIsANoOp() = runBlocking<Unit> {
        val b = controller()
        val s1 = b.startSession("com.game.a", GameMode.BEAST, ModeSpec.of(GameMode.BEAST))
        val writes = backend.writes.size
        val s2 = b.startSession("com.game.a", GameMode.BEAST, ModeSpec.of(GameMode.BEAST))
        assertSame(s1, s2)
        assertEquals(writes, backend.writes.size)
        b.endSession()
    }

    @Test fun hangupDimsAndReturnsToTheSessionBrightness() = runBlocking<Unit> {
        val b = controller()
        b.startSession("com.game.a", GameMode.BEAST, ModeSpec.of(GameMode.BEAST))
        b.beginHangup()
        assertEquals("3", backend.values[Tweak.BRIGHTNESS]) // 1 %
        assertTrue(b.hangupActive.value)
        b.endHangup()
        assertEquals("255", backend.values[Tweak.BRIGHTNESS])
        b.endSession()
        assertOriginal()
    }

    @Test fun hangupWithoutSessionIsCrashSafe() = runBlocking<Unit> {
        val b = controller()
        b.beginHangup()
        assertEquals("3", backend.values[Tweak.BRIGHTNESS])
        // Process dies during hang-up: the next start restores the brightness instead of leaving 1 %.
        controller().recoverIfNeeded()
        assertEquals("102", backend.values[Tweak.BRIGHTNESS])
    }

    @Test fun liveEffectsAreReadBackFromTheSystem() = runBlocking<Unit> {
        val b = controller()
        b.startSession("com.game.a", GameMode.BEAST, ModeSpec.of(GameMode.BEAST))
        assertTrue(b.liveEffects().first { it.name == "Brightness" }.holding)
        backend.values[Tweak.BRIGHTNESS] = "40"
        assertFalse(b.liveEffects().first { it.name == "Brightness" }.holding)
        b.endSession()
    }

    @Test fun blockedPermissionsAreReportedNotFaked() = runBlocking<Unit> {
        backend.revoke(Tweak.DND_FILTER, Tweak.DND_POLICY, Tweak.BRIGHTNESS, Tweak.BRIGHTNESS_MODE)
        val b = controller()
        val s = b.startSession("com.game.a", GameMode.BEAST, ModeSpec.of(GameMode.BEAST))
        assertEquals(ActionState.BLOCKED, s.results.first { it.name == "Do Not Disturb" }.state)
        assertEquals(ActionState.BLOCKED, s.results.first { it.name == "Brightness" }.state)
        assertEquals("1", backend.values[Tweak.DND_FILTER])
        b.endSession()
    }

    @Test fun lowLatencyWifiIsNeverClaimed() = runBlocking<Unit> {
        val s = controller().startSession(null, GameMode.BEAST, ModeSpec.of(GameMode.BEAST))
        assertEquals(ActionState.SKIPPED, s.results.first { it.name == "Low-latency Wi-Fi" }.state)
        controllers.last().endSession()
    }

    @Test fun androidFourteenBackgroundCleanIsHonest() = runBlocking<Unit> {
        val r = controller().cleanBackground()
        assertEquals(ActionState.SKIPPED, r.state)
        assertTrue(r.detail.contains("Android 14"))
    }

    @Test @Config(sdk = [33]) fun androidThirteenStillTriesAndNeverClaimsWithoutApps() = runBlocking<Unit> {
        val r = controller().cleanBackground()
        assertFalse("the Android 14 restriction doesn't apply on 13", r.detail.contains("Android 14"))
        // A fresh test device has no recently used apps (or no usage access): nothing may be claimed as closed.
        assertTrue(r.state != ActionState.APPLIED)
    }
}
