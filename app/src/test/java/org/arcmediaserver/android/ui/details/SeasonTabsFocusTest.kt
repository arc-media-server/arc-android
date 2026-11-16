package org.arcmediaserver.android.ui.details

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.arcmediaserver.android.data.Item
import org.arcmediaserver.android.screenshots.Fixtures
import org.arcmediaserver.android.ui.theme.ArcTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * A show's page driven with the remote: Down from Play reaches the season tabs on the season
 * shown, not whichever tab is nearest Play (landing on a tab switches season).
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class SeasonTabsFocusTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun show(initialSeason: Int) {
        compose.setContent {
            ArcTheme {
                DetailsScreen(
                    Details(Fixtures.show, Fixtures.seasons, next = Fixtures.seasons[0].episodes[2], initialSeason = initialSeason),
                    onPlay = { _: Item, _: Long -> },
                    onOpen = {},
                )
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
    }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
    }

    @Test
    fun `Down from Play lands on the season shown`() {
        show(initialSeason = 0)
        press(Key.DirectionDown)
        compose.onNodeWithText("Season 1").assertIsFocused()
    }

    @Test
    fun `Down from Play lands on a later season when that's the one shown`() {
        show(initialSeason = 1)
        press(Key.DirectionDown)
        compose.onNodeWithText("Season 2").assertIsFocused()
    }

    @Test
    fun `back up from the episodes returns to the season's tab`() {
        show(initialSeason = 0)
        press(Key.DirectionDown)
        press(Key.DirectionDown) // into Season 1's episodes
        press(Key.DirectionUp)
        compose.onNodeWithText("Season 1").assertIsFocused()
    }

    @Test
    fun `Shuffle Season takes the season's episodes on disk, Shuffle the whole show's`() {
        assertEquals(Fixtures.seasons[1].episodes.map { it.id }, shuffleCandidates(Fixtures.seasons, 2).map { it.id })
        assertEquals(Fixtures.seasons.sumOf { it.episodes.size }, shuffleCandidates(Fixtures.seasons).size)
        val offDisk = Fixtures.seasons[1].let { s -> s.copy(episodes = s.episodes.mapIndexed { i, e -> if (i == 0) e.copy(available = false) else e }) }
        assertEquals(2, shuffleCandidates(listOf(offDisk), 2).size)
    }
}
