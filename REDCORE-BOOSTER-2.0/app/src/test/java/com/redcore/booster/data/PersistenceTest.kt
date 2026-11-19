package com.redcore.booster.data

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import com.redcore.booster.testDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PersistenceTest {
    private val ctx: Application = ApplicationProvider.getApplicationContext()
    private lateinit var settings: SettingsRepository
    private lateinit var games: GameRepository

    @Before fun setUp() {
        settings = SettingsRepository(ctx, testDataStore(ctx))
        games = GameRepository(ctx, settings)
    }

    private fun installApp(pkg: String, label: String, game: Boolean) {
        val ai = ApplicationInfo().apply {
            packageName = pkg; name = label; nonLocalizedLabel = label
            if (game) category = ApplicationInfo.CATEGORY_GAME
        }
        val pi = PackageInfo().apply { packageName = pkg; applicationInfo = ai }
        val spm = shadowOf(ctx.packageManager)
        spm.installPackage(pi)
        val component = ComponentName(pkg, "$pkg.Main")
        spm.addActivityIfNotPresent(component)
        spm.addIntentFilterForActivity(component, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
        @Suppress("DEPRECATION")
        spm.addResolveInfoForIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            android.content.pm.ResolveInfo().apply { activityInfo = ActivityInfo().apply { packageName = pkg; name = "$pkg.Main"; applicationInfo = ai } })
    }

    @Test fun settingsPersist() = runBlocking<Unit> {
        settings.setMode(GameMode.LIMIT)
        settings.setFahrenheit(true)
        settings.setRestore(false)
        settings.put(SettingsRepository.K.coolTarget, 44)
        settings.put(SettingsRepository.K.notif, NotifMode.BLOCK.name)
        settings.setTriggerPoints(0.1f, 0.2f, 0.9f, 0.8f)
        val s = settings.current()
        assertEquals(GameMode.LIMIT, s.globalMode)
        assertTrue(s.fahrenheit)
        assertFalse(s.restoreOnExit)
        assertEquals(44, s.coolTarget)
        assertEquals(NotifMode.BLOCK, s.notifMode)
        assertEquals(0.9f, s.r1x, 0f)
        assertEquals("°F", s.tempUnit)
    }

    @Test fun unknownEnumValuesFallBackToDefaults() = runBlocking<Unit> {
        settings.put(SettingsRepository.K.mode, "TURBO_9000")
        settings.put(SettingsRepository.K.eq, "nonsense")
        val s = settings.current()
        assertEquals(GameMode.BALANCED, s.globalMode)
        assertEquals(EqPreset.OFF, s.eq)
    }

    @Test fun profileOverridesPersistAndApplyOnTopOfTheMode() = runBlocking<Unit> {
        games.add(listOf("com.game.a"))
        games.updateProfile(GameProfile("com.game.a", GameMode.BEAST, overlay = false, dnd = false, brightness = -1, refresh = RefreshTarget.LOWEST))
        val p = games.profile("com.game.a")!!
        assertEquals(GameMode.BEAST, p.mode)
        assertFalse(p.overlay)
        val e = p.effective()
        assertFalse(e.dnd)
        assertNull("-1 = leave brightness alone", e.brightness)
        assertEquals(RefreshTarget.LOWEST, e.refresh)
        assertTrue("other Beast settings still apply", e.cleanBackground && e.pauseSync)
    }

    @Test fun libraryAddRemoveHasNoDuplicatesAndRemembersRemovals() = runBlocking<Unit> {
        games.add(listOf("com.game.a", "com.game.b", "com.game.a"))
        games.add(listOf("com.game.b"))
        assertEquals(listOf("com.game.a", "com.game.b"), settings.library.first().map { it.pkg })
        games.remove("com.game.a")
        assertEquals(listOf("com.game.b"), settings.library.first().map { it.pkg })
        assertTrue("com.game.a" in settings.removed.first())
        games.add(listOf("com.game.a"))
        assertFalse("adding again un-removes it", "com.game.a" in settings.removed.first())
    }

    @Test fun scanAddsInstalledGamesOnceAndRespectsRemovals() = runBlocking<Unit> {
        installApp("com.studio.raiders", "Sky Raiders", game = true)
        installApp("com.example.notes", "Notes", game = false)
        assertEquals(1, games.scanForGames())
        assertEquals(0, games.scanForGames())
        assertEquals(listOf("com.studio.raiders"), settings.library.first().map { it.pkg })
        games.remove("com.studio.raiders")
        assertEquals("a removed game is not re-added by a scan", 0, games.scanForGames())
        assertTrue(settings.current().libraryScanned)
    }

    @Test fun libraryFlowReportsLabelsAndInstalledState() = runBlocking<Unit> {
        installApp("com.studio.raiders", "Sky Raiders", game = true)
        games.add(listOf("com.studio.raiders", "com.gone.game"))
        val lib = games.library.first()
        val installed = lib.first { it.profile.pkg == "com.studio.raiders" }
        assertTrue(installed.installed)
        assertFalse(lib.first { it.profile.pkg == "com.gone.game" }.installed)
    }

    @Test fun damagedLibraryEntriesAreSkippedNotFatal() = runBlocking<Unit> {
        settings.put(SettingsRepository.K.library, """[{"pkg":"ok.one","mode":"ECO"},{"mode":"BEAST"},{"pkg":"ok.two","brightness":"bright"},{"pkg":"ok.one"}]""")
        val lib = settings.library.first()
        assertEquals(listOf("ok.one", "ok.two"), lib.map { it.pkg })
        assertEquals(GameMode.ECO, lib[0].mode)
        assertNull(lib[1].brightness)
    }

    @Test fun sessionLogKeepsTheLatestEighty() = runBlocking<Unit> {
        repeat(90) { settings.logSession("com.game.a", it.toLong(), it + 1L) }
        val log = settings.sessions()
        assertEquals(80, log.size)
        assertEquals(89L, log.last().second)
    }
}
