package org.arcmediaserver.android.ui.browse

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.semantics.SemanticsProperties
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
    fun `Home keeps the first shelf backdrop while navigating lower shelves`() {
        val rows = (1..3).map { index ->
            val cards = movies.map { cardFor(it.copy(id = "${it.id}-row-$index")) }
            HomeRowState(
                HomeRowSpec("row-$index", RowSource.Collection("$index", "Shelf $index"), CardStyle.Thumb),
                LoadState.Ready(cards),
            )
        }
        val shown = mutableListOf<String>()
        compose.setContent {
            ArcTheme {
                HomeContent(rows, onOpen = {}, onRetryRow = {}, onFocusedCard = { it?.item?.id?.let(shown::add) })
            }
        }
        settle()
        press(Key.DirectionRight)
        assertEquals("movie-2-row-1", shown.last())
        val changes = shown.size
        press(Key.DirectionDown)
        assertTrue(bounds("Shelf 2").top < 120.dp)
        press(Key.DirectionRight)
        assertEquals(changes, shown.size)
        press(Key.DirectionUp)
        press(Key.DirectionLeft)
        assertEquals("movie-1-row-1", shown.last())
    }

    /** The focused card's name. */
    private fun focusedCard(): String? = compose.onNode(isFocused()).fetchSemanticsNode().config.let { c ->
        c.getOrElseNullable(SemanticsProperties.ContentDescription) { null }?.firstOrNull()
            ?: c.getOrElseNullable(SemanticsProperties.Text) { null }?.firstOrNull()?.text
    }

    @Test
    fun `Home shelves return to the card last focused in them, or their first`() {
        // Card widths differ between shelves (as Continue Watching's over posters), so the card
        // nearest the one left isn't the one in the same place.
        val styles = listOf(CardStyle.Thumb, CardStyle.Poster, CardStyle.Backdrop, CardStyle.Poster, CardStyle.Thumb)
        val rows = styles.mapIndexed { i, style ->
            HomeRowState(
                HomeRowSpec("row-$i", RowSource.Collection("$i", "Shelf $i"), style),
                LoadState.Ready((1..12).map { cardFor(Item(id = "r$i-m$it", kind = "movie", title = "R$i M$it")) }),
            )
        }
        compose.setContent {
            ArcTheme {
                HomeContent(rows, onOpen = {}, onRetryRow = {}, onFocusedCard = {})
            }
        }
        settle()
        fun go(key: Key, expected: String) {
            press(key)
            assertEquals(expected, focusedCard())
        }
        go(Key.DirectionRight, "R0 M2")
        go(Key.DirectionRight, "R0 M3")
        // A first visit lands on the shelf's first card, not the one under R0 M3.
        go(Key.DirectionDown, "R1 M1")
        repeat(3) { press(Key.DirectionRight) }
        assertEquals("R1 M4", focusedCard())
        go(Key.DirectionDown, "R2 M1")
        go(Key.DirectionDown, "R3 M1")
        // Moving up lands on the card left in each shelf, though they scrolled off screen.
        go(Key.DirectionUp, "R2 M1")
        go(Key.DirectionUp, "R1 M4")
        go(Key.DirectionUp, "R0 M3")
        go(Key.DirectionDown, "R1 M4")
        go(Key.DirectionDown, "R2 M1")
        go(Key.DirectionDown, "R3 M1")
        go(Key.DirectionDown, "R4 M1")
        go(Key.DirectionUp, "R3 M1")
        go(Key.DirectionUp, "R2 M1")
        go(Key.DirectionUp, "R1 M4")
        go(Key.DirectionUp, "R0 M3")
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
