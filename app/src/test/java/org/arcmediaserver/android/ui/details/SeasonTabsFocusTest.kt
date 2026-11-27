package org.arcmediaserver.android.ui.details

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.arcmediaserver.android.data.RecentCard
import org.arcmediaserver.android.data.cardFor
import org.arcmediaserver.android.ui.components.ArcIcons
import org.arcmediaserver.android.ui.components.RoundIconButton
import org.arcmediaserver.android.ui.components.PillTabs
import org.arcmediaserver.android.ui.components.cardSubtitle
import org.junit.Assert.assertTrue
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
    fun `episode text and cast names scroll fully into view`() {
        show(initialSeason = 0)
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        // Focus enters the episodes on the one Play would start (S1 · E3 here), not the first.
        val episode = Fixtures.seasons[0].episodes[2]
        // The artwork's placeholder carries the title too: the caption under it is the last.
        val title = compose.onAllNodesWithText(episode.title).onLast().getUnclippedBoundsInRoot()
        assertTrue("episode title is below screen: $title", title.top >= 0.dp && title.bottom <= 540.dp)
        episode.overview?.takeIf { it.isNotBlank() }?.let {
            val description = compose.onNodeWithText(it).getUnclippedBoundsInRoot()
            assertTrue("episode description is below screen: $description", description.bottom <= 540.dp)
        }
        press(Key.DirectionDown)
        val actor = Fixtures.show.credits.first { it.role == "actor" }
        val name = compose.onNodeWithText(actor.name).getUnclippedBoundsInRoot()
        assertTrue("cast name is below screen: $name", name.top >= 0.dp && name.bottom <= 540.dp)
    }

    /** The focused node's text, merged from the card it's on. */
    private fun focusedText(): String = compose.onNode(androidx.compose.ui.test.isFocused()).fetchSemanticsNode().config
        .getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Text) { null }.orEmpty().joinToString(" ") { it.text }

    /** Whether the focused card is the cast card named [name] (the name sits under its picture). */
    private fun castFocused(name: String): Boolean {
        val card = compose.onNode(androidx.compose.ui.test.isFocused()).getUnclippedBoundsInRoot()
        val label = compose.onNodeWithText(name).getUnclippedBoundsInRoot()
        val middle = (label.left + label.right) / 2
        return middle > card.left && middle < card.right
    }

    @Test
    fun `rows land at the foot and focus comes back to the card left in them`() {
        show(initialSeason = 0)
        press(Key.DirectionDown)
        press(Key.DirectionDown) // the episodes, on S1 · E3 (Play's)
        val episodes = Fixtures.seasons[0].episodes
        press(Key.DirectionRight)
        assertTrue(focusedText(), focusedText().contains(episodes[3].title))
        press(Key.DirectionDown) // the cast, on its first card whichever episode was left
        val actors = Fixtures.show.credits.filter { it.role == "actor" }
        assertTrue(castFocused(actors.first().name))
        // The cast row ends 40dp above the foot of the screen, not at the top.
        val name = compose.onNodeWithText(actors.first().name).getUnclippedBoundsInRoot()
        assertTrue("cast row went to the top: $name", name.top > 270.dp && name.bottom <= 540.dp)
        press(Key.DirectionRight)
        press(Key.DirectionUp)
        assertTrue(focusedText(), focusedText().contains(episodes[3].title))
        press(Key.DirectionDown)
        assertTrue(castFocused(actors[1].name))
    }

    @Test
    fun `a show with more seasons than fit scrolls its tabs, and Up still goes back to the top`() {
        val seasons = (1..14).map { n -> Fixtures.seasons[0].copy(seasonNumber = n, name = "") }
        compose.setContent {
            ArcTheme {
                DetailsScreen(Details(Fixtures.show, seasons, next = seasons[0].episodes[2]), onPlay = { _: Item, _: Long -> }, onOpen = {})
            }
        }
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        press(Key.DirectionDown)
        compose.onNodeWithText("Season 1").assertIsFocused()
        repeat(13) { press(Key.DirectionRight) }
        compose.onNodeWithText("Season 14").assertIsFocused()
        val tab = compose.onNodeWithText("Season 14").getUnclippedBoundsInRoot()
        assertTrue("the last season's tab is off screen: $tab", tab.left >= 0.dp && tab.right <= 960.dp && tab.right > tab.left + 40.dp)
        press(Key.DirectionUp)
        // Focus left the tabs for the buttons above (the page scrolls back up to them).
        compose.onNode(androidx.compose.ui.test.isFocused()).assertExists()
        compose.onAllNodes(androidx.compose.ui.test.isFocused() and androidx.compose.ui.test.hasText("Season", substring = true)).assertCountEquals(0)
        // And back to the first season's tab, scrolled back into view.
        press(Key.DirectionDown)
        repeat(13) { press(Key.DirectionLeft) }
        compose.onNodeWithText("Season 1").assertIsFocused()
        assertTrue(compose.onNodeWithText("Season 1").getUnclippedBoundsInRoot().left >= 0.dp)
    }

    @Test
    fun `opening a linked episode lands at the same height as navigating down in the show`() {
        val linked = androidx.compose.runtime.mutableStateOf(false)
        val episode = Fixtures.seasons[0].episodes[2]
        compose.setContent {
            ArcTheme {
                androidx.compose.runtime.key(linked.value) {
                    DetailsScreen(
                        Details(Fixtures.show, Fixtures.seasons, next = episode, focus = episode.takeIf { linked.value }),
                        onPlay = { _, _ -> }, onOpen = {},
                    )
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        val manual = compose.onAllNodesWithText(episode.title).onLast().getUnclippedBoundsInRoot().top
        compose.runOnIdle { linked.value = true }
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        val direct = compose.onAllNodesWithText(episode.title).onLast().getUnclippedBoundsInRoot().top
        assertEquals(manual, direct)
    }

    @Test
    fun `More stays a 44dp circle when the row offers less width`() {
        compose.setContent {
            ArcTheme {
                Box(Modifier.width(30.dp)) {
                    RoundIconButton(ArcIcons.More, "More", {}, containerColor = androidx.compose.ui.graphics.Color.White)
                }
            }
        }
        compose.waitForIdle()
        val button = compose.onNodeWithContentDescription("More").getUnclippedBoundsInRoot()
        assertEquals(44.dp, button.right - button.left)
        assertEquals(44.dp, button.bottom - button.top)
    }

    @Test
    fun `standalone pills are 36dp high and 10dp apart`() {
        val selected = mutableIntStateOf(0)
        compose.setContent { ArcTheme { PillTabs(listOf("Recommended", "Library"), selected.intValue, { selected.intValue = it }) } }
        compose.waitForIdle()
        val first = compose.onNodeWithText("Recommended").getUnclippedBoundsInRoot()
        val second = compose.onNodeWithText("Library").getUnclippedBoundsInRoot()
        assertEquals(36.dp, first.bottom - first.top)
        assertEquals(10.dp, second.left - first.right)
        compose.onNodeWithText("Recommended").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        press(Key.DirectionRight)
        compose.onNodeWithText("Library").assertIsFocused()
        assertEquals(1, selected.intValue)
    }

    @Test
    fun `recent captions are season name batch count and episode code`() {
        compose.setContent {
            ArcTheme {
                Column {
                    androidx.tv.material3.Text(cardSubtitle(cardFor(RecentCard("season", Fixtures.show, seasonNumber = 2, episodes = 4)))!!)
                    androidx.tv.material3.Text(cardSubtitle(cardFor(RecentCard("series", Fixtures.show, episodes = 6)))!!)
                    androidx.tv.material3.Text(cardSubtitle(cardFor(RecentCard("episode", Fixtures.seasons[0].episodes[3])))!!)
                }
            }
        }
        compose.onNodeWithText("Season 2").assertExists()
        compose.onNodeWithText("6 new episodes").assertExists()
        compose.onNodeWithText("S1 · E4").assertExists()
    }

    @Test
    fun `Shuffle Season takes the season's episodes on disk, Shuffle the whole show's`() {
        assertEquals(Fixtures.seasons[1].episodes.map { it.id }, shuffleCandidates(Fixtures.seasons, 2).map { it.id })
        assertEquals(Fixtures.seasons.sumOf { it.episodes.size }, shuffleCandidates(Fixtures.seasons).size)
        val offDisk = Fixtures.seasons[1].let { s -> s.copy(episodes = s.episodes.mapIndexed { i, e -> if (i == 0) e.copy(available = false) else e }) }
        assertEquals(offDisk.episodes.size - 1, shuffleCandidates(listOf(offDisk), 2).size)
    }
}
