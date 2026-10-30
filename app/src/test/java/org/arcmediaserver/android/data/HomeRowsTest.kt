package org.arcmediaserver.android.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeRowsTest {
    private val movies = Library(1, "Movies", "movies")
    private val tv = Library(2, "TV", "shows")
    private val videos = Library(3, "Home Videos", "videos")
    private val libs = listOf(movies, tv, videos)

    private fun ids(rows: List<HomeRowSpec>) = rows.map { it.id }

    @Test
    fun `the default home is Continue Watching, Watchlist and each library's newest`() {
        val rows = resolveHomeRows(Prefs(), libs)
        assertEquals(listOf("continue", "watchlist", "recent:1", "recent:2", "recent:3"), ids(rows))
        // Up Next is part of Continue Watching unless the profile split it out, as in Plex.
        assertEquals(RowSource.Continue(withUpNext = true), rows[0].source)
        assertEquals(CardStyle.Thumb, rows[0].style)
        assertEquals(CardStyle.Poster, rows[2].style)
        assertEquals(CardStyle.Thumb, rows[4].style)
    }

    @Test
    fun `a split Up Next is its own row`() {
        val rows = resolveHomeRows(Prefs(splitUpNext = true), libs)
        assertEquals(listOf("continue", "next-up", "watchlist", "recent:1", "recent:2", "recent:3"), ids(rows))
        assertEquals(RowSource.Continue(withUpNext = false), rows[0].source)
    }

    @Test
    fun `the saved order wins, hidden rows go, unplaced built-ins go where they'd be`() {
        val prefs = Prefs(
            home = listOf(
                HomeRowPref("recent:2"),
                HomeRowPref("continue"),
                HomeRowPref("watchlist", hidden = true),
                HomeRowPref("collection:9", title = "Bond"),
                HomeRowPref("recent:99"),
            ),
        )
        // recent:1 comes after watchlist by default (placed, though hidden); recent:3 after recent:2.
        assertEquals(listOf("recent:2", "recent:3", "continue", "recent:1"), ids(resolveHomeRows(prefs, libs)))
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
        val rows = resolveHomeRows(prefs, libs)
        // Built-ins the profile hasn't placed go where they'd be by default: Continue Watching first.
        assertEquals(listOf("continue", "watchlist", "recent:1", "recent:2", "recent:3", "custom:a"), ids(rows))
        val row = rows.first { it.id == "custom:a" }
        val source = row.source as RowSource.Custom
        assertEquals("Scary", source.title)
        assertEquals(CardStyle.Thumb, row.style)
        assertEquals(
            mapOf("library_id" to "1", "genre" to "Horror", "unwatched" to "1", "sort" to "rating", "kind" to "movie", "limit" to "24"),
            customRowQuery(source.filter, source.library),
        )
    }

    @Test
    fun `a custom row for a library that's gone is left out`() {
        val filter = buildJsonObject { put("library_id", JsonPrimitive(42)) }
        val prefs = Prefs(home = listOf(HomeRowPref("custom:a", title = "Gone", filter = filter)))
        assertEquals(listOf("continue", "watchlist", "recent:1", "recent:2", "recent:3"), ids(resolveHomeRows(prefs, libs)))
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
        assertEquals("/season1", card.poster)
        assertEquals(1, card.seasonNumber)
        assertEquals(3, card.newEpisodes)
    }

    private fun row(id: String, vararg extra: Pair<String, Any>) = buildJsonObject {
        put("id", id)
        for ((k, v) in extra) when (v) {
            is Boolean -> put(k, v)
            is String -> put(k, v)
            is JsonObject -> put(k, v)
        }
    }
    private fun layoutIds(rows: List<JsonObject>?) = rows!!.map { it["id"]!!.jsonPrimitive.content }

    @Test
    fun `moving a row on a fresh profile saves the whole default layout`() {
        // Home shows continue, watchlist and each library's newest; seasonal and Up Next keep their places.
        val shown = listOf("continue", "watchlist", "recent:1", "recent:2", "recent:3")
        val moved = HomeLayout.move(emptyList(), libs, shown, "recent:2", -1)
        assertEquals(listOf("continue", "next-up", "watchlist", "seasonal", "recent:2", "recent:1", "recent:3"), layoutIds(moved))
    }

    @Test
    fun `moving skips rows Home doesn't show and keeps every saved field`() {
        val filter = buildJsonObject { put("library_id", 1); put("genre", "Horror") }
        val saved = listOf(
            row("continue"),
            row("watchlist", "hidden" to true),
            row("collection:9", "title" to "Bond"),
            row("custom:a", "title" to "Scary", "filter" to filter, "posters" to "season"),
            row("recent:1"), row("recent:2"), row("recent:3"),
        )
        val shown = listOf("continue", "custom:a", "recent:1", "recent:2", "recent:3")
        val moved = HomeLayout.move(saved, libs, shown, "custom:a", -1)!!
        assertEquals(listOf("custom:a", "continue", "watchlist", "collection:9", "recent:1", "recent:2", "recent:3", "next-up", "seasonal").sorted(), layoutIds(moved).sorted())
        // Unplaced built-ins (Up Next, seasonal) go where they are by default.
        assertEquals(listOf("custom:a", "continue", "next-up", "watchlist"), layoutIds(moved).take(4))
        assertEquals(saved[3], moved.first())
        // Past either end there's nowhere to go.
        assertNull(HomeLayout.move(saved, libs, shown, "continue", -1))
        assertNull(HomeLayout.move(saved, libs, shown, "recent:3", 1))
    }

    @Test
    fun `a row's look is saved only when it isn't the usual one`() {
        val rows = resolveHomeRows(Prefs(), libs)
        val recent = rows.first { it.id == "recent:1" }
        val thumbs = HomeLayout.style(emptyList(), libs, recent, CardStyle.Thumb).first { it["id"]!!.jsonPrimitive.content == "recent:1" }
        assertEquals("thumb", thumbs["style"]!!.jsonPrimitive.content)
        val back = HomeLayout.style(listOf(row("recent:1", "style" to "thumb", "shows_only" to true)), libs, recent, CardStyle.Poster).first { it["id"]!!.jsonPrimitive.content == "recent:1" }
        assertNull(back["style"])
        assertEquals(true, back["shows_only"]!!.jsonPrimitive.content.toBoolean())
    }
}
