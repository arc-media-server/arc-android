package org.arcmediaserver.android.data

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The pre-roll list against a stand-in Arc. */
class PrerollTest {
    private val server = MockWebServer()
    private lateinit var api: ArcApi

    @Before
    fun setUp() {
        server.start()
        api = ArcApi(server.url("/"), OkHttpClient(), token = { "t" }, log = ArcLog.Print)
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun `a film's pre-rolls are listed in order with where to play them`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("""[{"title":"Cinema","url":"/api/preroll/0"},{"title":"Arc","url":"/api/preroll/1","extra":1}]""").build())
        val clips = api.preroll("m 1")
        assertEquals("/api/v1/items/m%201/preroll", server.takeRequest().url.encodedPath)
        assertEquals(listOf("/api/preroll/0", "/api/preroll/1"), clips.map { it.url })
        assertEquals("Cinema", clips.first().title)
    }

    @Test
    fun `none set, or not a film, is an empty list`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("[]").build())
        assertTrue(api.preroll("e1").isEmpty())
    }
}
