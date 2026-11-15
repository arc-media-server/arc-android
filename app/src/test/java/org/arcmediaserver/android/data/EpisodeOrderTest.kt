package org.arcmediaserver.android.data

import org.arcmediaserver.android.ui.details.initialTab
import org.arcmediaserver.android.ui.details.nextEpisode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeOrderTest {
    private fun ep(id: String, season: Int, n: Int, played: Boolean = false) =
        Item(id = id, kind = "episode", title = id, seasonNumber = season, episodeNumber = n, available = true, userData = UserData(0, played))

    private val show = Item(id = "s", kind = "series", title = "S")
    private val s1 = Season(1, "Season 1", episodes = listOf(ep("a", 1, 1, played = true), ep("b", 1, 2)))
    private val s2 = Season(2, "Season 2", episodes = listOf(ep("c", 2, 1)))
    // An episode the order doesn't include: Arc numbers it season 0, episode 0.
    private val extra = ep("x", 0, 0)

    @Test
    fun `an episode outside the order has no number, a special keeps its own`() {
        assertEquals(null, extra.orderNumbers)
        assertEquals(0 to 1, ep("sp", 0, 1).orderNumbers)
        assertEquals(2 to 1, s2.episodes[0].orderNumbers)
        assertEquals(null, Item(id = "m", kind = "movie", title = "M").orderNumbers)
    }
    private val response = SeasonsResponse(show, Ordering(1, "TheTVDB Aired Order"), listOf(s1, s2), unplaced = listOf(extra))

    @Test
    fun `the player lists the season holding the episode, else the episodes outside the order`() {
        assertEquals(EpisodeList(s2.episodes), episodesAround(response, "c", seasonNumber = 2))
        val outside = episodesAround(response, "x", seasonNumber = 0)
        assertTrue(outside.unplaced)
        assertEquals(listOf(extra), outside.episodes)
        assertFalse(outside.episodes.containsAll(s1.episodes))
    }

    @Test
    fun `an episode Arc didn't list falls back to its season number`() {
        assertEquals(EpisodeList(s1.episodes), episodesAround(response, "gone", seasonNumber = 1))
        assertEquals(EpisodeList(emptyList()), episodesAround(response, "gone", seasonNumber = 9))
    }

    @Test
    fun `the page opens on the episode's season, or Not in This Order after the seasons`() {
        val seasons = listOf(s1, s2)
        assertEquals(1, initialTab(seasons, listOf(extra), s2.episodes[0]))
        assertEquals(2, initialTab(seasons, listOf(extra), extra))
        assertEquals(0, initialTab(seasons, listOf(extra), null))
    }

    @Test
    fun `Play and Up Next follow the order, never what's outside it`() {
        // Every episode in the order watched: Play doesn't jump to the episode outside it.
        val watched = listOf(Season(1, episodes = listOf(ep("a", 1, 1, played = true))))
        assertEquals("a", nextEpisode(watched)?.id)
        assertEquals("b", nextEpisode(listOf(s1, s2))?.id)
    }
}
