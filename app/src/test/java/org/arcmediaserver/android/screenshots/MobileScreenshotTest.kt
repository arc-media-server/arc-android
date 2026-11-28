package org.arcmediaserver.android.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import com.github.takahirom.roborazzi.captureRoboImage
import org.arcmediaserver.android.data.Item
import org.arcmediaserver.android.ui.browse.BrowseUiState
import org.arcmediaserver.android.ui.browse.LibraryPage
import org.arcmediaserver.android.ui.browse.Section
import org.arcmediaserver.android.ui.components.LoadState
import org.arcmediaserver.android.ui.details.Details
import org.arcmediaserver.android.ui.mobile.Controls
import org.arcmediaserver.android.ui.mobile.MobileDetailsScreen
import org.arcmediaserver.android.ui.mobile.MobileHomeContent
import org.arcmediaserver.android.ui.mobile.MobileLibrary
import org.arcmediaserver.android.ui.mobile.MobilePersonScreen
import org.arcmediaserver.android.ui.mobile.MobileProfilesScreen
import org.arcmediaserver.android.ui.mobile.MobileSearch
import org.arcmediaserver.android.ui.mobile.MobileServerScreen
import org.arcmediaserver.android.ui.mobile.MobileSettingsScreen
import org.arcmediaserver.android.ui.mobile.MobileShellFrame
import org.arcmediaserver.android.ui.mobile.MobileSignInRoute
import org.arcmediaserver.android.ui.mobile.MobileTab
import org.arcmediaserver.android.ui.player.PlayerUiState
import org.arcmediaserver.android.ui.profiles.ProfilesUiState
import org.arcmediaserver.android.ui.search.SearchUiState
import org.arcmediaserver.android.ui.search.searchSections
import org.arcmediaserver.android.ui.server.ServerUiState
import org.arcmediaserver.android.ui.settings.SettingsUiState
import org.arcmediaserver.android.ui.theme.ArcColors
import org.arcmediaserver.android.ui.theme.ArcMobileTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The phone and tablet interface on a 411x891dp phone (a Pixel's size) on the JVM, into
 * app/screenshots/phone-*.png (the tablet's into tablet-*.png): `./gradlew recordRoborazziDebug`.
 */
@OptIn(coil3.annotation.ExperimentalCoilApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-port-xxhdpi")
class MobileScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                LocalAsyncImagePreviewHandler provides AsyncImagePreviewHandler { request -> Fixtures.image(request) },
            ) {
                ArcMobileTheme { Box(Modifier.fillMaxSize().background(ArcColors.Background)) { content() } }
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    private val home = BrowseUiState(frame = LoadState.Ready(Fixtures.frame), rows = Fixtures.homeRows)

    @Test
    fun server() = shot("phone-server") {
        MobileServerScreen(
            ServerUiState(
                address = "192.168.1.20",
                found = listOf(org.arcmediaserver.android.data.FoundServer("s1", "Living room", "0.9.0", "http://192.168.1.20:8099")),
            ),
            onEdit = {}, onConnect = {}, onChoose = {}, onScan = {},
        )
    }

    @Test
    fun signIn() = shot("phone-sign-in") {
        MobileSignInRoute(onSignedIn = {}, onChangeServer = {})
    }

    @Test
    fun profiles() = shot("phone-profiles") {
        MobileProfilesScreen(ProfilesUiState(profiles = LoadState.Ready(Fixtures.profiles), serverName = "Living Room"), onChoose = {}, onRetry = {}, onDigit = {}, onDelete = {}, onCancelPin = {}, onSignOut = {})
    }

    @Test
    fun home() = shot("phone-home") {
        MobileShellFrame(MobileTab.Home, onTab = {}, profile = Fixtures.profiles[0], topBar = true, onAccount = {}) {
            MobileHomeContent(home, onCard = {}, onRetry = {}, onRetryRow = {})
        }
    }

    @Test
    fun library() = shot("phone-library") {
        MobileShellFrame(MobileTab.Libraries, onTab = {}, profile = Fixtures.profiles[0], topBar = false, onAccount = {}) {
            MobileLibrary(
                home.copy(section = Section.Library(1), library = LoadState.Ready(LibraryPage.of(Fixtures.libraries[0], Fixtures.movies + Fixtures.moreMovies))),
                onBack = {}, onOpen = {}, onNeed = {}, onRetry = {},
            )
        }
    }

    @Test
    fun search() = shot("phone-search") {
        MobileShellFrame(MobileTab.Search, onTab = {}, profile = Fixtures.profiles[0], topBar = true, onAccount = {}) {
            MobileSearch(SearchUiState(text = "the", query = "the", sections = searchSections(Fixtures.searchResults)), onType = {}, onCard = {}, onPerson = {})
        }
    }

    @Test
    fun film() = shot("phone-film") {
        MobileDetailsScreen(Details(Fixtures.movieWithFile), onPlay = { _: Item, _: Long -> }, onWatched = {}, onWatchlist = {}, onShuffle = null, onOpen = {}, onPerson = {})
    }

    @Test
    fun show() = shot("phone-show") {
        MobileDetailsScreen(
            Details(Fixtures.show, Fixtures.seasons, next = Fixtures.seasons[0].episodes[2], initialSeason = 0),
            onPlay = { _: Item, _: Long -> }, onWatched = {}, onWatchlist = {}, onShuffle = {}, onOpen = {}, onPerson = {},
        )
    }

    @Test
    fun showOnEpisode() = shot("phone-show-on-episode") {
        // Opened from an episode's card further into the season: the page scrolls to it.
        val ep = Fixtures.seasons[0].episodes.last()
        MobileDetailsScreen(
            Details(Fixtures.show, Fixtures.seasons, next = ep, focus = ep, initialSeason = 0),
            onPlay = { _: Item, _: Long -> }, onWatched = {}, onWatchlist = {}, onShuffle = {}, onOpen = {}, onPerson = {},
        )
    }

    @Test
    fun person() = shot("phone-person") {
        MobilePersonScreen(Fixtures.person, onOpen = {})
    }

    @Test
    fun settings() = shot("phone-settings") {
        MobileSettingsScreen(
            SettingsUiState(
                prefs = LoadState.Ready(org.arcmediaserver.android.data.Prefs()),
                serverName = "Living Room",
                profile = Fixtures.profiles[0],
                address = "http://192.168.1.20:8099",
                health = org.arcmediaserver.android.data.Health("Living Room", "0.9.0", 1),
            ),
            onBack = {}, onChange = { _, _, _ -> }, onRetry = {}, onSwitchProfile = {}, onSwitchServer = {}, onSignOut = {},
        )
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    fun player() = shot("phone-player") {
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.DarkGray)) {
            Controls(
                PlayerUiState(item = Fixtures.seasons[0].episodes[2], plan = Fixtures.plan, loading = false, playing = true, positionMs = 600_000, durationMs = Fixtures.plan.durationMs),
                dragMs = null, onDrag = {}, onDragEnd = {}, onTogglePlay = {}, onSeekBy = {}, onTracks = {}, onClose = {},
            )
        }
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
    fun tabletHome() = shot("tablet-home") {
        MobileShellFrame(MobileTab.Home, onTab = {}, profile = Fixtures.profiles[0], topBar = true, onAccount = {}) {
            MobileHomeContent(home, onCard = {}, onRetry = {}, onRetryRow = {})
        }
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
    fun tabletShow() = shot("tablet-show") {
        MobileDetailsScreen(
            Details(Fixtures.show, Fixtures.seasons, next = Fixtures.seasons[0].episodes[2], initialSeason = 0),
            onPlay = { _: Item, _: Long -> }, onWatched = {}, onWatchlist = {}, onShuffle = {}, onOpen = {}, onPerson = {},
        )
    }
}
