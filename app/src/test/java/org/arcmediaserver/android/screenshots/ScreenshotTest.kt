package org.arcmediaserver.android.screenshots

import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.performKeyInput
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.asImage
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import com.github.takahirom.roborazzi.captureRoboImage
import org.arcmediaserver.android.data.Item
import org.arcmediaserver.android.ui.browse.BrowseScreen
import org.arcmediaserver.android.ui.browse.BrowseUiState
import org.arcmediaserver.android.ui.browse.LibraryPage
import org.arcmediaserver.android.ui.browse.Section
import org.arcmediaserver.android.ui.components.LoadState
import org.arcmediaserver.android.ui.components.UiError
import org.arcmediaserver.android.ui.details.Details
import org.arcmediaserver.android.ui.details.DetailsPanel
import org.arcmediaserver.android.ui.details.TitleActions
import org.arcmediaserver.android.ui.details.DetailsScreen
import org.arcmediaserver.android.ui.player.UpNextCard
import org.arcmediaserver.android.ui.search.SearchScreen
import org.arcmediaserver.android.ui.search.SearchUiState
import org.arcmediaserver.android.ui.search.searchSections
import org.arcmediaserver.android.ui.player.PlayerControls
import org.arcmediaserver.android.ui.player.PlayerUiState
import org.arcmediaserver.android.ui.player.PlayerMenu
import org.arcmediaserver.android.ui.player.PlayerMenuPanel
import org.arcmediaserver.android.ui.profiles.PinPrompt
import org.arcmediaserver.android.ui.profiles.ProfilesScreen
import org.arcmediaserver.android.ui.profiles.ProfilesUiState
import org.arcmediaserver.android.ui.server.ServerProblem
import org.arcmediaserver.android.ui.server.ServerScreen
import org.arcmediaserver.android.ui.server.ServerUiState
import org.arcmediaserver.android.ui.signin.SignInScreen
import org.arcmediaserver.android.ui.signin.SignInUiState
import org.arcmediaserver.android.ui.theme.ArcTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders each screen at a TV's 1920x1080 (960x540dp at xhdpi) on the JVM, into
 * app/screenshots/tv-*.png: `./gradlew recordRoborazziDebug`. Look at them after UI changes.
 */
