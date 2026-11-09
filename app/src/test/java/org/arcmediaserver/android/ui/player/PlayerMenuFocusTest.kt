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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The player's controls with a menu over them, wired as PlayerRoute wires them, driven with the
 * remote's keys: the timeline, the round buttons above it, the tabs below it, and the menus.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class PlayerMenuFocusTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var audio = 1
    private var speed = 1f
    private var zoom = false
    private var restarted = false

    private fun show() {
        compose.setContent {
            ArcTheme {
                var menu by remember { mutableStateOf(PlayerMenu.None) }
                var speedNow by remember { mutableStateOf(speed) }
                var zoomNow by remember { mutableStateOf(zoom) }
                BackHandler(enabled = menu != PlayerMenu.None) { menu = PlayerMenu.None }
                val state = PlayerUiState(
                    item = Fixtures.seasons[0].episodes[2], plan = Fixtures.plan, loading = false, playing = true,
                    durationMs = Fixtures.plan.durationMs, speed = speedNow, zoom = zoomNow, episodes = Fixtures.seasons[0].episodes,
                )
                PlayerControls(
                    state = state, visible = true, scrubMs = null,
                    onPlayPause = {}, onBack10 = {}, onForward10 = {}, onScrub = {}, onSkip = {}, onNext = {},
                    onMenu = { menu = it }, onClose = {}, menu = menu, onRestart = { restarted = true },
                )
                PlayerMenuPanel(
                    menu = menu, state = state, plan = Fixtures.plan,
                    onAudio = { audio = it; menu = PlayerMenu.None }, onSubtitle = {}, onVersion = {}, onQuality = {}, onChapter = {},
                    onSpeed = { speed = it; speedNow = it; if (menu != PlayerMenu.Playback) menu = PlayerMenu.None },
                    onZoom = { zoomNow = !zoomNow; zoom = zoomNow },
                    onOpen = { menu = it },
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

    private fun open(button: String) {
        compose.onNodeWithContentDescription(button).performSemanticsAction(SemanticsActions.RequestFocus)
        settle()
        compose.onNodeWithContentDescription(button).assertIsFocused()
        press(Key.DirectionCenter)
    }

    @Test
    fun `after choosing in a menu, the button that opened it has the focus and the remote moves on`() {
        show()
        open("Audio")
        // The menu opens on the current choice.
        compose.onNodeWithText("English").assertIsFocused()
        press(Key.DirectionDown)
        press(Key.DirectionCenter) // French
        assertEquals(2, audio)
        compose.onNodeWithContentDescription("Audio").assertIsFocused()
        press(Key.DirectionLeft)
        compose.onNodeWithContentDescription("Playback").assertIsFocused()
    }

    @Test
    fun `one Back closes a menu, and the controls still answer`() {
        show()
        open("Audio")
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        settle()
        compose.onNodeWithContentDescription("Audio").assertIsFocused()
        press(Key.DirectionRight)
        compose.onNodeWithContentDescription("Subtitles").assertIsFocused()
    }

    @Test
    fun `Playback's Zoom Mode and Speed change in place and the menu stays open`() {
        show()
        open("Playback")
        compose.onNodeWithText("Zoom Mode").assertIsFocused()
        press(Key.DirectionCenter)
        assertTrue(zoom)
        press(Key.DirectionDown)
        compose.onNodeWithText("Speed").assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(1.25f, speed)
        compose.onNodeWithText("1.25×").assertExists()
    }

    @Test
    fun `the timeline has the focus, Up reaches the buttons and Down the tabs and their cards`() {
        show()
        press(Key.DirectionUp)
        compose.onNodeWithContentDescription("Subtitles").assertIsFocused()
        press(Key.DirectionDown) // the timeline
        press(Key.DirectionDown) // the tabs
        compose.onNodeWithText("Info").assertIsFocused()
        // The card replaces the title and timeline.
        compose.onNodeWithContentDescription("Subtitles").assertDoesNotExist()
        press(Key.DirectionDown)
        compose.onNodeWithContentDescription("Restart").assertIsFocused()
        press(Key.DirectionCenter)
        assertTrue(restarted)
        press(Key.DirectionUp)
        compose.onNodeWithText("Info").assertIsFocused()
        press(Key.DirectionRight)
        compose.onNodeWithText("Chapters").assertIsFocused()
        press(Key.DirectionUp) // back to the timeline, with the title and buttons
        compose.onNodeWithContentDescription("Subtitles").assertExists()
        press(Key.DirectionUp)
        compose.onNodeWithContentDescription("Subtitles").assertIsFocused()
    }
}
