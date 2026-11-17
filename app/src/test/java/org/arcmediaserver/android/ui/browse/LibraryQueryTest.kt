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
    fun `the video, audio and watch status filters become the web's parameters`() {
        val q = LibraryQuery(watchState = "unstarted", dynamicRange = "dolby_vision", videoCodec = "hevc", audioCodec = "truehd", audioChannels = 8)
        val p = q.params(movies, 60)
        assertEquals("unstarted", p["watch_state"])
        assertEquals("dolby_vision", p["dynamic_range"])
        assertEquals("hevc", p["video_codec"])
        assertEquals("truehd", p["audio_codec"])
        assertEquals("8", p["audio_channels"])
        assertEquals(5, q.filterCount)
        // None of them is sent when not chosen.
        assertEquals(null, LibraryQuery().params(movies, 60)["watch_state"])
    }

    @Test
    fun `Unwatched only and a watch status exclude each other, as Arc requires`() {
        val unwatched = LibraryQuery(unwatched = true)
        val watched = unwatched.withWatchState("played")
        assertEquals(false, watched.unwatched)
        assertEquals("played", watched.watchState)
        val back = watched.withUnwatched(true)
        assertEquals(true, back.unwatched)
        assertEquals(null, back.watchState)
        // Turning Unwatched off leaves a watch status alone (there isn't one to clear).
        assertEquals("unstarted", LibraryQuery(watchState = "unstarted").withUnwatched(false).watchState)
    }

    @Test
    fun `Clear Filters keeps the sort`() {
        val q = LibraryQuery(sort = "added", reverse = true, genre = "Drama", dynamicRange = "hdr10", audioChannels = 6)
        assertEquals(LibraryQuery(sort = "added", reverse = true), q.cleared())
        assertEquals(0, q.cleared().filterCount)
    }

    @Test
    fun `a video or audio filter shows when the library has a choice, or one is chosen`() {
        val one = listOf(org.arcmediaserver.android.data.Facet("hevc", 12))
        val two = one + org.arcmediaserver.android.data.Facet("h264", 30)
        assertEquals(false, offered(one, null))
        assertEquals(true, offered(two, null))
        assertEquals(true, offered(one, "hevc"))
        assertEquals(false, offered(emptyList(), null))
    }

    @Test
    fun `channel counts list the most first`() {
        val facets = listOf("2", "8", "6", "x").map { org.arcmediaserver.android.data.Facet(it, 1) }
        assertEquals(listOf(8, 6, 2), channelChoices(facets))
    }

    @Test
    fun `letters start where the titles before them end, in either direction`() {
        val letters = listOf(LetterCount("1", 2), LetterCount("a", 3), LetterCount("b", 1), LetterCount("z", 4))
        assertEquals(linkedMapOf("#" to 0, "A" to 2, "B" to 5, "Z" to 6), letterOffsets(letters, reverse = false))
        assertEquals(linkedMapOf("Z" to 0, "B" to 4, "A" to 5, "#" to 8), letterOffsets(letters, reverse = true))
    }
}
