package com.redcore.booster.overlay

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.redcore.booster.SampleData
import com.redcore.booster.booster.EffectsState
import com.redcore.booster.data.FpsProbe
import com.redcore.booster.data.NotifMode
import com.redcore.booster.ui.theme.RedcoreTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w915dp-h412dp-land-xhdpi", application = Application::class)
class OverlayUiTest {
    @get:Rule val rule = createComposeRule()

    private fun state(fx: EffectsState, expanded: Boolean = false) = OverlayState(
        SampleData.stats(), SampleData.settings.copy(showBubble = true), SampleData.session, expanded, recording = false,
        dndOn = true, dndAllowed = true, brightness = 80, volume = 50, mic = false, fx = fx, notifMode = NotifMode.BARRAGE,
    )

    @Test fun realGameFpsIsLabelledFps() {
        rule.setContent { RedcoreTheme { OverlayUi(state(EffectsState(fps = 118, fpsSource = FpsProbe.Source.GAME_LAYER)), object : OverlayActions {}) } }
        rule.onNodeWithText("FPS").assertIsDisplayed()
        rule.onNodeWithText("118").assertIsDisplayed()
    }

    @Test fun displayCommitRateIsNeverCalledFps() {
        rule.setContent { RedcoreTheme { OverlayUi(state(EffectsState(fps = 60, fpsSource = FpsProbe.Source.DISPLAY_COMPOSITION)), object : OverlayActions {}) } }
        rule.onNodeWithText("SCREEN").assertIsDisplayed()
        rule.onNodeWithText("60/s").assertIsDisplayed()
        rule.onNodeWithText("FPS").assertDoesNotExist()
    }

    @Test fun noFpsReadingShowsNoNumber() {
        rule.setContent { RedcoreTheme { OverlayUi(state(EffectsState(fps = null, fpsNote = "needs ADB")), object : OverlayActions {}) } }
        rule.onNodeWithText("FPS").assertDoesNotExist()
        rule.onNodeWithText("SCREEN").assertDoesNotExist()
    }

    @Test fun tappingTheHudOpensTheMenu() {
        var expanded: Boolean? = null
        rule.setContent { RedcoreTheme { OverlayUi(state(EffectsState()), object : OverlayActions { override fun expand(e: Boolean) { expanded = e } }) } }
        rule.onNodeWithText("CPU").performClick()
        assertEquals(true, expanded)
    }
}