@OptIn(coil3.annotation.ExperimentalCoilApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shot(name: String, keys: List<androidx.compose.ui.input.key.Key> = emptyList(), content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                LocalAsyncImagePreviewHandler provides AsyncImagePreviewHandler { request -> Fixtures.art(request.data.toString()).asImage() },
            ) {
                ArcTheme { Box(Modifier.fillMaxSize().background(org.arcmediaserver.android.ui.theme.ArcColors.Background)) { content() } }
            }
        }
        compose.waitForIdle()
        // Let focus land (lazy rows compose their items a frame later) and the focus animation finish.
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        for (key in keys) {
            @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
            compose.onRoot().performKeyInput { pressKey(key) }
            compose.mainClock.advanceTimeBy(600)
            compose.waitForIdle()
        }
        compose.onRoot().captureRoboImage("screenshots/tv-$name.png")
    }

    @Test
    fun server() = shot("server") {
        ServerScreen(ServerUiState(address = "192.168.1.20"), onEdit = {}, onConnect = {})
    }

    @Test
    fun serverError() = shot("server-error") {
        ServerScreen(ServerUiState(address = "192.168.1.99", problem = ServerProblem.Failed(UiError.Unreachable)), onEdit = {}, onConnect = {})
    }

    @Test
    fun signIn() = shot("sign-in") {
        SignInScreen(SignInUiState(server = "http://192.168.1.20:8099", code = "K7QF"), onNewCode = {}, onChangeServer = {})
    }

    @Test
    fun profiles() = shot("profiles") {
        ProfilesScreen(ProfilesUiState(profiles = LoadState.Ready(Fixtures.profiles)), onChoose = {}, onRetry = {}, onDigit = {}, onDelete = {})
    }

    @Test
    fun profilesServerName() = shot("profiles-server-name") {
        // The server's own name (Arc's Settings), under the heading.
        ProfilesScreen(ProfilesUiState(profiles = LoadState.Ready(Fixtures.profiles), serverName = "Living Room"), onChoose = {}, onRetry = {}, onDigit = {}, onDelete = {})
    }

    @Test
    fun watchlist() = shot("watchlist") {
        // The Watchlist from the sidebar, placed after the first library.
        browse(
            BrowseUiState(
                frame = LoadState.Ready(Fixtures.frame.copy(watchlistAt = 1)),
                section = Section.Watchlist,
                watchlist = LoadState.Ready(Fixtures.movies.take(5) + Fixtures.show),
            ),
            drawerOpen = true,
        )
    }

    @Test
    fun homeQueue() = shot("home-queue") {
        // The play queue, first while there's one to carry on: from the title it's on.
        val queued = listOf(Fixtures.movies[2], Fixtures.seasons[0].episodes[4], Fixtures.movies[4])
            .mapIndexed { i, item -> org.arcmediaserver.android.data.cardFor(item, key = "queue:e$i") }
        val rows = listOf(org.arcmediaserver.android.ui.browse.HomeRowState(org.arcmediaserver.android.data.QueueRow, LoadState.Ready(queued))) + Fixtures.homeRows
        browse(BrowseUiState(frame = LoadState.Ready(Fixtures.frame), rows = rows))
    }

    @Test
    fun moreMenuQueue() = shot("movie-more-menu-queue") {
        // On a server that keeps play queues: Play Next and Add to Queue.
        val menu = object : org.arcmediaserver.android.ui.components.CardMenu {
            override fun current(item: Item) = item
            override fun open(item: Item, onClose: () -> Unit, anchor: androidx.compose.ui.geometry.Rect?, inContinue: Boolean, queueEntry: String?) = Unit
            override val queue: ((Item, Boolean) -> Unit)? = { _, _ -> }
        }
        androidx.compose.runtime.CompositionLocalProvider(org.arcmediaserver.android.ui.components.LocalCardMenu provides menu) {
            DetailsScreen(Details(Fixtures.movieWithFile), onPlay = { _: Item, _: Long -> }, onOpen = {}, initialPanel = DetailsPanel.More)
        }
    }

    @Test
    fun showShuffle() = shot("show-shuffle") {
        DetailsScreen(
            Details(Fixtures.show, Fixtures.seasons, next = Fixtures.seasons[0].episodes[2], initialSeason = 0),
            onPlay = { _: Item, _: Long -> },
            onOpen = {},
            actions = org.arcmediaserver.android.ui.details.TitleActions(onShuffle = {}),
        )
    }

    @Test
    fun pin() = shot("pin") {
        ProfilesScreen(
            ProfilesUiState(profiles = LoadState.Ready(Fixtures.profiles), pin = PinPrompt(Fixtures.profiles[1], digits = "12")),
            onChoose = {}, onRetry = {}, onDigit = {}, onDelete = {},
        )
    }

    @Composable
    private fun browse(state: BrowseUiState, drawerOpen: Boolean = false, search: @Composable () -> Unit = {}) {
        BrowseScreen(
            state, onSection = {}, onOpen = {}, onRetry = {}, onRetryRow = {}, onRetryLibrary = {}, onNeedTitles = {},
            onSwitchProfile = {}, onSignOut = {},
            drawerValue = if (drawerOpen) androidx.tv.material3.DrawerValue.Open else androidx.tv.material3.DrawerValue.Closed,
            searchContent = search,
        )
    }

    @Test
    fun settings() = shot("settings") {
        org.arcmediaserver.android.ui.settings.SettingsContent(
            org.arcmediaserver.android.ui.settings.SettingsUiState(
                prefs = LoadState.Ready(org.arcmediaserver.android.data.Prefs(audioLanguage = "eng", subtitleMode = "foreign", autoSkip = true)),
                serverName = "Living Room Arc",
                profile = org.arcmediaserver.android.data.Profile("p1", "Alex", "violet"),
            ),
            onChange = { _, _, _ -> }, onRetry = {}, onSwitchServer = {}, onSignOut = {},
        )
    }

    @Test
    fun settingsAbout() = shot("settings-about") {
        org.arcmediaserver.android.ui.settings.SettingsContent(
            org.arcmediaserver.android.ui.settings.SettingsUiState(
                prefs = LoadState.Ready(org.arcmediaserver.android.data.Prefs()),
                serverName = "Living Room Arc",
                health = org.arcmediaserver.android.data.Health("Living Room Arc", "0.1.0", 1),
                address = "http://192.168.1.20:8099",
                local = true,
            ),
            onChange = { _, _, _ -> }, onRetry = {}, onSwitchServer = {}, onSignOut = {}, initialAbout = true,
        )
    }

    @Test
    fun settingsLanguage() = shot("settings-language", keys = List(2) { androidx.compose.ui.input.key.Key.DirectionDown } + androidx.compose.ui.input.key.Key.DirectionCenter) {
        org.arcmediaserver.android.ui.settings.SettingsContent(
            org.arcmediaserver.android.ui.settings.SettingsUiState(prefs = LoadState.Ready(org.arcmediaserver.android.data.Prefs(audioLanguage = "eng"))),
            onChange = { _, _, _ -> }, onRetry = {}, onSwitchServer = {}, onSignOut = {},
        )
    }

    @Test
    fun home() = shot("home") {
        browse(BrowseUiState(frame = LoadState.Ready(Fixtures.frame), rows = Fixtures.homeRows))
    }

    @Test
    fun cardMenuOnContinue() = shot("card-menu-continue") {
        // A card on Home's Continue Watching row: its menu offers Remove from Continue Watching.
        Box(Modifier.fillMaxSize().background(org.arcmediaserver.android.ui.theme.ArcColors.Background)) {
            org.arcmediaserver.android.ui.details.MoreMenu(
                Fixtures.movieWithFile, onWatched = {}, onWatchlist = {}, onMediaInfo = {}, onClose = {}, onRemoveContinue = {},
            )
        }
    }

    @Test
    fun cardMenuOnQueue() = shot("card-menu-queue") {
        // A card on Home's Queue row (not the one playing): arrange the queue.
        Box(Modifier.fillMaxSize().background(org.arcmediaserver.android.ui.theme.ArcColors.Background)) {
            org.arcmediaserver.android.ui.details.MoreMenu(
                Fixtures.movieWithFile, onWatched = {}, onWatchlist = {}, onMediaInfo = {}, onClose = {},
                queueMoves = org.arcmediaserver.android.ui.details.QueueMoves(earlier = {}, later = {}, remove = {}),
            )
        }
    }

    @Test
    fun homeContinueRemoved() = shot("home-continue-removed") {
        // The first Continue Watching title was just removed: the row goes on without it.
        val continueRow = Fixtures.homeRows.first { it.spec.source is org.arcmediaserver.android.data.RowSource.Continue }
        val gone = (continueRow.cards as LoadState.Ready).value.first().item.id
        val menu = object : org.arcmediaserver.android.ui.components.CardMenu {
            override fun current(item: Item) = item
            override fun open(item: Item, onClose: () -> Unit, anchor: androidx.compose.ui.geometry.Rect?, inContinue: Boolean, queueEntry: String?) = Unit
            override fun offContinue(id: String) = id == gone
        }
        androidx.compose.runtime.CompositionLocalProvider(org.arcmediaserver.android.ui.components.LocalCardMenu provides menu) {
            browse(BrowseUiState(frame = LoadState.Ready(Fixtures.frame), rows = Fixtures.homeRows))
        }
    }

    @Test
    fun homeSidebar() = shot("home-sidebar") {
        browse(BrowseUiState(frame = LoadState.Ready(Fixtures.frame), rows = Fixtures.homeRows), drawerOpen = true)
    }

    @Test
    fun library() = shot("library") {
        browse(
            BrowseUiState(
                frame = LoadState.Ready(Fixtures.frame),
                section = Section.Library(1),
                library = LoadState.Ready(LibraryPage.of(Fixtures.libraries[0], Fixtures.movies + Fixtures.moreMovies).copy(letters = listOf(org.arcmediaserver.android.data.LetterCount("a", 1), org.arcmediaserver.android.data.LetterCount("b", 1), org.arcmediaserver.android.data.LetterCount("d", 1), org.arcmediaserver.android.data.LetterCount("e", 1), org.arcmediaserver.android.data.LetterCount("g", 1), org.arcmediaserver.android.data.LetterCount("i", 1), org.arcmediaserver.android.data.LetterCount("m", 1), org.arcmediaserver.android.data.LetterCount("o", 1), org.arcmediaserver.android.data.LetterCount("p", 1), org.arcmediaserver.android.data.LetterCount("s", 1)))),
                libraryTab = org.arcmediaserver.android.ui.browse.LibraryTab.Titles,
                libraryExtras = libraryExtras,
            ),
        )
    }

    private val libraryExtras = org.arcmediaserver.android.ui.browse.LibraryExtras(
        recommended = LoadState.Ready(
            listOf(
                org.arcmediaserver.android.data.RecommendedRow(type = "recently_added", items = Fixtures.movies),
                org.arcmediaserver.android.data.RecommendedRow(type = "because_you_watched", name = "Dune: Part Two", items = Fixtures.movies.reversed()),
            ),
        ),
        collections = LoadState.Ready(
            listOf(
                org.arcmediaserver.android.data.CollectionRef("c1", "Dune Collection", "auto", 2, org.arcmediaserver.android.data.Images(primary = "art:poster:Dune Collection")),
                org.arcmediaserver.android.data.CollectionRef("c2", "Spider-Man: Spider-Verse Collection", "auto", 2, org.arcmediaserver.android.data.Images(primary = "art:poster:Spider-Man: Spider-Verse Collection")),
            ),
        ),
    )

    @Test
    fun libraryScrolled() = shot("library-scrolled", keys = List(2) { androidx.compose.ui.input.key.Key.DirectionDown }) {
        // Two rows down: the third row's titles show whole under its cards.
        browse(
            BrowseUiState(
                frame = LoadState.Ready(Fixtures.frame),
                section = Section.Library(1),
                library = LoadState.Ready(LibraryPage.of(Fixtures.libraries[0], (1..4).flatMap { n -> Fixtures.movies.map { it.copy(id = it.id + n, title = it.title + " " + n) } })),
                libraryTab = org.arcmediaserver.android.ui.browse.LibraryTab.Titles,
                libraryExtras = libraryExtras,
            ),
        )
    }

    @Test
    fun librarySort() = shot("library-sort") {
        org.arcmediaserver.android.ui.browse.SortPanel(org.arcmediaserver.android.ui.browse.LibraryQuery(sort = "added"), onChoose = {}, onClose = {})
    }

    @Test
    fun libraryFilters() = shot("library-filters") {
        org.arcmediaserver.android.ui.browse.FilterPanel(
            org.arcmediaserver.android.ui.browse.LibraryQuery(unwatched = true, genre = "Drama"),
            org.arcmediaserver.android.data.Facets(genres = listOf(org.arcmediaserver.android.data.Facet("Drama", 4)), decades = listOf(org.arcmediaserver.android.data.Facet("2010", 3)), resolutions = listOf(org.arcmediaserver.android.data.Facet("4k", 2))),
            onChoose = {},
            onClose = {},
        )
    }

    @Test
    fun libraryRecommended() = shot("library-recommended") {
        browse(
            BrowseUiState(
                frame = LoadState.Ready(Fixtures.frame),
                section = Section.Library(1),
                library = LoadState.Ready(LibraryPage.of(Fixtures.libraries[0], Fixtures.movies)),
                libraryTab = org.arcmediaserver.android.ui.browse.LibraryTab.Recommended,
                libraryExtras = libraryExtras,
            ),
        )
    }

    @Test
    fun libraryFolders() = shot("library-folders") {
        // A home videos library's Folders tab, two folders deep: the way down above, two folders
        // (cover, Folder tag, count) and a video of its own.
        val video = Fixtures.movies[0].copy(id = "v1", kind = "movie", title = "Arrival day", year = 2024)
        val page = org.arcmediaserver.android.data.FolderPage(
            path = "0/Holidays 2024",
            trail = listOf(org.arcmediaserver.android.data.FolderCrumb("Holidays 2024", "0/Holidays 2024")),
            folders = listOf(
                org.arcmediaserver.android.data.VideoFolder("Cornwall", "0/Holidays 2024/Cornwall", 1, cover = "art:still:Cornwall"),
                org.arcmediaserver.android.data.VideoFolder("Spain", "0/Holidays 2024/Spain", 12, cover = "art:still:Spain"),
            ),
            items = listOf(video),
        )
        browse(
            BrowseUiState(
                frame = LoadState.Ready(Fixtures.frame),
                section = Section.Library(3),
                library = LoadState.Ready(LibraryPage.of(Fixtures.libraries[2], listOf(video))),
                libraryTab = org.arcmediaserver.android.ui.browse.LibraryTab.Folders,
                libraryExtras = libraryExtras.copy(folderPath = page.path, folders = LoadState.Ready(page), folderFocus = ""),
            ),
        )
    }

    @Test
    fun libraryCollections() = shot("library-collections") {
        browse(
            BrowseUiState(
                frame = LoadState.Ready(Fixtures.frame),
                section = Section.Library(1),
                library = LoadState.Ready(LibraryPage.of(Fixtures.libraries[0], Fixtures.movies)),
                libraryTab = org.arcmediaserver.android.ui.browse.LibraryTab.Collections,
                libraryExtras = libraryExtras,
            ),
        )
    }

    @Test
    fun homeError() = shot("home-error") {
        browse(BrowseUiState(frame = LoadState.Failed(UiError.Timeout)))
    }

    @Test
    fun movieAboutCards() = shot("movie-about-cards", keys = List(4) { androidx.compose.ui.input.key.Key.DirectionDown }) {
        // The foot of a film's page: About across the top, then Details, Crew and Playback.
        val credits = listOf(
            org.arcmediaserver.android.data.Credit(1, "Timothée Chalamet", "actor", "Paul Atreides", "art:person:Timothée Chalamet"),
            org.arcmediaserver.android.data.Credit(2, "Denis Villeneuve", "director", image = "art:person:Denis Villeneuve"),
            org.arcmediaserver.android.data.Credit(3, "Jon Spaihts", "writer", image = "art:person:Jon Spaihts"),
        )
        val film = Fixtures.movieWithFile.copy(credits = credits, releaseDate = "2024-03-01", studio = "Legendary Pictures", contentRating = "12A", genres = listOf("Science Fiction", "Adventure"), runtimeMin = 167)
        DetailsScreen(Details(film), onPlay = { _: Item, _: Long -> }, onOpen = {}, serverName = "Living Room Arc")
    }

    @Test
    fun movie() = shot("movie") {
        // With its cast, which should show on arrival under the buttons.
        val cast = listOf("Timothée Chalamet" to "Paul Atreides", "Zendaya" to "Chani", "Rebecca Ferguson" to "Jessica", "Javier Bardem" to "Stilgar", "Josh Brolin" to "Gurney Halleck", "Austin Butler" to "Feyd-Rautha")
            .mapIndexed { i, (name, role) -> org.arcmediaserver.android.data.Credit(personId = i.toLong(), name = name, role = "actor", character = role, image = "art:person:$name") } +
            org.arcmediaserver.android.data.Credit(personId = 99, name = "Denis Villeneuve", role = "director", image = "art:person:Denis Villeneuve")
        DetailsScreen(Details(Fixtures.movieWithFile.copy(credits = cast)), onPlay = { _: Item, _: Long -> }, onOpen = {})
    }

    @Test
    fun showFromEpisode() = shot("show-from-episode") {
        // Opened from an episode's card on Home: the show's page, focused on that episode.
        DetailsScreen(
            Details(Fixtures.show, Fixtures.seasons, next = Fixtures.seasons[0].episodes[2], initialSeason = 0, focus = Fixtures.seasons[0].episodes[3]),
            onPlay = { _: Item, _: Long -> },
            onOpen = {},
        )
    }

    @Test
    fun movieRowBack() = shot("movie-row-back", keys = listOf(androidx.compose.ui.input.key.Key.DirectionDown) + List(9) { androidx.compose.ui.input.key.Key.DirectionRight } + List(9) { androidx.compose.ui.input.key.Key.DirectionLeft }) {
        // Into the cast, along it and back: the first card returns to the row's start, not the edge.
        val cast = (1..10).map { org.arcmediaserver.android.data.Credit(personId = it.toLong(), name = "Actor $it", role = "actor", character = "Role $it") }
        DetailsScreen(Details(Fixtures.movieWithFile.copy(credits = cast)), onPlay = { _: Item, _: Long -> }, onOpen = {})
    }

    @Test
    fun showRowBack() = shot("show-row-back", keys = List(2) { androidx.compose.ui.input.key.Key.DirectionDown } + List(6) { androidx.compose.ui.input.key.Key.DirectionLeft }) {
        // Into the episodes and left to the first: it lines up with the row's start (48dp).
        DetailsScreen(
            Details(Fixtures.show, Fixtures.seasons, next = Fixtures.seasons[0].episodes[2], initialSeason = 0),
            onPlay = { _: Item, _: Long -> },
            onOpen = {},
        )
    }

    @Test
    fun showMissingEpisodes() = shot("show-missing-episodes", keys = List(2) { androidx.compose.ui.input.key.Key.DirectionDown } + List(2) { androidx.compose.ui.input.key.Key.DirectionRight }) {
        // Show Missing Episodes on: Season 2 gains two episodes with no file, dimmed and "Not on disk".
        val season = Fixtures.seasons[1].let { s ->
            s.copy(episodes = s.episodes + listOf(Fixtures.episode(2, 4, "Woe's Hollow", 54), Fixtures.episode(2, 5, "Trojan's Horse", 50)).map { it.copy(available = false, userData = null) })
        }
        DetailsScreen(
            Details(Fixtures.show, listOf(Fixtures.seasons[0], season), next = Fixtures.seasons[0].episodes[2], initialSeason = 1, missing = true),
            onPlay = { _: Item, _: Long -> },
            onOpen = {},
        )
    }

    @Test
    fun show() = shot("show-episodes") {
        DetailsScreen(
            Details(Fixtures.show, Fixtures.seasons, next = Fixtures.seasons[0].episodes[2], initialSeason = 0),
            onPlay = { _: Item, _: Long -> },
            onOpen = {},
        )
    }

    @Test
    fun showSpoilersHidden() = shot("show-spoilers-hidden", keys = listOf(androidx.compose.ui.input.key.Key.DirectionDown, androidx.compose.ui.input.key.Key.DirectionLeft, androidx.compose.ui.input.key.Key.DirectionDown) + List(2) { androidx.compose.ui.input.key.Key.DirectionRight }) {
        // Down to the tabs (it lands on Season 2), back to Season 1, into its episodes (from the
        // next one, 3) and along to 5, so watched 3 and hidden 4-6 are side by side.
        // Hide Spoilers on: episodes 4-6 aren't started, so they show the show's background (the
        // last has none, so its own still is blurred) and no description; 1-3 are as usual.
        val season = Fixtures.seasons[0].let { s ->
            s.copy(episodes = s.episodes.mapIndexed { i, e -> if (i == 5) e.copy(images = e.images?.copy(seriesBackdrop = null)) else e })
        }
        CompositionLocalProvider(org.arcmediaserver.android.ui.components.LocalHideSpoilers provides true) {
            DetailsScreen(
                Details(Fixtures.show, listOf(season) + Fixtures.seasons.drop(1), next = season.episodes[2], initialSeason = 0),
                onPlay = { _: Item, _: Long -> },
                onOpen = {},
            )
        }
    }

    @Test
    fun showNotInOrder() = shot("show-not-in-order") {
        // Two files the show's order leaves out: the last tab, with the note and no episode numbers.
        val outside = listOf(
            // Arc numbers them season 0, episode 0: no number shows anywhere.
            Fixtures.episode(0, 0, "Inside Severance", 30),
            Fixtures.episode(0, 0, "Severance: The Lumon Orientation", 12, played = true).copy(id = "ep:sv:x:2"),
        )
        DetailsScreen(
            Details(
                Fixtures.show, Fixtures.seasons, next = Fixtures.seasons[0].episodes[2],
                initialSeason = Fixtures.seasons.size, unplaced = outside, orderName = "TheTVDB Aired Order",
            ),
            onPlay = { _: Item, _: Long -> },
            onOpen = {},
        )
    }

    @Test
    fun showSeasonMenu() = shot("show-season-menu", keys = listOf(androidx.compose.ui.input.key.Key.DirectionDown, androidx.compose.ui.input.key.Key.DirectionCenter)) {
        // Down to the season tabs (landing on Season 1, the season shown), then OK: its menu offers
        // Mark Season as Watched and Shuffle Season.
        DetailsScreen(
            Details(Fixtures.show, Fixtures.seasons, next = Fixtures.seasons[0].episodes[2], initialSeason = 0),
            onPlay = { _: Item, _: Long -> },
            onOpen = {},
            actions = org.arcmediaserver.android.ui.details.TitleActions(onShuffleSeason = {}),
        )
    }

    @Test
    fun showSeasonWatchedFailed() = shot("show-season-failed", keys = listOf(androidx.compose.ui.input.key.Key.DirectionDown)) {
        // A watched season (Season 2) whose change couldn't be saved: the note sits beside the tabs.
        val seasons = Fixtures.seasons.mapIndexed { i, s -> if (i == 1) s.copy(episodes = s.episodes.map { it.withWatched(true) }) else s }
        DetailsScreen(
            Details(Fixtures.show, seasons, next = Fixtures.seasons[0].episodes[2], initialSeason = 1),
            onPlay = { _: Item, _: Long -> },
            onOpen = {},
            actions = org.arcmediaserver.android.ui.details.TitleActions(problem = org.arcmediaserver.android.ui.details.ActionProblem.Season),
        )
    }

    @Test
    fun playerStats() = shot("player-stats") {
        // Stats for nerds over a remuxed Dolby Vision profile 8 film with TrueHD sent as DD+ 5.1.
        val plan = Fixtures.plan.copy(
            sourceKbps = 48_200,
            audioTracks = listOf(org.arcmediaserver.android.data.AudioTrack(1, "eng", codec = "truehd", channels = 8, default = true)),
            audioStream = 1,
            audioOutput = org.arcmediaserver.android.data.AudioOutput("eac3", 6, converted = true),
        )
        val state = PlayerUiState(plan = plan, loading = false, playing = true, positionMs = 600_000, bufferedMs = 642_000, durationMs = plan.durationMs)
        val stats = org.arcmediaserver.android.playback.PlaybackStats(
            videoDecoder = "c2.amlogic.dvhe.decoder", videoMime = "video/dolby-vision", videoCodecs = "dvhe.08.06",
            width = 3840, height = 2160, frameRate = 23.976f, colorTransfer = androidx.media3.common.C.COLOR_TRANSFER_ST2084,
            audioDecoder = null, audioMime = "audio/eac3", audioChannels = 6, droppedFrames = 2,
        )
        val caps = org.arcmediaserver.android.data.PlayerCaps(
            listOf("mp4", "mkv"), listOf("hevc"), listOf("aac", "eac3"), 2160,
            audioPassthrough = listOf("eac3"), hdrFormats = listOf("hdr10", "hlg", "dolby_vision"), dolbyVisionProfiles = listOf(5, 8),
        )
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.DarkGray)) {
            val (title, body) = org.arcmediaserver.android.ui.player.methodLabel(plan)
            org.arcmediaserver.android.ui.player.StatsPanel(title, body, org.arcmediaserver.android.ui.player.statsRows(state, plan, stats, caps))
        }
    }

    @Test
    fun playerControls() = shot("player") {
        Box(Modifier.fillMaxSize()) {
            androidx.compose.foundation.Image(
                bitmap = Fixtures.art("art:still:In Perpetuity").asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
            PlayerControls(
                state = PlayerUiState(
                    item = Fixtures.seasons[0].episodes[2],
                    plan = Fixtures.plan,
                    loading = false,
                    playing = true,
                    positionMs = 21 * 60_000L + 14_000,
                    durationMs = Fixtures.plan.durationMs,
                    bufferedMs = 26 * 60_000L,
                    next = Fixtures.seasons[0].episodes[3],
                ),
                visible = true,
                scrubMs = null,
                onPlayPause = {}, onBack10 = {}, onForward10 = {}, onScrub = {}, onSkip = {}, onNext = {}, onMenu = {}, onClose = {},
            )
        }
    }

    @Test
    fun playerSkipIntro() = shot("player-skip-intro") {
        Box(Modifier.fillMaxSize()) {
            androidx.compose.foundation.Image(
                bitmap = Fixtures.art("art:still:In Perpetuity").asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
            PlayerControls(
                state = PlayerUiState(item = Fixtures.seasons[0].episodes[2], plan = Fixtures.plan, loading = false, playing = true, positionMs = 160_000, durationMs = Fixtures.plan.durationMs),
                visible = false,
                scrubMs = null,
                onPlayPause = {}, onBack10 = {}, onForward10 = {}, onScrub = {}, onSkip = {}, onNext = {}, onMenu = {}, onClose = {},
            )
        }
    }

    @Test
    fun search() = shot("search") {
        browse(
            BrowseUiState(frame = LoadState.Ready(Fixtures.frame), section = Section.Search),
            search = {
                SearchScreen(
                    SearchUiState(text = "the", query = "the", sections = searchSections(Fixtures.searchResults)),
                    onType = {}, onOpen = {}, onPerson = {}, focusField = false,
                )
            },
        )
    }

    @Test
    fun searchCollectionsGenres() = shot("search-collections-genres") {
        browse(
            BrowseUiState(frame = LoadState.Ready(Fixtures.frame), section = Section.Search),
            search = {
                SearchScreen(
                    SearchUiState(
                        text = "drama", query = "drama",
                        sections = searchSections(
                            org.arcmediaserver.android.data.SearchResults(
                                collections = listOf(org.arcmediaserver.android.data.CollectionRef("c1", "Dune Collection", count = 2, overview = "Denis Villeneuve's adaptation of Frank Herbert's novel, in two parts.", images = org.arcmediaserver.android.data.Images(primary = "art:poster:Dune Collection"))),
                                genres = listOf(
                                    org.arcmediaserver.android.data.GenreMatch("Drama", 1, "Movies", 12),
                                    org.arcmediaserver.android.data.GenreMatch("Drama", 2, "TV", 4),
                                ),
                            ),
                        ),
                    ),
                    onType = {}, onOpen = {}, onPerson = {}, focusField = false,
                )
            },
        )
    }

    @Test
    fun movieRelated() = shot("movie-related") {
        // The rows under a title's page, as they appear once the page is scrolled down.
        androidx.compose.foundation.layout.Column(Modifier.padding(top = 24.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(20.dp)) {
            org.arcmediaserver.android.ui.components.Shelf("Cast", start = 56.dp) {
                items(Fixtures.profiles.size) { i -> org.arcmediaserver.android.ui.components.PersonCard(Fixtures.profiles[i].name, null, "Paul Atreides") {} }
            }
            org.arcmediaserver.android.ui.components.Shelf("More Like This", start = 56.dp) {
                items(Fixtures.movies.size) { i -> org.arcmediaserver.android.ui.components.MediaCardView(org.arcmediaserver.android.data.cardFor(Fixtures.movies[i]), landscape = false, onClick = {}) }
            }
        }
    }

    @Test
    fun movieActions() = shot("movie-actions") {
        DetailsScreen(Details(Fixtures.movieWithFile), onPlay = { _: Item, _: Long -> }, onOpen = {})
    }

    private val twoVersions = Fixtures.movieWithFile.copy(
        files = Fixtures.movieWithFile.files + Fixtures.movieWithFile.files.first().copy(id = 99, edition = "Director's Cut", size = 31_400_000_000),
    )

    @Test
    fun moreMenuRate() = shot("movie-more-menu-rate") {
        // The ⋯ menu on a film rated 3½ stars: Rate… shows them.
        val rated = Fixtures.movieWithFile.withRating(7)
        DetailsScreen(Details(rated), onPlay = { _: Item, _: Long -> }, onOpen = {}, initialPanel = DetailsPanel.More)
    }

    @Test
    fun ratingMenu() = shot("movie-rating") {
        // Rate…: five stars down to half a star, 3½ ticked and focused, then Clear Rating.
        val rated = Fixtures.movieWithFile.withRating(7)
        DetailsScreen(Details(rated), onPlay = { _: Item, _: Long -> }, onOpen = {}, initialPanel = DetailsPanel.Rating)
    }

    @Test
    fun moreMenuVersions() = shot("movie-more-menu-versions") {
        DetailsScreen(Details(twoVersions), onPlay = { _: Item, _: Long -> }, onOpen = {}, initialPanel = DetailsPanel.More, actions = TitleActions(onPlayVersion = { _, _, _ -> }))
    }

    @Test
    fun playVersion() = shot("movie-play-version") {
        DetailsScreen(Details(twoVersions), onPlay = { _: Item, _: Long -> }, onOpen = {}, initialPanel = DetailsPanel.Version, actions = TitleActions(onPlayVersion = { _, _, _ -> }))
    }

    @Test
    fun aboutPanel() = shot("movie-about") {
        val long = Fixtures.movieWithFile.copy(overview = List(4) { Fixtures.movieWithFile.overview }.joinToString(" "))
        DetailsScreen(Details(long), onPlay = { _: Item, _: Long -> }, onOpen = {}, initialPanel = DetailsPanel.About)
    }

    @Test
    fun collectionPlay() = shot("collection-play") {
        // A collection's page on a server with play queues: Play (focused) and Shuffle.
        org.arcmediaserver.android.ui.browse.CollectionScreen(
            LoadState.Ready(org.arcmediaserver.android.data.CollectionPage(org.arcmediaserver.android.data.CollectionRef("c1", "Dune Collection", count = 2, overview = "Denis Villeneuve's adaptation of Frank Herbert's novel, in two parts.", images = org.arcmediaserver.android.data.Images(primary = "art:poster:Dune Collection")), Fixtures.movies.take(3))),
            onOpen = {}, onRetry = {}, onPlay = {},
        )
    }

    @Test
    fun addToPlaylist() = shot("add-to-playlist") {
        // Add to Playlist… over a film's page: ordinary playlists only (the smart one isn't listed).
        DetailsScreen(Details(Fixtures.movieWithFile), onPlay = { _: Item, _: Long -> }, onOpen = {})
        org.arcmediaserver.android.ui.details.PlaylistPicker(
            Fixtures.movieWithFile,
            LoadState.Ready(
                listOf(
                    org.arcmediaserver.android.data.PlaylistInfo("p1", "Movie Night", count = 6),
                    org.arcmediaserver.android.data.PlaylistInfo("p2", "Unwatched 4K", count = 31, smartFilter = kotlinx.serialization.json.buildJsonObject { put("resolution", kotlinx.serialization.json.JsonPrimitive("4k")) }),
                    org.arcmediaserver.android.data.PlaylistInfo("p3", "Short List", count = 1),
                ),
            ),
            onPick = {}, onRetry = {}, onClose = {},
        )
    }

    @Test
    fun addToPlaylistNone() = shot("add-to-playlist-none") {
        // No playlists to add to: a note pointing to the web, and Close.
        DetailsScreen(Details(Fixtures.show), onPlay = { _: Item, _: Long -> }, onOpen = {})
        org.arcmediaserver.android.ui.details.PlaylistPicker(Fixtures.show, LoadState.Ready(emptyList()), onPick = {}, onRetry = {}, onClose = {})
    }

    @Test
    fun playlists() = shot("playlists") {
        // The sidebar's Playlists: a mosaic of four posters, and one with fewer titles (its first poster).
        val posters = Fixtures.movies.take(4).mapNotNull { it.images?.primary }
        browse(
            BrowseUiState(
                frame = LoadState.Ready(Fixtures.frame),
                section = Section.Playlists,
                playlists = LoadState.Ready(
                    listOf(
                        org.arcmediaserver.android.data.PlaylistInfo("p1", "Movie Night", count = 6, durationMs = 46_140_000, images = posters),
                        org.arcmediaserver.android.data.PlaylistInfo("p2", "Short List", count = 1, images = posters.take(1)),
                    ),
                ),
            ),
        )
    }

    @Test
    fun playlistPage() = shot("playlist-page") {
        // A playlist's page: its count and length, Play (focused) and Shuffle, then its titles in order.
        val entries = (Fixtures.movies.take(4) + Fixtures.seasons[0].episodes[1]).mapIndexed { i, it -> org.arcmediaserver.android.data.PlaylistEntry(i.toLong() + 1, it) }
        org.arcmediaserver.android.ui.browse.PlaylistScreen(
            LoadState.Ready(org.arcmediaserver.android.data.PlaylistPage(org.arcmediaserver.android.data.PlaylistInfo("p1", "Movie Night", count = 5, durationMs = 36_000_000), entries)),
            onOpen = {}, onRetry = {}, onPlay = {},
        )
    }

    @Test
    fun moreMenu() = shot("movie-more-menu") {
        DetailsScreen(Details(Fixtures.movieWithFile), onPlay = { _: Item, _: Long -> }, onOpen = {}, initialPanel = DetailsPanel.More)
    }

    @Test
    fun mediaInfo() = shot("movie-media-info") {
        DetailsScreen(Details(Fixtures.movieWithFile), onPlay = { _: Item, _: Long -> }, onOpen = {}, initialPanel = DetailsPanel.MediaInfo)
    }

    @Composable
    private fun playerOver(backdrop: String, content: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize()) {
            androidx.compose.foundation.Image(
                bitmap = Fixtures.art(backdrop).asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
            content()
        }
    }

    @Test
    fun upNext() = shot("player-up-next") {
        playerOver("art:backdrop:Mill Road") {
            PlayerControls(
                state = PlayerUiState(item = Fixtures.seasons[0].episodes[2], plan = Fixtures.plan, loading = false, playing = true, positionMs = 3_010_000, durationMs = Fixtures.plan.durationMs, next = Fixtures.seasons[0].episodes[3]),
                visible = false, scrubMs = null, upNextShown = true,
                onPlayPause = {}, onBack10 = {}, onForward10 = {}, onScrub = {}, onSkip = {}, onNext = {}, onMenu = {}, onClose = {},
            )
            UpNextCard(Fixtures.seasons[0].episodes[3], seconds = 7, onPlayNow = {}, onCancel = {})
        }
    }

    @Test
    fun scrubbing() = shot("player-scrubbing") {
        playerOver("art:still:In Perpetuity") {
            PlayerControls(
                state = PlayerUiState(
                    item = Fixtures.seasons[0].episodes[2], plan = osdState().plan, loading = false, playing = true,
                    positionMs = 21 * 60_000L, durationMs = Fixtures.plan.durationMs, bufferedMs = 26 * 60_000L, trickplay = Fixtures.trickplay,
                ),
                visible = true,
                scrubMs = 33 * 60_000L + 20_000,
                onPlayPause = {}, onBack10 = {}, onForward10 = {}, onScrub = {}, onSkip = {}, onNext = {}, onMenu = {}, onClose = {},
            )
        }
    }

    /** The playing episode, as the player's tabs and cards see it. */
    private fun osdState(): PlayerUiState {
        val chapters = listOf(
            org.arcmediaserver.android.data.Chapter(0, 300_000, "Cold Open"),
            org.arcmediaserver.android.data.Chapter(300_000, 1_200_000, "The Bridge"),
            org.arcmediaserver.android.data.Chapter(1_200_000, 2_400_000, "Low Water"),
            org.arcmediaserver.android.data.Chapter(2_400_000, 3_120_000, "Mill Road"),
        )
        val episode = Fixtures.seasons[0].episodes[2].copy(
            overview = "The river rises overnight and the mill's old gate gives way, forcing Nell to choose between the town and her brother.",
            runtimeMin = 52, releaseDate = "2024-03-14",
        )
        return PlayerUiState(
            item = episode, plan = Fixtures.plan.copy(chapters = chapters), loading = false, playing = true,
            positionMs = 21 * 60_000L + 14_000, durationMs = Fixtures.plan.durationMs, bufferedMs = 26 * 60_000L,
            trickplay = Fixtures.trickplay, episodes = Fixtures.seasons[0].episodes,
            cast = listOf(
                org.arcmediaserver.android.data.Credit(1, "Mara Ellison", "actor", "Nell Hart", "art:poster:Mara Ellison"),
                org.arcmediaserver.android.data.Credit(2, "Theo Lund", "actor", "Sam Hart"),
                org.arcmediaserver.android.data.Credit(3, "June Okafor", "actor", "Mayor Price", "art:poster:June Okafor"),
                org.arcmediaserver.android.data.Credit(4, "Ravi Shah", "actor", "Dr. Amin", "art:poster:Ravi Shah"),
            ),
        )
    }

    private fun osdShot(name: String, tab: org.arcmediaserver.android.playback.PlayerOsd.Tab) = shot(name) {
        playerOver("art:still:In Perpetuity") {
            PlayerControls(
                state = osdState(), visible = true, scrubMs = null, initialTab = tab,
                onPlayPause = {}, onBack10 = {}, onForward10 = {}, onScrub = {}, onSkip = {}, onNext = {}, onMenu = {}, onClose = {},
            )
        }
    }

    @Test
    fun playerInfoCard() = osdShot("player-card-info", org.arcmediaserver.android.playback.PlayerOsd.Tab.Info)

    @Test
    fun playerChaptersCard() = osdShot("player-card-chapters", org.arcmediaserver.android.playback.PlayerOsd.Tab.Chapters)

    @Test
    fun playerCastCard() = osdShot("player-card-cast", org.arcmediaserver.android.playback.PlayerOsd.Tab.Cast)

    @Test
    fun playerEpisodesCard() = osdShot("player-card-episodes", org.arcmediaserver.android.playback.PlayerOsd.Tab.Episodes)

    @Test
    fun playerPlaybackMenu() = shot("player-menu-playback") {
        playerOver("art:still:In Perpetuity") {
            val state = osdState()
            PlayerControls(
                state = state, visible = true, scrubMs = null, menu = PlayerMenu.Playback,
                onPlayPause = {}, onBack10 = {}, onForward10 = {}, onScrub = {}, onSkip = {}, onNext = {}, onMenu = {}, onClose = {},
            )
            PlayerMenuPanel(
                menu = PlayerMenu.Playback, state = state, plan = state.plan!!,
                onAudio = {}, onSubtitle = {}, onVersion = {}, onQuality = {}, onChapter = {}, onSpeed = {},
            )
        }
    }

    /** Text subtitles on, shown 1.5 s later than the file has them. */
    private fun timedState() = osdState().let { s ->
        val track = s.plan!!.subtitleTracks.first()
        s.copy(subtitle = track, subtitleOffsets = mapOf(org.arcmediaserver.android.playback.SubtitleTiming.key(s.plan!!.fileId, track) to 1_500L))
    }

    @Test
    fun playerSubtitlesMenuTiming() = shot("player-menu-subtitles-timing") {
        // The subtitle menu with text subtitles on: Timing shows after the tracks, with its value.
        playerOver("art:backdrop:Hollow Creek") {
            val state = timedState()
            PlayerControls(
                state = state, visible = true, scrubMs = null, menu = PlayerMenu.Subtitles,
                onPlayPause = {}, onBack10 = {}, onForward10 = {}, onScrub = {}, onSkip = {}, onNext = {}, onMenu = {}, onClose = {},
            )
            PlayerMenuPanel(
                menu = PlayerMenu.Subtitles, state = state, plan = state.plan!!,
                onAudio = {}, onSubtitle = {}, onVersion = {}, onQuality = {}, onChapter = {}, onSpeed = {},
            )
        }
    }

    @Test
    fun playerSubtitleTimingMenu() = shot("player-menu-subtitle-timing") {
        // Timing open: "Timing: +1.5s (later)", Show 0.5s Earlier / Later and Reset.
        playerOver("art:backdrop:Hollow Creek") {
            val state = timedState()
            PlayerControls(
                state = state, visible = true, scrubMs = null, menu = PlayerMenu.SubtitleTiming,
                onPlayPause = {}, onBack10 = {}, onForward10 = {}, onScrub = {}, onSkip = {}, onNext = {}, onMenu = {}, onClose = {},
            )
            PlayerMenuPanel(
                menu = PlayerMenu.SubtitleTiming, state = state, plan = state.plan!!,
                onAudio = {}, onSubtitle = {}, onVersion = {}, onQuality = {}, onChapter = {}, onSpeed = {},
            )
        }
    }

    @Test
    fun person() = shot("person") {
        val credits = listOf(
            org.arcmediaserver.android.data.PersonCredit(Fixtures.movies[0], listOf("actor"), "Commander Hale"),
            org.arcmediaserver.android.data.PersonCredit(Fixtures.movies[1], listOf("actor", "director"), "Ada"),
            org.arcmediaserver.android.data.PersonCredit(Fixtures.movies[2], listOf("actor"), "Dr. Wren"),
            org.arcmediaserver.android.data.PersonCredit(Fixtures.show, listOf("actor"), "Nell Hart"),
        )
        org.arcmediaserver.android.ui.person.PersonScreen(
            org.arcmediaserver.android.data.PersonPage(org.arcmediaserver.android.data.Person(1, "Mara Ellison", "art:poster:Mara Ellison", count = 4, knownFor = "actor"), credits),
            onOpen = {},
        )
    }
}
