package org.arcmediaserver.android.data

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException

/** The API client against a stand-in server: paths, the token, answers and failures. */
class ArcApiTest {
    private val server = MockWebServer()
    private var token: String? = "secret-token"
    private lateinit var api: ArcApi

    @Before
    fun setUp() {
        server.start()
        api = ArcApi(server.url("/"), OkHttpClient(), token = { token }, log = ArcLog.Print)
    }

    @After
    fun tearDown() = server.close()

    private fun answer(code: Int, body: String) = server.enqueue(MockResponse.Builder().code(code).body(body).build())

    @Test
    fun `calls go to api v1 with the device token`() = runTest {
        answer(200, """{"name":"Arc Media Server","version":"0.9.0","api_version":1,"status":"ok","new_field":[1,2]}""")
        val health = api.health()
        assertEquals(1, health.apiVersion)
        assertEquals("0.9.0", health.version)
        val req = server.takeRequest()
        assertEquals("/api/v1/health", req.url.encodedPath)
        assertEquals("Bearer secret-token", req.headers["Authorization"])
    }

    @Test
    fun `home video folders by path, the top without one`() = runTest {
        answer(200, """{"path":"0","trail":[],"folders":[{"name":"Holidays 2024","path":"0/Holidays 2024","count":3,"cover_id":"v1","cover":"/api/items/v1/images/primary"}],"items":[]}""")
        val top = api.libraryFolders(3, "")
        assertEquals("0/Holidays 2024", top.folders.single().path)
        assertEquals(3, top.folders.single().count)
        assertEquals("/api/v1/libraries/3/folders", server.takeRequest().url.toString().substringAfter(server.url("/").toString().removeSuffix("/")))
        answer(200, """{"path":"0/Holidays 2024","trail":[{"name":"Holidays 2024","path":"0/Holidays 2024"}],"folders":[],"items":[]}""")
        assertEquals("Holidays 2024", api.libraryFolders(3, "0/Holidays 2024").trail.single().name)
        assertEquals("/api/v1/libraries/3/folders?path=0%2FHolidays%202024", server.takeRequest().url.toString().substringAfter(server.url("/").toString().removeSuffix("/")))
    }

