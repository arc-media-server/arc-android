package org.arcmediaserver.android.ui.browse

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.arcmediaserver.android.data.CardStyle
import org.arcmediaserver.android.data.CollectionRef
import org.arcmediaserver.android.data.HomeRowSpec
import org.arcmediaserver.android.data.Item
import org.arcmediaserver.android.data.LetterCount
import org.arcmediaserver.android.data.Library
import org.arcmediaserver.android.data.RecommendedRow
import org.arcmediaserver.android.data.RowSource
import org.arcmediaserver.android.data.cardFor
import org.arcmediaserver.android.ui.components.LoadState
import org.arcmediaserver.android.ui.theme.ArcTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class BrowseLayoutTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val library = Library(id = 1, name = "Movies", kind = "movies")
    private val movies = (1..10).map { Item(id = "movie-$it", kind = "movie", title = "Movie $it") }

    private fun settle() {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        settle()
    }

    private fun bounds(label: String) = compose.onNodeWithText(label).getUnclippedBoundsInRoot()

    private fun assertShelfLanding() {
        val first = bounds("Shelf 1").top
        press(Key.DirectionDown)
        val second = bounds("Shelf 2").top
        assertTrue("the second shelf did not scroll into view", second < 120.dp)
        press(Key.DirectionDown)
        assertTrue("the third shelf did not scroll into view", bounds("Shelf 3").top < 120.dp)
        press(Key.DirectionUp)
        assertEquals(second, bounds("Shelf 2").top)
        press(Key.DirectionUp)
        assertEquals(first, bounds("Shelf 1").top)
    }

    @Test
    fun `Home shelves land at the same height in both directions and return to the hero`() {
        val rows = (1..4).map { index ->
            HomeRowState(
                HomeRowSpec("row-$index", RowSource.Collection("$index", "Shelf $index"), CardStyle.Poster),
                LoadState.Ready(movies.map { cardFor(it) }),
            )
        }
        compose.setContent {
            ArcTheme {
                HomeContent(rows, onOpen = {}, onRetryRow = {}, onFocusedCard = {})
            }
        }
        settle()
        assertShelfLanding()
    }

    @Test
    fun `Recommended shelves land at the same height in both directions`() {
        val rows = (1..4).map { index ->
            RecommendedRow(type = "custom", title = "Shelf $index", items = movies)
        }
        compose.setContent {
            ArcTheme {
                LibraryContent(
                    LoadState.Ready(LibraryPage.of(library, movies)), LibraryTab.Recommended,
                    LibraryExtras(recommended = LoadState.Ready(rows)),
                    onTab = {}, onOpen = {}, onOpenCollection = {}, onRetry = {}, onRetryExtras = {}, onNeed = {},
                )
            }
        }
        settle()
        assertShelfLanding()
    }

    @Test
    fun `the letter rail leaves Library posters the same width as Collections posters`() {
        val tab = mutableStateOf(LibraryTab.Titles)
        val page = LibraryPage.of(library, movies).copy(letters = listOf(LetterCount("a", 1), LetterCount("b", 9)))
        val collections = listOf(CollectionRef("one", "Collection One"), CollectionRef("two", "Collection Two"))
        compose.setContent {
            ArcTheme {
                LibraryContent(
                    LoadState.Ready(page), tab.value, LibraryExtras(collections = LoadState.Ready(collections)),
                    onTab = {}, onOpen = {}, onOpenCollection = {}, onRetry = {}, onRetryExtras = {}, onNeed = {},
                )
            }
        }
        settle()
        fun posterSize(title: String) = compose.onNode(hasText(title) and hasClickAction())
            .getUnclippedBoundsInRoot().let { (it.right - it.left) to (it.bottom - it.top) }
        val titlePoster = posterSize("Movie 2")
        compose.runOnIdle { tab.value = LibraryTab.Collections }
        settle()
        assertEquals(titlePoster, posterSize("Collection Two"))
    }
}
