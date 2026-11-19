package com.redcore.booster.booster

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PermissionsTest {
    private val ctx: Application = ApplicationProvider.getApplicationContext()

    @Test fun deniedRuntimePermissionsAreReportedAsMissing() {
        assertFalse(Permissions.granted(ctx, Perm.MICROPHONE))
        assertFalse(Permissions.granted(ctx, Perm.MEDIA))
        assertFalse("ADB-only grants are never assumed", Permissions.granted(ctx, Perm.SECURE_SETTINGS))
        assertFalse(Permissions.granted(ctx, Perm.FPS_ADB))
    }

    @Test fun grantedRuntimePermissionsAreSeen() {
        shadowOf(ctx).grantPermissions(Manifest.permission.RECORD_AUDIO, *Permissions.mediaPermissions())
        assertTrue(Permissions.granted(ctx, Perm.MICROPHONE))
        assertTrue(Permissions.granted(ctx, Perm.MEDIA))
    }

    @Test fun notificationsSwitchedOffForTheAppCountAsMissing() {
        shadowOf(ctx).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        shadowOf(nm).setNotificationsEnabled(true)
        assertTrue(Permissions.granted(ctx, Perm.NOTIFICATIONS))
        shadowOf(nm).setNotificationsEnabled(false)
        assertFalse(Permissions.granted(ctx, Perm.NOTIFICATIONS))
    }

    @Test fun dndAccessFollowsTheSystem() {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        shadowOf(nm).setNotificationPolicyAccessGranted(false)
        assertFalse(Permissions.granted(ctx, Perm.DND))
        shadowOf(nm).setNotificationPolicyAccessGranted(true)
        assertTrue(Permissions.granted(ctx, Perm.DND))
    }

    @Test fun everyPermissionHasAWayToGrantIt() {
        Perm.entries.filter { it != Perm.CALL_SCREENING }.forEach { p ->
            assertTrue("$p needs a settings screen or a runtime prompt",
                Permissions.settingsIntent(ctx, p) != null || Permissions.runtimePermissions(p).isNotEmpty())
        }
        assertTrue(Permissions.adbOnly.containsAll(listOf(Perm.SECURE_SETTINGS, Perm.FPS_ADB)))
    }

    @Test fun apkInstallsOnAndroid13AreFlaggedForRestrictedSettings() {
        assertTrue("installed by nobody (adb / file manager) on API 34", Permissions.restrictedSettingsLikely(ctx))
    }

    @Test @Config(sdk = [32]) fun noRestrictedSettingsBeforeAndroid13() {
        assertFalse(Permissions.restrictedSettingsLikely(ctx))
    }
}