    @Test
    fun `no token, no Authorization header`() = runTest {
        token = null
        answer(200, """{"api_version":1}""")
        api.health()
        assertNull(server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `TV sign-in code polling`() = runTest {
        answer(200, """{"code":"ABCD","poll":"p0ll","expires_in":600}""")
        val link = api.startLink("Living Room TV")
        assertEquals("ABCD", link.code)
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("""{"device":"Living Room TV"}""", req.body?.utf8())

        answer(202, """{"status":"waiting"}""")
        assertEquals(LinkPoll.Waiting, api.pollLink(link.poll))
        assertEquals("/api/v1/auth/link/p0ll", server.takeRequest().url.encodedPath)
        answer(200, """{"token":"device-token"}""")
        assertEquals(LinkPoll.Approved("device-token"), api.pollLink(link.poll))
        answer(404, """{"error":"this code has run out; ask for a new one"}""")
        assertEquals(LinkPoll.Expired, api.pollLink(link.poll))
    }

    @Test
    fun `errors carry Arc's words and what it needs`() = runTest {
        answer(401, """{"error":"wrong PIN","needs":"pin"}""")
        try {
            api.chooseProfile("p1", "1234")
            fail("expected an error")
        } catch (e: ApiException) {
            assertEquals(401, e.status)
            assertEquals("wrong PIN", e.message)
            assertEquals("pin", e.needs)
        }
        assertEquals("""{"id":"p1","pin":"1234"}""", server.takeRequest().body?.utf8())
    }

    @Test
    fun `an answer that isn't JSON is an IOException, not a crash`() = runTest {
        answer(200, "<html>not arc</html>")
        try {
            api.health()
            fail("expected an error")
        } catch (e: IOException) {
            assertTrue(e !is ApiException)
        }
    }

    @Test
    fun `playback decision sends the caps in the API's shape`() = runTest {
        answer(
            200,
            """{"method":"transcode","reasons":["DTS audio"],"url":"/api/transcode/s1/index.m3u8","hls":true,"session_id":"s1",
               "offset_ms":120000,"duration_ms":5400000,"file_id":7,"audio_stream":1,"audio_tracks":[{"index":1,"codec":"dts","channels":6,"default":true}],
               "subtitle_tracks":[{"id":"s:3","language":"eng","codec":"subrip","forced":false,"default":false,"hearing_impaired":false,"external":false,"image":false}],
               "versions":[],"chapters":[],"markers":[],"source_kbps":8000,"source_height":1080,"server_limit_kbps":0}""",
        )
        val caps = PlayerCaps(listOf("mp4", "mkv"), listOf("h264", "hevc"), listOf("aac", "ac3"), 2160)
        val plan = api.startPlayback("ep:1:tvdb:2", PlaybackRequest(caps, startMs = 120_000))
        assertEquals("transcode", plan.method)
        assertEquals(120_000, plan.offsetMs)
        assertEquals("s1", plan.sessionId)
        assertEquals(1, plan.audioTracks.single().index)
        val req = server.takeRequest()
        assertEquals("/api/v1/items/ep%3A1%3Atvdb%3A2/playback", req.url.encodedPath)
        assertEquals(
            """{"caps":{"containers":["mp4","mkv"],"video":["h264","hevc"],"audio":["aac","ac3"],"max_height":2160,"audio_passthrough":[]},"start_ms":120000}""",
            req.body?.utf8(),
        )
    }

    @Test
    fun `progress is a PUT with position, duration and state`() = runTest {
        answer(200, """{"position_ms":60000,"played":false}""")
        api.reportProgress("m1", ProgressReport(60_000, 7_200_000, "paused"))
        val req = server.takeRequest()
        assertEquals("PUT", req.method)
        assertEquals("""{"position_ms":60000,"duration_ms":7200000,"state":"paused"}""", req.body?.utf8())
    }

    @Test
    fun `an admin stopping playback is a 403 needing stopped`() = runTest {
        answer(403, """{"error":"Time for bed","needs":"stopped"}""")
        try {
            api.reportProgress("m1", ProgressReport(1, 2, "playing"))
            fail("expected an error")
        } catch (e: ApiException) {
            assertEquals("stopped", e.needs)
            assertEquals("Time for bed", e.message)
        }
    }

    @Test
    fun `addresses Arc gives are made absolute under api v1`() {
        val base = server.url("/").toString().trimEnd('/')
        assertEquals("$base/api/v1/transcode/s1/index.m3u8", api.absolute("/api/transcode/s1/index.m3u8"))
        assertEquals("$base/api/v1/items/m1/images/primary?v=abc", api.absolute("/api/items/m1/images/primary?v=abc"))
        assertEquals("$base/api/v1/health", api.absolute("/api/v1/health"))
    }

    @Test
    fun `items take their query, lists ignore unknown fields`() = runTest {
        answer(200, """{"total":1,"offset":0,"items":[{"id":"m1","kind":"movie","title":"Heat","year":1995,"sort_title":"heat","metadata_state":"matched","locked_fields":[],"added_at":1,"images":{"primary":"/api/items/m1/images/primary"},"user_data":{"position_ms":600000,"played":false},"runtime_min":170}]}""")
        val page = api.items(mapOf("library_id" to "1", "kind" to "movie", "sort" to "added", "limit" to "24"))
        val heat = page.items.single()
        assertEquals("Heat", heat.title)
        assertEquals(600_000, heat.resumeMs)
        assertEquals(170 * 60_000L, heat.durationMs)
        val req = server.takeRequest()
        assertEquals("/api/v1/items", req.url.encodedPath)
        assertEquals("1", req.url.queryParameter("library_id"))
        assertEquals("added", req.url.queryParameter("sort"))
    }

    @Test
    fun `a track choice is saved with PATCH me prefs, only that field`() = runTest {
        answer(200, """{"audio_language":"jpn","subtitle_language":"","subtitle_mode":"","auto_play":false,"home":[]}""")
        val prefs = api.updatePrefs(mapOf("audio_language" to "jpn"))
        assertEquals("jpn", prefs.audioLanguage)
        assertEquals(false, prefs.autoPlay)
        val req = server.takeRequest()
        assertEquals("PATCH", req.method)
        assertEquals("/api/v1/me/prefs", req.url.encodedPath)
        assertEquals("""{"audio_language":"jpn"}""", req.body?.utf8())
    }

    @Test
    fun `auto-play is on when Arc doesn't say`() = runTest {
        answer(200, """{"home":[]}""")
        assertEquals(true, api.prefs().autoPlay)
    }

    @Test
    fun `a show's seasons come with the episodes outside its order`() = runTest {
        answer(200, """{"series":{"id":"s","kind":"series","title":"S"},"ordering":{"id":3,"name":"TheTVDB Aired Order"},"seasons":[{"season_number":1,"name":"Season 1","episodes":[{"id":"e1","kind":"episode","title":"One"}]}],"unplaced":[{"id":"x","kind":"episode","title":"Extra","season_number":0,"episode_number":0}]}""")
        answer(200, """{"series":{"id":"s","kind":"series","title":"S"},"seasons":[],"unplaced":null}""")
        val first = api.seasons("s")
        assertEquals("TheTVDB Aired Order", first.ordering?.name)
        assertEquals(listOf("x"), first.unplaced.map { it.id })
        // The contract allows null: none outside the order.
        assertEquals(emptyList<Item>(), api.seasons("s").unplaced)
    }

    @Test
    fun `missing episodes are asked for with missing=1, and only then`() = runTest {
        answer(200, """{"series":{"id":"s","kind":"series","title":"S"},"seasons":[]}""")
        answer(200, """{"series":{"id":"s","kind":"series","title":"S"},"seasons":[{"season_number":1,"episodes":[{"id":"m","kind":"episode","title":"Gone","available":false}]}]}""")
        api.seasons("s")
        assertEquals(null, server.takeRequest().url.queryParameter("missing"))
        val withMissing = api.seasons("s", missing = true)
        assertEquals("1", server.takeRequest().url.queryParameter("missing"))
        assertEquals(false, withMissing.seasons[0].episodes[0].available)
    }

    @Test
    fun `watched and watchlist are PUTs that answer 204`() = runTest {
        answer(204, "")
        answer(204, "")
        api.setPlayed("ep:1", true)
        api.setWatchlist("m1", false)
        val played = server.takeRequest()
        assertEquals("PUT", played.method)
        assertEquals("/api/v1/items/ep:1/played", played.url.encodedPath.replace("%3A", ":"))
        assertEquals("""{"played":true}""", played.body?.utf8())
        val watchlist = server.takeRequest()
        assertEquals("/api/v1/items/m1/watchlist", watchlist.url.encodedPath)
        assertEquals("""{"on":false}""", watchlist.body?.utf8())
    }

    @Test
    fun `a season is marked with the show's id and its season number`() = runTest {
        answer(204, "")
        api.setPlayed("s1", false, seasonNumber = 2)
        val played = server.takeRequest()
        assertEquals("PUT", played.method)
        assertEquals("/api/v1/items/s1/played", played.url.encodedPath)
        assertEquals("""{"played":false,"season_number":2}""", played.body?.utf8())
    }

    @Test
    fun `a failed watchlist change throws with Arc's words`() = runTest {
        answer(500, """{"error":"database is locked"}""")
        try {
            api.setWatchlist("m1", true)
            fail("expected a failure")
        } catch (e: ApiException) {
            assertEquals(500, e.status)
        }
    }

    @Test
    fun `search sends the words and reads the groups`() = runTest {
        answer(200, """{"movies":[{"id":"m1","kind":"movie","title":"Alien"}],"shows":[],"episodes":[],"people":[{"id":3,"name":"Sigourney Weaver","count":2,"known_for":"actor"}],"collections":[],"genres":[]}""")
        val r = api.search("alien & co")
        assertEquals("Alien", r.movies.single().title)
        assertEquals("Sigourney Weaver", r.people.single().name)
        assertEquals("alien & co", server.takeRequest().url.queryParameter("q"))
    }

    @Test
    fun `timeline thumbnails, or none`() = runTest {
        answer(200, """{"info":{"interval_ms":10000,"width":320,"height":180,"columns":10,"rows":10,"count":312,"sheets":4},"sheets":["/api/items/m1/trickplay/5/0"]}""")
        answer(404, """{"error":"not made yet"}""")
        val t = api.trickplay("m1", 5)
        assertEquals(312, t?.info?.count)
        assertEquals("5", server.takeRequest().url.queryParameter("file"))
        assertNull(api.trickplay("m1", 5))
    }

    @Test
    fun `answers are read off the calling thread (Android forbids network I-O on the main thread)`() = kotlinx.coroutines.runBlocking {
        val caller = Thread.currentThread()
        var readOn: Thread? = null
        val client = OkHttpClient.Builder().addNetworkInterceptor { chain ->
            val response = chain.proceed(chain.request())
            val original = response.body
            response.newBuilder().body(object : okhttp3.ResponseBody() {
                override fun contentType() = original.contentType()
                override fun contentLength() = original.contentLength()
                override fun source(): okio.BufferedSource { readOn = Thread.currentThread(); return original.source() }
            }).build()
        }.build()
        val api = ArcApi(server.url("/"), client, token = { token }, log = ArcLog.Print)
        answer(200, """{"api_version":1}""")
        api.health()
        assertTrue("body was read on the calling thread", readOn != null && readOn != caller)
    }

    @Test
    fun `artwork palettes give colours, none for this title, or none from an older Arc`() = runTest {
        answer(200, """{"available":true,"algorithm":"arc-regional-v1","color_space":"srgb","artwork_type":"backdrop","artwork_url":"/x","artwork_version":"v","colors":{"top_left":"#163064","top_right":"#b84b2c","bottom_left":"#172955","bottom_right":"#643354"}}""")
        assertEquals("#b84b2c", api.artworkPalette("m1")?.colors?.topRight)
        assertEquals("/api/v1/items/m1/artwork/palette", server.takeRequest().url.encodedPath)
        answer(200, """{"available":false,"algorithm":"arc-regional-v1","color_space":"srgb","artwork_type":"primary","artwork_url":"","artwork_version":"","reason":"no_artwork"}""")
        assertNull(api.artworkPalette("m2"))
        answer(404, """{"error":"not found"}""")
        assertNull(api.artworkPalette("m3"))
    }
}
