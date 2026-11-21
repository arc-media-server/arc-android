package org.arcmediaserver.android.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.asImage
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import org.arcmediaserver.android.data.Item
import org.arcmediaserver.android.ui.browse.BrowseScreen
import org.arcmediaserver.android.ui.browse.BrowseUiState
import org.arcmediaserver.android.ui.components.CardMenu
import org.arcmediaserver.android.ui.components.LoadState
import org.arcmediaserver.android.ui.components.LocalCardMenu
import org.arcmediaserver.android.ui.details.MoreMenu
import org.arcmediaserver.android.ui.theme.ArcTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(coil3.annotation.ExperimentalCoilApi::class, androidx.compose.ui.test.ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w960dp-h540dp-land-television-xhdpi")
/** Holding OK on a card opens its menu (Home's cards here); letting go mustn't choose anything. */
class CardMenuHoldTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun holdingOkOpensTheMenuAndKeepsIt() {
        var closes = 0
        var watched = 0
        compose.setContent {
            var open by remember { mutableStateOf<Pair<Item, () -> Unit>?>(null) }
            val menu = remember {
                object : CardMenu {
                    override fun current(item: Item) = item
                    override fun open(item: Item, onClose: () -> Unit, anchor: androidx.compose.ui.geometry.Rect?, inContinue: Boolean, queueEntry: String?) {
                        open = item to onClose
                    }
                }
            }
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                LocalAsyncImagePreviewHandler provides AsyncImagePreviewHandler { request -> Fixtures.art(request.data.toString()).asImage() },
                LocalCardMenu provides menu,
            ) {
                ArcTheme {
                    Box(Modifier.fillMaxSize()) {
                        BrowseScreen(
                            BrowseUiState(frame = LoadState.Ready(Fixtures.frame), rows = Fixtures.homeRows),
                            onSection = {}, onOpen = {}, onRetry = {}, onRetryRow = {}, onRetryLibrary = {}, onNeedTitles = {},
                            onSwitchProfile = {}, onSignOut = {},
                        )
                        open?.let { (item, restore) ->
                            MoreMenu(item, onWatched = { watched++ }, onWatchlist = {}, onMediaInfo = {}, onClose = { closes++; open = null; restore() })
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        compose.onRoot().performKeyInput {
            keyDown(Key.DirectionCenter)
            advanceEventTime(900)
            keyUp(Key.DirectionCenter)
        }
        compose.mainClock.advanceTimeBy(800)
        compose.waitForIdle()
        // Letting go of OK isn't a choice: the menu stays open with nothing done.
        assertEquals("watched after letting go", 0, watched)
        assertEquals("closes after letting go", 0, closes)
        // A press of its own chooses the focused one (Mark as Watched).
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
        assertEquals("watched after a press", 1, watched)
        assertEquals("closes after a press", 1, closes)
    }
}
