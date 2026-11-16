package org.arcmediaserver.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpoilersTest {
    private val images = Images(primary = "still", seriesBackdrop = "show")
    private fun ep(played: Boolean = false, pos: Long = 0, images: Images? = this.images) =
        Item(id = "e", kind = "episode", title = "E", userData = UserData(pos, played), images = images)

    @Test
    fun `only an episode not yet started is hidden, and only while the profile hides spoilers`() {
        assertTrue(ep().spoilerHidden(true))
        assertFalse(ep().spoilerHidden(false))
        assertFalse(ep(played = true).spoilerHidden(true))
        assertFalse(ep(pos = 60_000).spoilerHidden(true))
        assertFalse(Item(id = "m", kind = "movie", title = "M").spoilerHidden(true))
        assertFalse(Item(id = "s", kind = "series", title = "S").spoilerHidden(true))
    }

    @Test
    fun `a hidden episode shows the show's background, else its still blurred`() {
        assertEquals(EpisodeArt("still", blur = false), episodeArt(ep(), "still", hideSpoilers = false))
        assertEquals(EpisodeArt("show", blur = false), episodeArt(ep(), "still", hideSpoilers = true))
        assertEquals(EpisodeArt("still", blur = true), episodeArt(ep(images = Images(primary = "still")), "still", hideSpoilers = true))
        // Started: its own still, as always.
        assertEquals(EpisodeArt("still", blur = false), episodeArt(ep(pos = 60_000), "still", hideSpoilers = true))
        // Not its own still (no picture at all): nothing to blur.
        assertEquals(EpisodeArt(null, blur = false), episodeArt(ep(images = null), null, hideSpoilers = true))
    }
}
