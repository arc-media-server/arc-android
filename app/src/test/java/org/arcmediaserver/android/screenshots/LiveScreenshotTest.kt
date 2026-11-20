package org.arcmediaserver.android.screenshots

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.EventListener
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.crossfade
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.arcmediaserver.android.AppContainer
import org.arcmediaserver.android.ArcApplication
import org.arcmediaserver.android.data.ArcApi
import org.arcmediaserver.android.data.ArcJson
import org.arcmediaserver.android.data.ArcLog
import org.arcmediaserver.android.data.SecretBox
import org.arcmediaserver.android.data.SessionStore
import org.arcmediaserver.android.ui.ArcScreens
import org.arcmediaserver.android.ui.arcViewModel
import org.arcmediaserver.android.ui.browse.BrowseRoute
import org.arcmediaserver.android.ui.browse.BrowseViewModel
import org.arcmediaserver.android.ui.browse.LibraryTab
import org.arcmediaserver.android.ui.browse.Section
import org.arcmediaserver.android.ui.components.LoadState
import org.arcmediaserver.android.ui.details.DetailsRoute
import org.arcmediaserver.android.ui.details.DetailsViewModel
import org.arcmediaserver.android.ui.profiles.ProfilesRoute
import org.arcmediaserver.android.ui.profiles.ProfilesViewModel
import org.arcmediaserver.android.ui.search.SearchViewModel
import org.arcmediaserver.android.ui.theme.ArcColors
import org.arcmediaserver.android.ui.theme.ArcTheme
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The README pictures: the app's real screens (its routes and view models, the real [ArcApi] and
 * Coil loading the real artwork), drawn by Robolectric at a TV's 1920x1080, against the shared
 * screenshot library (arc-media-server's docs/SCREENSHOTS.md). Not an emulator or a device: the
 * pictures are what these screens draw on the JVM.
 *
 * Skipped unless that library's server is running, so ordinary test runs don't change:
 *
 *     (in arc-media-server) cd web && node tools/screenshots.mjs --serve
 *     ./gradlew recordRoborazziDebug --tests '*LiveScreenshotTest*'
 *
 * It writes app/screenshots/readme-*.png. ARC_SCREENSHOTS_URL picks another address (the shared
 * library's throwaway server only, never anyone's real Arc); it signs in with that server's
 * household password and picks Jordan, as the library's instructions say.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w960dp-h540dp-land-television-xhdpi", application = LiveScreenshotTest.App::class)
class LiveScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<App>()
    private val url: HttpUrl = (System.getenv("ARC_SCREENSHOTS_URL") ?: "http://127.0.0.1:18299").toHttpUrl()
    private val http = OkHttpClient.Builder().connectTimeout(2, TimeUnit.SECONDS).build()

    /** The app, with its sign-in kept in plain preferences (Robolectric has no Android Keystore). */
    class App : ArcApplication() {
        override fun createContainer() = AppContainer(this, SessionStore(getSharedPreferences("arc", Context.MODE_PRIVATE), Plain))

        /** As the app's own loader, plus [Loads]; no crossfade, and software bitmaps for the JVM. */
        override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { container.mediaClient })) }
            .eventListener(Loads)
            .crossfade(false)
            .allowHardware(false)
            .build()
    }

    private object Plain : SecretBox {
        override fun seal(plain: String) = plain
        override fun open(sealed: String) = sealed
    }

    /** Coil's requests: how many have started, and how many are still loading. */
    object Loads : EventListener() {
        val started = AtomicInteger()
        val active = AtomicInteger()
        val failed = AtomicInteger()
        override fun onStart(request: ImageRequest) {
            started.incrementAndGet()
            active.incrementAndGet()
        }
        override fun onSuccess(request: ImageRequest, result: SuccessResult) {
            active.decrementAndGet()
        }
        override fun onError(request: ImageRequest, result: ErrorResult) {
            failed.incrementAndGet()
            println("screenshots: an image failed (${result.throwable.javaClass.simpleName})")
            active.decrementAndGet()
        }
        override fun onCancel(request: ImageRequest) {
            active.decrementAndGet()
        }
    }

    @Before
    fun connect() {
        val up = runCatching {
            http.newCall(Request.Builder().url(url.resolve("api/v1/health")!!).build()).execute().use { it.isSuccessful }
        }.onFailure { println("screenshots: no screenshot server (${it.javaClass.simpleName})") }.getOrDefault(false)
        assumeTrue("start the shared screenshot library's server (node tools/screenshots.mjs --serve) to take these", up)
        val body = """{"password":"screenshots-only","device":"Arc for Android TV (screenshots)"}""".toRequestBody("application/json".toMediaType())
        val login = http.newCall(Request.Builder().url(url.resolve("api/v1/auth/login")!!).post(body).build()).execute().use {
            check(it.isSuccessful) { "sign-in answered ${it.code}: is this the shared screenshot library?" }
            it.body.string()
        }
        // Robolectric's native graphics must start on this thread: started first by Coil's
        // decoder thread, they abort the JVM ("Class not found: java/nio/FloatBuffer").
        android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888).copyPixelsToBuffer(java.nio.ByteBuffer.allocate(4))
        val session = app.container.session
        session.server = url
        session.token = ArcJson.decodeFromString(JsonObject.serializer(), login)["token"]!!.jsonPrimitive.content
        Loads.started.set(0)
        Loads.active.set(0)
        Loads.failed.set(0)
    }

    private fun api(): ArcApi = ArcApi(url, http, token = { app.container.session.token }, log = ArcLog.Print)

    /** Jordan, the library's admin, as the app's profile picker does. */
    private fun pickJordan() = runBlocking {
        val api = api()
        val jordan = api.authStatus().profiles.first { it.name == "Jordan" }
        api.chooseProfile(jordan.id, null)
    }

    /**
     * Draws [content], waits until [ready] says its data is in and Coil has finished every picture
     * it asked for (no time is waited for its own sake), then saves it.
     */
    private fun shot(name: String, ready: () -> Boolean, content: @Composable () -> Unit) {
        compose.setContent {
            ArcTheme { Box(Modifier.fillMaxSize().background(ArcColors.Background)) { ArcScreens(app.container) { content() } } }
        }
        // Robolectric runs the main thread's work (the view models' results) only when idled.
        compose.waitUntil(60_000) {
            compose.waitForIdle()
            ready()
        }
        // Pictures start as the screen composes them, and a loaded one can bring more (a row
        // scrolled into place): settled when nothing is loading and nothing new started since.
        var seen = -1
        compose.waitUntil(90_000) {
            compose.waitForIdle()
            val now = Loads.started.get()
            val settled = Loads.active.get() == 0 && now == seen
            seen = now
            settled
        }
        compose.waitForIdle()
        println("screenshots: $name drew ${Loads.started.get()} pictures, ${Loads.failed.get()} failed")
        compose.onRoot().captureRoboImage("screenshots/readme-$name.png")
    }

    @Test
    fun profiles() {
        var vm: ProfilesViewModel? = null
        shot("profiles", ready = { vm?.state?.value?.profiles is LoadState.Ready }) {
            vm = arcViewModel { ProfilesViewModel(it) }
            ProfilesRoute(onChosen = {}, onSignedOut = {})
        }
    }

    private fun browse(name: String, section: Section? = null, tab: LibraryTab? = null, ready: (BrowseViewModel) -> Boolean) {
        var vm: BrowseViewModel? = null
        shot(name, ready = { vm?.let(ready) == true }) {
            val model = arcViewModel { BrowseViewModel(it) }
            vm = model
            LaunchedEffect(Unit) {
                model.state.first { it.frame is LoadState.Ready }
                section?.let(model::show)
                if (tab != null) {
                    model.state.first { it.library is LoadState.Ready }
                    model.showLibraryTab(tab)
                }
            }
            val loaded by model.state.collectAsState()
            // Home is drawn once its rows are in. Drawn while they arrive, the render can end up
            // scrolled past the hero (rows landing out of order; not seen on a device).
            if (section != null || (loaded.rows.isNotEmpty() && model.homeLoaded())) BrowseRoute(onOpen = {}, onPerson = {}, onSwitchProfile = {}, onSignedOut = {})
        }
    }

    private fun BrowseViewModel.homeLoaded() = state.value.let { s -> s.frame is LoadState.Ready && s.rows.isNotEmpty() && s.rows.none { it.cards is LoadState.Loading } }

    @Test
    fun home() {
        pickJordan()
        browse("home") { it.homeLoaded() }
    }

    /** Jordan picked, then the Movies library's id. */
    private fun moviesId(): Long {
        pickJordan()
        return runBlocking { api().libraries().first { it.kind == "movies" }.id }
    }

    @Test
    fun libraryRecommended() {
        val movies = moviesId()
        browse("library-recommended", Section.Library(movies)) { vm ->
            val s = vm.state.value
            s.section == Section.Library(movies) && s.library is LoadState.Ready && s.libraryExtras.recommended !is LoadState.Loading
        }
    }

    @Test
    fun library() {
        val movies = moviesId()
        browse("library", Section.Library(movies), LibraryTab.Titles) { vm ->
            val s = vm.state.value
            s.section == Section.Library(movies) && s.library is LoadState.Ready && s.libraryTab == LibraryTab.Titles
        }
    }

    @Test
    fun settings() {
        pickJordan()
        browse("settings", Section.Settings) { it.state.value.section == Section.Settings }
    }

    @Test
    fun search() {
        pickJordan()
        var browse: BrowseViewModel? = null
        var search: SearchViewModel? = null
        shot("search", ready = { search?.state?.value?.let { it.query == "Dune" && !it.searching && it.sections.isNotEmpty() } == true && browse?.state?.value?.section == Section.Search }) {
            val model = arcViewModel { BrowseViewModel(it) }
            val find = arcViewModel { SearchViewModel(it) }
            browse = model
            search = find
            LaunchedEffect(Unit) {
                model.state.first { it.frame is LoadState.Ready }
                model.show(Section.Search)
                find.type("Dune")
            }
            BrowseRoute(onOpen = {}, onPerson = {}, onSwitchProfile = {}, onSignedOut = {})
        }
    }

    /** The featured film or show of the shared library, found by its title. */
    private fun featured(title: String, kind: String): String = runBlocking {
        val api = api()
        api.libraries().filter { it.kind == kind }.firstNotNullOf { lib ->
            api.items(mapOf("library_id" to lib.id.toString(), "limit" to "200")).items.firstOrNull { it.title == title }
        }.id
    }

    private fun details(name: String, title: String, kind: String) {
        pickJordan()
        val id = featured(title, kind)
        var vm: DetailsViewModel? = null
        shot(name, ready = { vm?.state?.value is LoadState.Ready }) {
            vm = arcViewModel { DetailsViewModel(it, id) }
            DetailsRoute(itemId = id, onPlay = { _, _ -> }, onOpen = {})
        }
    }

    @Test
    fun movie() = details("movie", "Dune: Part Two", "movies")

    @Test
    fun show() = details("show-episodes", "Breaking Bad", "shows")

    @Test
    fun signIn() {
        var vm: org.arcmediaserver.android.ui.signin.SignInViewModel? = null
        shot("sign-in", ready = { vm?.state?.value?.code != null }) {
            vm = arcViewModel { org.arcmediaserver.android.ui.signin.SignInViewModel(it) }
            org.arcmediaserver.android.ui.signin.SignInRoute(onSignedIn = {}, onChangeServer = {})
        }
    }

    /**
     * The player, as far as Robolectric can draw it: it can't play video, so the player's real
     * controls are drawn over the episode's still, with what the player's view model would load
     * (Breaking Bad's episode part-way through, its season, the cast, and Arc's answer to the
     * playback request). The timeline is the episode's running time and Arc's resume point, since
     * the library's file is a ten-second clip.
     */
    private fun player(name: String, tab: org.arcmediaserver.android.playback.PlayerOsd.Tab?) {
        pickJordan()
        val state = runBlocking {
            val api = api()
            val showId = featured("Breaking Bad", "shows")
            val seasons = api.seasons(showId)
            val resumed = seasons.seasons.flatMap { it.episodes }.first { it.userData?.positionMs ?: 0 > 0 }
            // The season list's copy of the episode (it has the numbers), the details' cast and the
            // show's title: Arc's GET /items/{id} doesn't yet give an episode its show's title or
            // its numbers, so the player shows the episode's title twice.
            val show = api.item(showId)
            val episode = resumed.copy(credits = api.item(resumed.id).credits, seriesTitle = resumed.seriesTitle ?: show.title)
            val caps = org.arcmediaserver.android.data.PlayerCaps(listOf("mp4", "mkv"), listOf("h264", "hevc"), listOf("aac", "ac3", "eac3"), 2160)
            val plan = api.startPlayback(episode.id, org.arcmediaserver.android.data.PlaybackRequest(caps, startMs = 0))
            plan.sessionId?.let { runCatching { api.stopTranscode(it) } }
            val cast = episode.credits.filter { it.role == "actor" }.ifEmpty { show.credits.filter { it.role == "actor" } }
            val list = org.arcmediaserver.android.data.episodesAround(seasons, episode.id, episode.seasonNumber)
            val duration = (episode.runtimeMin ?: 0) * 60_000L
            org.arcmediaserver.android.ui.player.PlayerUiState(
                item = episode, plan = plan, loading = false, playing = true,
                positionMs = episode.userData?.positionMs ?: 0, durationMs = duration,
                bufferedMs = minOf(duration, (episode.userData?.positionMs ?: 0) + 5 * 60_000L),
                cast = cast, episodes = list.episodes, next = list.episodes.getOrNull(list.episodes.indexOfFirst { it.id == episode.id } + 1),
            )
        }
        shot(name, ready = { true }) {
            Box(Modifier.fillMaxSize()) {
                coil3.compose.AsyncImage(
                    model = org.arcmediaserver.android.ui.components.LocalImageUrl.current(state.item!!.images!!.primary!!),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
                org.arcmediaserver.android.ui.player.PlayerControls(
                    state = state, visible = true, scrubMs = null,
                    initialTab = tab,
                    onPlayPause = {}, onBack10 = {}, onForward10 = {}, onScrub = {}, onSkip = {}, onNext = {}, onMenu = {}, onClose = {},
                )
            }
        }
    }

    @Test
    fun playerControls() = player("player", null)

    @Test
    fun playerEpisodes() = player("player-card-episodes", org.arcmediaserver.android.playback.PlayerOsd.Tab.Episodes)

    /**
     * Records the answers the other screenshot tests draw ([Fixtures]) into
     * src/test/resources/screenshot-library/: the films, Breaking Bad with its first two seasons
     * (Arc's Show Missing Episodes listing) and its specials, Dune: Part Two's details, its lead actor's
     * page and the profiles. Only when
     * ARC_SCREENSHOTS_RECORD=1, since it rewrites files in git. Files and stream addresses are
     * left out; artwork addresses are paths on the server, never its address, and no token is in
     * any answer.
     */
    @Test
    fun recordFixtures() {
        assumeTrue("set ARC_SCREENSHOTS_RECORD=1 to record the fixtures", System.getenv("ARC_SCREENSHOTS_RECORD") == "1")
        pickJordan()
        val dir = java.io.File("src/test/resources/screenshot-library").apply { mkdirs() }
        fun get(path: String): kotlinx.serialization.json.JsonElement {
            val req = Request.Builder().url(url.resolve("api/v1/$path")!!).header("Authorization", "Bearer ${app.container.session.token}").build()
            val body = http.newCall(req).execute().use { check(it.isSuccessful) { "$path answered ${it.code}" }; it.body.string() }
            return ArcJson.parseToJsonElement(body)
        }
        fun clean(e: kotlinx.serialization.json.JsonElement): kotlinx.serialization.json.JsonElement = when (e) {
            is JsonObject -> JsonObject(e.filterKeys { it !in setOf("files", "stream_url", "path") }.mapValues { clean(it.value) })
            is kotlinx.serialization.json.JsonArray -> kotlinx.serialization.json.JsonArray(e.map(::clean))
            else -> e
        }
        val pretty = kotlinx.serialization.json.Json { prettyPrint = true }
        fun save(name: String, e: kotlinx.serialization.json.JsonElement) {
            val text = pretty.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), clean(e)) + "\n"
            check(!text.contains(url.host) && !text.contains(app.container.session.token!!)) { "$name holds the server's address or token" }
            java.io.File(dir, "$name.json").writeText(text)
        }
        val movies = runBlocking { api().libraries().first { it.kind == "movies" } }
        save("movies", get("items?library_id=${movies.id}&kind=movie&sort=title&limit=200"))
        val show = featured("Breaking Bad", "shows")
        save("show", get("items/$show"))
        // Seasons 1 and 2 and the specials are enough for the tests (the library has Season 1's files).
        save("seasons", (get("series/$show/seasons?missing=1") as JsonObject).let { all ->
            JsonObject(all + ("seasons" to kotlinx.serialization.json.JsonArray((all["seasons"] as kotlinx.serialization.json.JsonArray).filter {
                (it as JsonObject)["season_number"]!!.jsonPrimitive.content in setOf("0", "1", "2")
            })))
        })
        val dune = get("items/${featured("Dune: Part Two", "movies")}")
        save("dune", dune)
        val lead = ((dune as JsonObject)["credits"] as kotlinx.serialization.json.JsonArray).first()
        save("person", get("people/${(lead as JsonObject)["person_id"]!!.jsonPrimitive.content}"))
        save("profiles", get("auth/status").let { (it as JsonObject)["profiles"]!! })
    }
}
