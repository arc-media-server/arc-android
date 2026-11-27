package org.arcmediaserver.android.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class MovieEditionTest {
    @Test
    fun `movie captions use the named edition when the server supplies it`() {
        val movie = Json.decodeFromString<Item>("""{"id":"m1","kind":"movie","title":"Film","year":2008,"edition":"IMAX"}""")
        assertEquals("2008 · IMAX", movie.yearAndEdition)
        assertEquals("IMAX", movie.copy(year = null).yearAndEdition)

        val legacy = Json.decodeFromString<Item>("""{"id":"m1","kind":"movie","title":"Film","year":2008}""")
        assertEquals("2008", legacy.yearAndEdition)
    }
}
