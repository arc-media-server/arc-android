package org.arcmediaserver.android.data

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The play queue store against a stand-in Arc: what it sends, revisions, refusals and older servers. */
class PlayQueuesTest {
    private val server = MockWebServer()
    private lateinit var queues: PlayQueues

    @Before
    fun setUp() {
        server.start()
        val api = ArcApi(server.url("/"), OkHttpClient(), token = { "t" }, log = ArcLog.Print)
        queues = PlayQueues({ api }, ArcLog.Print)
    }

    @After
    fun tearDown() = server.close()

    private fun answer(code: Int, body: String = "") = server.enqueue(MockResponse.Builder().code(code).body(body).build())

    private fun supported(yes: Boolean = true) =
        answer(200, if (yes) """{"api_version":1,"features":{"play_queues":{"supported":true,"available":true,"allowed":true}}}""" else """{"api_version":1,"features":{}}""")

    private fun entry(id: String, item: String?) =
        if (item == null) """{"id":"$id","available":false}""" else """{"id":"$id","available":true,"item_id":"$item","item":{"id":"$item","title":"$item","kind":"movie"}}"""

    private fun queue(revision: Int, current: String, vararg entries: Pair<String, String?>) =
        """{"id":"q1","revision":$revision,"current_entry_id":"$current","shuffled":false,"expires_at":0,"entries":[${entries.joinToString(",") { entry(it.first, it.second) }}]}"""

    @Test
    fun `an older Arc without queues is left alone`() = runTest {
        supported(false)
        assertNull(queues.start(listOf("m1")))
        assertNull(queues.add(listOf("m2"), next = true))
        assertEquals(1, server.requestCount) // only the capabilities, asked once
    }

    @Test
    fun `playing starts a queue, Play Next and moving on edit it with the latest revision`() = runTest {
        supported()
        answer(201, queue(1, "e1", "e1" to "m1"))
        val started = queues.start(listOf("m1"))!!
        assertEquals("e1", started.currentEntryId)
        server.takeRequest() // capabilities
        val create = server.takeRequest()
        assertEquals("POST", create.method)
        assertEquals("/api/v1/play-queues", create.url.encodedPath)
        assertEquals("""{"item_ids":["m1"]}""", create.body?.utf8())

        answer(200, queue(2, "e1", "e1" to "m1", "e2" to "m2", "e3" to "m3"))
        queues.add(listOf("m2"), next = true)
        val next = server.takeRequest()
        assertEquals("PATCH", next.method)
        assertEquals("/api/v1/play-queues/q1", next.url.encodedPath)
        assertEquals("""{"revision":1,"operation":"next","item_ids":["m2"]}""", next.body?.utf8())

        // The entry straight after is Advance; any other is Select.
        answer(200, queue(3, "e2", "e1" to "m1", "e2" to "m2", "e3" to "m3"))
        queues.moveTo("e2")
        assertEquals("""{"revision":2,"operation":"advance"}""", server.takeRequest().body?.utf8())
        answer(200, queue(4, "e1", "e1" to "m1", "e2" to "m2", "e3" to "m3"))
        queues.moveTo("e1")
        assertEquals("""{"revision":3,"operation":"select","entry_id":"e1"}""", server.takeRequest().body?.utf8())
        // Already there: nothing to send.
        queues.moveTo("e1")
        assertEquals(5, server.requestCount)
    }

    @Test
    fun `a queue changed elsewhere is read again and the edit made on it`() = runTest {
        supported()
        answer(201, queue(1, "e1", "e1" to "m1"))
        queues.start(listOf("m1"))
        answer(409, """{"error":"the queue changed"}""")
        answer(200, queue(5, "e1", "e1" to "m1", "e9" to "m9"))
        answer(200, queue(6, "e1", "e1" to "m1", "e9" to "m9", "e2" to "m2"))
        val q = queues.add(listOf("m2"), next = false)!!
        assertEquals(6L, q.revision)
        repeat(2) { server.takeRequest() }
        assertTrue(server.takeRequest().body!!.utf8().contains("\"revision\":1"))
        assertEquals("/api/v1/play-queues/current", server.takeRequest().url.encodedPath)
        assertEquals("""{"revision":5,"operation":"append","item_ids":["m2"]}""", server.takeRequest().body?.utf8())
    }

    @Test
    fun `Add to Queue with no queue starts one, and a failure keeps the queue as it was`() = runTest {
        supported()
        answer(404, """{"error":"no queue"}""")
        answer(201, queue(1, "e1", "e1" to "m1"))
        assertEquals("e1", queues.add(listOf("m1"), next = false)?.currentEntryId)
        answer(500, """{"error":"disk full"}""")
        assertNull(queues.add(listOf("m2"), next = false))
        assertEquals(1L, queues.queue.value?.revision)
    }

    @Test
    fun `the end of the queue deletes it with its revision`() = runTest {
        supported()
        answer(201, queue(3, "e1", "e1" to "m1"))
        queues.start(listOf("m1"))
        answer(204)
        queues.end()
        repeat(2) { server.takeRequest() }
        val delete = server.takeRequest()
        assertEquals("DELETE", delete.method)
        assertEquals("/api/v1/play-queues/q1?revision=3", delete.url.encodedPath + "?" + delete.url.encodedQuery)
        assertNull(queues.queue.value)
    }

    @Test
    fun `what's up next skips titles that are gone, which carry nothing but their id`() {
        val q = ArcJson.decodeFromString(PlayQueue.serializer(), queue(1, "e2", "e1" to "m1", "e2" to "m2", "e3" to null, "e4" to "m4"))
        assertEquals("e4", q.nextAvailable()?.id)
        assertEquals(listOf("e2", "e4"), q.upcoming().map { it.id })
        assertEquals(listOf("queue:e2", "queue:e4"), queueCards(q).map { it.key })
        assertEquals("e4", queueCards(q).last().queueEntryId())
        val finished = q.copy(currentEntryId = "")
        assertNull(finished.nextAvailable())
        assertTrue(finished.upcoming().isEmpty())
    }
}
