package org.arcmediaserver.android.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeRowsTest {
    private val movies = Library(1, "Movies", "movies")
    private val tv = Library(2, "TV", "shows")
    private val videos = Library(3, "Home Videos", "videos")
    private val libs = listOf(movies, tv, videos)

    private fun ids(rows: List<HomeRowSpec>) = rows.map { it.id }

    // Outside every seasonal pick, so the seasonal row doesn't come and go with the calendar.
    private val june = java.time.LocalDate.of(2026, 6, 1)

    @Test
    fun `the default home is Continue Watching, Watchlist and each library's newest`() {
        val rows = resolveHomeRows(Prefs(), libs, june)
        assertEquals(listOf("continue", "watchlist", "recent:1", "recent:2", "recent:3"), ids(rows))
        // Up Next is part of Continue Watching unless the profile split it out, as in Plex.
        assertEquals(RowSource.Continue(withUpNext = true), rows[0].source)
        assertEquals(CardStyle.Thumb, rows[0].style)
        assertEquals(CardStyle.Poster, rows[2].style)
        assertEquals(CardStyle.Thumb, rows[4].style)
    }

    @Test
    fun `the Watchlist row sits where the Watchlist sits among the libraries`() {
        val at = { n: Int -> ids(resolveHomeRows(Prefs(watchlistPosition = n), libs, june)) }
        assertEquals(listOf("continue", "recent:1", "watchlist", "recent:2", "recent:3"), at(1))
        assertEquals(listOf("continue", "recent:1", "recent:2", "recent:3", "watchlist"), at(3))
        assertEquals(listOf("continue", "recent:1", "recent:2", "recent:3", "watchlist"), at(9))
    }

    @Test
    fun `a split Up Next is its own row`() {
        val rows = resolveHomeRows(Prefs(splitUpNext = true), libs, june)
        assertEquals(listOf("continue", "next-up", "watchlist", "recent:1", "recent:2", "recent:3"), ids(rows))
        assertEquals(RowSource.Continue(withUpNext = false), rows[0].source)
    }

    @Test
    fun `rows follow the library order, not the saved order, and hidden rows go`() {
        val prefs = Prefs(
            home = listOf(
                HomeRowPref("recent:2"),
                HomeRowPref("continue"),
                HomeRowPref("watchlist", hidden = true),
                HomeRowPref("collection:9", title = "Bond"),
                HomeRowPref("recent:99"),
            ),
        )
        // The libraries come in the profile's order (TV, Movies, Home Videos).
        assertEquals(listOf("continue", "recent:2", "recent:1", "recent:3", "collection:9"), ids(resolveHomeRows(prefs, listOf(tv, movies, videos), june)))
    }

    @Test
    fun `custom rows keep their title and filter`() {
        val filter = buildJsonObject {
            put("library_id", 1)
            put("genre", "Horror")
            put("unwatched", true)
            put("decade", 0)
            put("sort", "rating")
        }
        val prefs = Prefs(home = listOf(HomeRowPref("custom:a", title = "Scary", filter = filter, style = "thumb")))
        val rows = resolveHomeRows(prefs, libs, june)
        // A filter row sits under its library's newest.
        assertEquals(listOf("continue", "watchlist", "recent:1", "custom:a", "recent:2", "recent:3"), ids(rows))
        val row = rows.first { it.id == "custom:a" }
        val source = row.source as RowSource.Custom
        assertEquals("Scary", source.title)
        // Only the rows carrying on watching take a saved look.
        assertEquals(CardStyle.Poster, row.style)
        assertEquals(
            mapOf("library_id" to "1", "genre" to "Horror", "unwatched" to "1", "sort" to "rating", "kind" to "movie", "limit" to "24"),
            customRowQuery(source.filter, source.library),
        )
    }

    @Test
    fun `a custom row for a library that's gone is left out`() {
        val filter = buildJsonObject { put("library_id", JsonPrimitive(42)) }
        val prefs = Prefs(home = listOf(HomeRowPref("custom:a", title = "Gone", filter = filter)))
        assertEquals(listOf("continue", "watchlist", "recent:1", "recent:2", "recent:3"), ids(resolveHomeRows(prefs, libs, june)))
    }

    @Test
    fun `cards use the show's poster and the episode's still`() {
        val ep = Item(
            id = "e1", kind = "episode", title = "Serenity", seriesTitle = "Firefly", seasonNumber = 1, episodeNumber = 1,
            images = Images(primary = "/still", seriesPrimary = "/show-poster", seriesBackdrop = "/show-bg"),
            userData = UserData(positionMs = 30 * 60_000), runtimeMin = 60,
        )
        val card = cardFor(ep)
        assertEquals("/show-poster", card.poster)
        assertEquals("/still", card.thumb)
        assertEquals(0.5f, card.progress!!, 0.001f)
        assertNull(cardFor(ep.copy(userData = UserData(positionMs = 30 * 60_000, played = true))).progress)
    }

    @Test
    fun `a season of new episodes shows the season's poster`() {
        val show = Item(id = "s1", kind = "series", title = "Firefly", images = Images(primary = "/show"))
        val card = cardFor(RecentCard("season", show, seasonNumber = 1, episodes = 3, seasonPoster = "/season1"))
        assertEquals("/season1", card.seasonPoster)
        assertEquals("/show", card.poster)
        assertEquals(1, card.seasonNumber)
        assertEquals(3, card.newEpisodes)
    }

    @Test
    fun `the same season added on two days is two cards with their own keys`() {
        // A row's cards need distinct keys; two days' batches of one season are separate cards.
        val show = Item(id = "s1", kind = "series", title = "Firefly")
        val monday = cardFor(RecentCard("season", show, seasonNumber = 2, episodes = 3, addedAt = 1_700_000_000))
        val wednesday = cardFor(RecentCard("season", show, seasonNumber = 2, episodes = 4, addedAt = 1_700_172_800))
        assertNotEquals(monday.key, wednesday.key)
    }

    @Test
    fun `a series batch keeps its new episode count and distinct added time`() {
        val show = Item(id = "s", kind = "series", title = "Show")
        val first = cardFor(RecentCard("series", show, episodes = 6, addedAt = 2000))
        val second = cardFor(RecentCard("series", show, episodes = 2, addedAt = 1000))
        assertEquals(6, first.newEpisodes)
        assertNull(first.seasonNumber)
        assertNotEquals(first.key, second.key)
    }

    @Test
    fun `Continue Watching's saved look is shown, other rows keep theirs`() {
        val prefs = Prefs(home = listOf(HomeRowPref("continue", style = "arc", posters = "season"), HomeRowPref("recent:2", style = "thumb", posters = "series")))
        val rows = resolveHomeRows(prefs, libs, june)
        assertEquals(CardStyle.Backdrop, rows.first { it.id == "continue" }.style)
        assertEquals(true, rows.first { it.id == "continue" }.seasonPosters)
        // Recently Added always shows posters, with each new season's own.
        assertEquals(CardStyle.Poster, rows.first { it.id == "recent:2" }.style)
        assertEquals(true, rows.first { it.id == "recent:2" }.seasonPosters)
    }

    @Test
    fun `the seasonal row shows in its season, collections and playlists come last`() {
        val prefs = Prefs(
            home = listOf(
                HomeRowPref("playlist:p1", title = "Road trip"),
                HomeRowPref("collection:c1", title = "Bond"),
                HomeRowPref("collection:c2", title = "Hidden", hidden = true),
            ),
        )
        val halloween = resolveHomeRows(prefs, libs, java.time.LocalDate.of(2026, 10, 30))
        assertEquals(listOf("continue", "seasonal", "watchlist", "recent:1", "recent:2", "recent:3", "playlist:p1", "collection:c1"), ids(halloween))
        assertEquals(RowSource.Seasonal(SeasonalPick.Horror), halloween[1].source)
        assertEquals(RowSource.Playlist("p1", "Road trip"), halloween[6].source)
        assertEquals(listOf("continue", "watchlist", "recent:1", "recent:2", "recent:3", "playlist:p1", "collection:c1"), ids(resolveHomeRows(prefs, libs, june)))
        // Hiding the seasonal row keeps it away in season too.
        assertEquals(false, "seasonal" in ids(resolveHomeRows(Prefs(home = listOf(HomeRowPref("seasonal", hidden = true))), libs, java.time.LocalDate.of(2026, 12, 25))))
    }
}
