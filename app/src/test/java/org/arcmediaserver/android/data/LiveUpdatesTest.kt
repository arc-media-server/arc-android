package org.arcmediaserver.android.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Live updates: reading Arc's event stream, gathering hints, and a stream against a stand-in Arc. */
class LiveUpdatesTest {
    @Test
    fun `the stream reader returns whole events, skips heartbeats and keeps the cursor`() {
        val p = SseParser()
        val lines = listOf(
            ": heartbeat", "",
            "event: invalidate", "data: {\"topic\":\"catalog\"}", "id: 7", "",
            "event: resync", "data: {}", "",
        )
        val events = lines.mapNotNull { p.line(it) }
        assertEquals(2, events.size)
        assertEquals(SseEvent("invalidate", "{\"topic\":\"catalog\"}", "7"), events[0])
        // The id carries on until a new one comes.
        assertEquals(SseEvent("resync", "{}", "7"), events[1])
    }

    @Test
    fun `a hint's topic is read, a broken one is skipped`() {
        assertEquals("profile", hintTopic("""{"topic":"profile","library_id":3}"""))
        assertNull(hintTopic("not json"))
        assertNull(hintTopic("""{"topic":5}"""))
    }

    @Test
    fun `topics touch the screens that show them, and a resync touches everything`() {
        assertTrue(LiveTopics.touches(setOf("catalog"), LiveTopics.home))
        assertFalse(LiveTopics.touches(setOf("sessions", "activity"), LiveTopics.home))
        assertTrue(LiveTopics.touches(setOf(LiveTopics.RESYNC), LiveTopics.prefs))
        assertFalse(LiveTopics.touches(setOf("catalog"), LiveTopics.prefs))
    }

    @Test
    fun `hints are gathered, the first after a moment, then at most every five seconds`() {
        val scope = TestScope(StandardTestDispatcher())
        val flushed = mutableListOf<Set<String>>()
        val batcher = HintBatcher(scope, now = { scope.testScheduler.currentTime }) { flushed += it }
        batcher.add("catalog")
        batcher.add("profile")
        scope.advanceTimeBy(1_499)
        scope.runCurrent()
        assertEquals(0, flushed.size)
        scope.advanceTimeBy(2)
        scope.runCurrent()
        assertEquals(listOf(setOf("catalog", "profile")), flushed)
        // A scan keeps sending: the next batch waits until five seconds after the last.
        batcher.add("catalog")
        scope.advanceTimeBy(3_000)
        scope.runCurrent()
        assertEquals(1, flushed.size)
        scope.advanceTimeBy(2_100)
        scope.runCurrent()
        assertEquals(2, flushed.size)
        batcher.stop()
    }

    private val server = MockWebServer()
    private lateinit var api: ArcApi
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Before
    fun setUp() {
        server.start()
        api = ArcApi(server.url("/"), OkHttpClient(), token = { "secret-token" }, log = ArcLog.Print)
    }

    @After
    fun tearDown() {
        scope.cancel()
        server.close()
    }

    private fun stream(body: String) = MockResponse.Builder().code(200).setHeader("Content-Type", "text/event-stream").body(body).build()

    @Test
    fun `a stream's hints reach the screens, its opening resync doesn't, and it resumes from the cursor`() = runBlocking {
        server.enqueue(stream("event: resync\ndata: {}\nid: 1\n\n: hb\n\nevent: invalidate\ndata: {\"topic\":\"catalog\"}\nid: 2\n\n"))
        // Arc refuses the reopen while its streams are full.
        server.enqueue(MockResponse.Builder().code(429).setHeader("Retry-After", "60").build())
        val live = LiveUpdates({ api }, OkHttpClient(), scope, log = ArcLog.Print)
        live.start()
        val got = withTimeout(10_000) { live.changes.first() }
        assertEquals(setOf("catalog"), got)
        val first = server.takeRequest()
        assertEquals("/api/v1/events", first.url.encodedPath)
        assertNull(first.headers["Last-Event-ID"])
        // Arc closed the stream: it reopens where it left off.
        val second = server.takeRequest(5, TimeUnit.SECONDS)
        assertEquals("2", second?.headers?.get("Last-Event-ID"))
        live.stop()
    }

    @Test
    fun `a resync on a resumed stream is real`() = runBlocking {
        server.enqueue(stream("event: invalidate\ndata: {\"topic\":\"library\"}\nid: 4\n\n"))
        // Arc no longer has what came after 4: it says resync.
        server.enqueue(stream("event: resync\ndata: {}\nid: 9\n\n"))
        server.enqueue(MockResponse.Builder().code(429).setHeader("Retry-After", "60").build())
        val live = LiveUpdates({ api }, OkHttpClient(), scope, log = ArcLog.Print)
        val seen = java.util.Collections.synchronizedList(mutableListOf<Set<String>>())
        val collector = scope.launch { live.changes.collect { seen += it } }
        live.start()
        withTimeout(15_000) { while (seen.none { LiveTopics.RESYNC in it }) kotlinx.coroutines.delay(100) }
        assertTrue(seen.first().contains("library"))
        collector.cancel()
        live.stop()
    }
}
