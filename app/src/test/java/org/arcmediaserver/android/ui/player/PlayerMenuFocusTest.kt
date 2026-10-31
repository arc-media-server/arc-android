package org.arcmediaserver.android.ui.player

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.arcmediaserver.android.screenshots.Fixtures
import org.arcmediaserver.android.ui.theme.ArcTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The player's controls with a menu over them, wired as PlayerRoute wires them, driven with the
 * remote's keys: after a menu closes (a choice, or Back) the controls must still answer the remote.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class PlayerMenuFocusTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var speed = 1f

    private fun show() {
        compose.setContent {
            ArcTheme {
                var menu by remember { mutableStateOf(PlayerMenu.None) }
                BackHandler(enabled = menu != PlayerMenu.None) { menu = PlayerMenu.None }
                val state = PlayerUiState(plan = Fixtures.plan, loading = false, playing = true, durationMs = Fixtures.plan.durationMs, speed = speed)
                PlayerControls(
                    state = state, visible = true, scrubMs = null,
                    onPlayPause = {}, onBack10 = {}, onForward10 = {}, onScrub = {}, onSkip = {}, onNext = {},
                    onMenu = { menu = it }, onClose = {}, menu = menu,
                )
                PlayerMenuPanel(
                    menu = menu, state = state, plan = Fixtures.plan,
                    onAudio = {}, onSubtitle = {}, onVersion = {}, onQuality = {}, onChapter = {},
                    onSpeed = { speed = it; menu = PlayerMenu.None },
                )
            }
        }
        settle()
    }

    private fun settle() {
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
    }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        settle()
    }

    private fun openSpeed() {
        compose.onNodeWithContentDescription("Speed").performSemanticsAction(SemanticsActions.RequestFocus)
        settle()
        compose.onNodeWithContentDescription("Speed").assertIsFocused()
        press(Key.DirectionCenter)
        // The menu is open with its choices.
        compose.onNodeWithText("0.5×").assertExists()
        // It opens on the current choice.
        compose.onNodeWithText("Normal").assertIsFocused()
    }

    @Test
    fun `after choosing in a menu, the button that opened it has the focus and the remote moves on`() {
        show()
        openSpeed()
        press(Key.DirectionDown)
        press(Key.DirectionCenter) // 1.25×
        assertEquals(1.25f, speed)
        compose.onNodeWithContentDescription("Speed").assertIsFocused()
        press(Key.DirectionLeft)
        compose.onNodeWithContentDescription("Chapters").assertIsFocused()
    }

    @Test
    fun `one Back closes a menu, and the controls still answer`() {
        show()
        openSpeed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        settle()
        compose.onNodeWithContentDescription("Speed").assertIsFocused()
        press(Key.DirectionLeft)
        compose.onNodeWithContentDescription("Chapters").assertIsFocused()
    }
}
