package org.arcmediaserver.android.ui.browse

import org.arcmediaserver.android.data.LetterCount
import org.arcmediaserver.android.data.Library
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryQueryTest {
    private val movies = Library(1, "Movies", "movies")

    @Test
    fun `sort, direction and filters become the web's items parameters`() {
        val q = LibraryQuery(sort = "added", reverse = true, unwatched = true, genre = "Drama", decade = 1990, resolution = "4k")
        assertEquals(
            mapOf(
                "library_id" to "1", "kind" to "movie", "sort" to "added", "limit" to "60", "offset" to "120",
                "reverse" to "1", "unwatched" to "1", "genre" to "Drama", "decade" to "1990", "resolution" to "4k",
            ),
            q.params(movies, 60, offset = 120),
        )
        assertEquals(4, q.filterCount)
        // Title sort asks for the letter counts with its first page, for the A–Z bar; shuffle has no direction.
        assertEquals("1", LibraryQuery().params(movies, 60)["letters"])
        assertEquals(null, LibraryQuery(sort = "shuffle", reverse = true).params(movies, 60)["reverse"])
    }

    @Test
    fun `letters start where the titles before them end, in either direction`() {
        val letters = listOf(LetterCount("1", 2), LetterCount("a", 3), LetterCount("b", 1), LetterCount("z", 4))
        assertEquals(linkedMapOf("#" to 0, "A" to 2, "B" to 5, "Z" to 6), letterOffsets(letters, reverse = false))
        assertEquals(linkedMapOf("Z" to 0, "B" to 4, "A" to 5, "#" to 8), letterOffsets(letters, reverse = true))
    }
}
