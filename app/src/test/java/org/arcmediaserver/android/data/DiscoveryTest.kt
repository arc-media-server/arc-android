package org.arcmediaserver.android.data

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Finding Arc on the home network: which addresses are tried, and what counts as Arc. */
class DiscoveryTest {
    private fun ip(vararg parts: Int) = ByteArray(4) { parts[it].toByte() }

    @Test
    fun `a home slash 24 is tried in full, except this TV and the network's own addresses`() {
        val hosts = Discovery.candidates(ip(192, 168, 1, 20), 24)
        assertEquals(253, hosts.size)
        assertEquals("192.168.1.1", hosts.first())
        assertEquals("192.168.1.254", hosts.last())
        assertFalse("192.168.1.20" in hosts)
    }

    @Test
    fun `a bigger network is searched only around this TV, a smaller one in full`() {
        val wide = Discovery.candidates(ip(10, 0, 5, 7), 16)
        assertEquals(253, wide.size)
        assertTrue(wide.all { it.startsWith("10.0.5.") })
        assertEquals(listOf("192.168.1.17", "192.168.1.18", "192.168.1.19", "192.168.1.21", "192.168.1.22"), Discovery.candidates(ip(192, 168, 1, 20), 29))
        assertTrue(Discovery.candidates(ip(192, 168, 1, 20), 31).isEmpty())
    }

    @Test
    fun `only private networks are searched`() {
        assertTrue(Discovery.isPrivate(ip(192, 168, 0, 2)))
        assertTrue(Discovery.isPrivate(ip(172, 20, 0, 2)))
        assertTrue(Discovery.isPrivate(ip(10, 1, 2, 3)))
        assertFalse(Discovery.isPrivate(ip(172, 32, 0, 2)))
        assertFalse(Discovery.isPrivate(ip(8, 8, 8, 8)))
    }

    private val server = MockWebServer()

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.close()

    @Test
    fun `an Arc that answers is found once, with the address to connect to`() = runTest {
        val arc = """{"product":"arc","id":"s1","name":"Living room","version":"0.9.0","api_version":1}"""
        server.enqueue(MockResponse.Builder().code(200).body(arc).build())
        server.enqueue(MockResponse.Builder().code(200).body(arc).build())
        val seen = mutableListOf<FoundServer>()
        // The same server by two names: listed once.
        val found = Discovery.scan(listOf("127.0.0.1", "localhost"), OkHttpClient(), port = server.port) { seen += it }
        assertEquals("/api/v1/discover", server.takeRequest().url.encodedPath)
        assertEquals(1, found.size)
        assertEquals("Living room", found.single().name)
        assertTrue(found.single().address.endsWith(":${server.port}"))
        assertEquals(found, seen)
    }

    @Test
    fun `other servers on the port, and silence, aren't Arc`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("""{"product":"other","id":"x","api_version":1}""").build())
        assertTrue(Discovery.scan(listOf("127.0.0.1"), OkHttpClient(), port = server.port).isEmpty())
        server.enqueue(MockResponse.Builder().code(404).body("""{"error":"no such endpoint"}""").build())
        assertTrue(Discovery.scan(listOf("127.0.0.1"), OkHttpClient(), port = server.port).isEmpty())
        // Nothing listening there at all.
        assertTrue(Discovery.scan(listOf("127.0.0.1"), OkHttpClient(), port = 1).isEmpty())
    }
}
