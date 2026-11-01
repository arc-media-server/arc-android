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
            ),
            onChange = { _, _, _ -> }, onRetry = {}, onSwitchServer = {}, onSignOut = {},
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
                library = LoadState.Ready(LibraryPage.of(Fixtures.libraries[0], Fixtures.movies + Fixtures.movies.map { it.copy(id = it.id + "b", title = it.title + " II") }).copy(letters = listOf(org.arcmediaserver.android.data.LetterCount("g", 2), org.arcmediaserver.android.data.LetterCount("l", 2), org.arcmediaserver.android.data.LetterCount("n", 2), org.arcmediaserver.android.data.LetterCount("p", 2), org.arcmediaserver.android.data.LetterCount("q", 1), org.arcmediaserver.android.data.LetterCount("s", 4), org.arcmediaserver.android.data.LetterCount("t", 3)))),
                libraryTab = org.arcmediaserver.android.ui.browse.LibraryTab.Titles,
                libraryExtras = libraryExtras,
            ),
        )
    }

    private val libraryExtras = org.arcmediaserver.android.ui.browse.LibraryExtras(
        recommended = LoadState.Ready(
            listOf(
                org.arcmediaserver.android.data.RecommendedRow(type = "recently_added", items = Fixtures.movies),
                org.arcmediaserver.android.data.RecommendedRow(type = "because_you_watched", name = "The Long Orbit", items = Fixtures.movies.reversed()),
            ),
        ),
        collections = LoadState.Ready(
            listOf(
                org.arcmediaserver.android.data.CollectionRef("c1", "The Orbit Trilogy", "auto", 3, org.arcmediaserver.android.data.Images(primary = "art:poster:The Orbit Trilogy")),
                org.arcmediaserver.android.data.CollectionRef("c2", "Harbour Nights", "manual", 5, org.arcmediaserver.android.data.Images(primary = "art:poster:Harbour Nights")),
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
    fun movie() = shot("movie") {
        // With its cast, which should show on arrival under the buttons.
        val cast = listOf("Ada Reyes" to "Captain Reyes", "Tomas Hale" to "Hale", "Mira Okafor" to "Dr. Okafor", "Jun Park" to "Pilot", "Sol Varga" to "Engineer", "Lena Ruiz" to "Navigator")
            .mapIndexed { i, (name, role) -> org.arcmediaserver.android.data.Credit(personId = i.toLong(), name = name, role = "actor", character = role) }
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
    fun show() = shot("show-episodes") {
        DetailsScreen(
            Details(Fixtures.show, Fixtures.seasons, next = Fixtures.seasons[0].episodes[2], initialSeason = 0),
            onPlay = { _: Item, _: Long -> },
            onOpen = {},
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
                bitmap = Fixtures.art("art:backdrop:Hollow Creek").asImageBitmap(),
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
                bitmap = Fixtures.art("art:backdrop:The Ferry").asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
            PlayerControls(
                state = PlayerUiState(item = Fixtures.seasons[0].episodes[2], plan = Fixtures.plan, loading = false, playing = true, positionMs = 75_000, durationMs = Fixtures.plan.durationMs),
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
                                collections = listOf(org.arcmediaserver.android.data.CollectionRef("c1", "The Orbit Collection", count = 3)),
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
                items(Fixtures.profiles.size) { i -> org.arcmediaserver.android.ui.components.PersonCard(Fixtures.profiles[i].name, null, "Captain Reyes") {} }
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
        playerOver("art:backdrop:Hollow Creek") {
            PlayerControls(
                state = PlayerUiState(
                    item = Fixtures.seasons[0].episodes[2], plan = Fixtures.plan, loading = false, playing = true,
                    positionMs = 21 * 60_000L, durationMs = Fixtures.plan.durationMs, bufferedMs = 26 * 60_000L, trickplay = Fixtures.trickplay,
                ),
                visible = true,
                scrubMs = 33 * 60_000L + 20_000,
                onPlayPause = {}, onBack10 = {}, onForward10 = {}, onScrub = {}, onSkip = {}, onNext = {}, onMenu = {}, onClose = {},
            )
        }
    }
}
