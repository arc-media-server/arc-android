package org.arcmediaserver.android.ui.details

import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.arcmediaserver.android.data.ArcApi
import org.arcmediaserver.android.data.ArcJson
import org.arcmediaserver.android.data.ArcLog
import org.arcmediaserver.android.data.Item
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** The rows under a title's page: its collections (without itself), then More Like This. */
class RelatedRowsTest {
    private val server = MockWebServer()

    @After
    fun tearDown() = server.close()

    @Test
    fun `collections come first without the title, then More Like This, failures and empty rows go`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = when (request.url.encodedPath) {
                    "/api/v1/collections/c1" -> """{"collection":{"id":"c1","title":"Bond","kind":"auto"},"items":[{"id":"m1","kind":"movie","title":"Dr. No"},{"id":"m2","kind":"movie","title":"Goldfinger"}]}"""
                    "/api/v1/collections/c2" -> """{"collection":{"id":"c2","title":"Just this","kind":"manual"},"items":[{"id":"m1","kind":"movie","title":"Dr. No"}]}"""
                    "/api/v1/items/m1/similar" -> """[{"id":"m9","kind":"movie","title":"Thunderball"}]"""
                    else -> return MockResponse.Builder().code(500).body("""{"error":"broken"}""").build()
                }
                return MockResponse.Builder().code(200).body(body).build()
            }
        }
        server.start()
        val api = ArcApi(server.url("/"), OkHttpClient(), token = { null }, log = ArcLog.Print)
        val movie = ArcJson.decodeFromString(
            Item.serializer(),
            """{"id":"m1","kind":"movie","title":"Dr. No","collections":[{"id":"c1","title":"Bond"},{"id":"c2","title":"Just this"},{"id":"c3","title":"Broken"}]}""",
        )
        val rows = relatedRows(api, movie)
        assertEquals(listOf("collection:c1", "similar"), rows.map { it.key })
        assertEquals(listOf("m2"), rows[0].items.map { it.id })
        assertEquals("Bond", rows[0].title)
        assertEquals(null, rows[1].title)
    }
}
