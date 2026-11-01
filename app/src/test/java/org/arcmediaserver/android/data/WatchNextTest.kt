package org.arcmediaserver.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Google TV's Continue watching row, worked out from Arc's Continue Watching. */
class WatchNextTest {
    private val movie = Item(
        id = "m1", kind = "movie", title = "The Long Orbit", year = 2019, runtimeMin = 120,
        userData = UserData(positionMs = 600_000), images = Images(primary = "/api/items/m1/images/primary?v=a", backdrop = "/api/items/m1/images/backdrop?v=b"),
    )
    private val episode = Item(
        id = "e2", kind = "episode", title = "The Well", seriesTitle = "Hollow Creek", seasonNumber = 1, episodeNumber = 2, runtimeMin = 45,
        images = Images(primary = "/api/items/e2/images/primary?v=c", seriesPrimary = "/api/items/s1/images/primary?v=d"),
    )
    private val show = Item(id = "s1", kind = "series", title = "Hollow Creek")

    @Test
    fun `entries keep Arc's order, part-way titles continue and episodes come next`() {
        val entries = watchNextEntries(listOf(movie, show, episode, movie), now = 10_000)
        assertEquals(listOf("m1", "e2"), entries.map { it.itemId }) // no shows, no repeats
        val (m, e) = entries
        assertTrue(m.partWay)
        assertEquals(600_000, m.positionMs)
        assertEquals(7_200_000, m.durationMs)
        assertEquals("/api/items/m1/images/backdrop?v=b", m.thumb)
        assertFalse(e.partWay)
        assertEquals("Hollow Creek", e.title)
        assertEquals("The Well", e.episodeTitle)
        assertEquals(2, e.episode)
        assertTrue(m.engagedAt > e.engagedAt)
    }

    @Test
    fun `at most twenty titles go in the row`() {
        val many = (1..30).map { movie.copy(id = "m$it") }
        assertEquals(WATCH_NEXT_MAX, watchNextEntries(many, 0).size)
    }

    @Test
    fun `the row is brought up to date, new titles added and finished ones taken away`() {
        val entries = watchNextEntries(listOf(movie, episode), 0)
        val plan = planWatchNext(entries, listOf(WatchNextPublished(7, "m1", 1000, true), WatchNextPublished(8, "gone", 0, true)))
        assertEquals(listOf(7L), plan.update.map { it.first })
        assertEquals(listOf("e2"), plan.add.map { it.itemId })
        assertEquals(listOf(8L), plan.remove)
    }

    @Test
    fun `a title taken out of the row stays out until more of it is watched`() {
        val entries = watchNextEntries(listOf(movie), 0)
        val untouched = planWatchNext(entries, listOf(WatchNextPublished(7, "m1", 600_000, browsable = false)))
        assertTrue(untouched.add.isEmpty() && untouched.update.isEmpty() && untouched.remove.isEmpty())
        val watchedMore = planWatchNext(entries, listOf(WatchNextPublished(7, "m1", 300_000, browsable = false)))
        assertEquals(listOf(7L), watchedMore.remove)
        assertEquals(listOf("m1"), watchedMore.add.map { it.itemId })
    }

    @Test
    fun `signed out, everything is taken away, and doubles are cleared`() {
        val plan = planWatchNext(emptyList(), listOf(WatchNextPublished(1, "m1", 0, true), WatchNextPublished(2, "m1", 0, true)))
        assertEquals(listOf(1L, 2L), plan.remove)
        val doubled = planWatchNext(watchNextEntries(listOf(movie), 0), listOf(WatchNextPublished(1, "m1", 0, true), WatchNextPublished(2, "m1", 0, true)))
        assertEquals(listOf(1L), doubled.update.map { it.first })
        assertEquals(listOf(2L), doubled.remove)
        assertNull(doubled.add.firstOrNull())
    }
}
