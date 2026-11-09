package org.arcmediaserver.android.playback

import org.arcmediaserver.android.data.Chapter
import org.arcmediaserver.android.playback.PlayerOsd.Segment
import org.arcmediaserver.android.playback.PlayerOsd.Tab
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerOsdTest {
    @Test
    fun `no chapters or one chapter is one whole line`() {
        assertEquals(listOf(Segment(0f, 1f)), PlayerOsd.segments(emptyList(), 60_000))
        assertEquals(listOf(Segment(0f, 1f)), PlayerOsd.segments(listOf(Chapter(0, 60_000)), 60_000))
        assertEquals(listOf(Segment(0f, 1f)), PlayerOsd.segments(listOf(Chapter(0), Chapter(30_000)), 0))
    }

    @Test
    fun `chapters split the line at their starts`() {
        val s = PlayerOsd.segments(listOf(Chapter(0, 25_000), Chapter(25_000, 75_000), Chapter(75_000, 100_000)), 100_000)
        assertEquals(listOf(Segment(0f, 0.25f), Segment(0.25f, 0.75f), Segment(0.75f, 1f)), s)
    }

    @Test
    fun `a first chapter after the start still begins the line at zero, and stray chapters are ignored`() {
        val s = PlayerOsd.segments(listOf(Chapter(50_000), Chapter(10_000), Chapter(50_000), Chapter(200_000), Chapter(-5)), 100_000)
        assertEquals(listOf(Segment(0f, 0.1f), Segment(0.1f, 0.5f), Segment(0.5f, 1f)), s)
    }

    @Test
    fun `fill and fraction stay inside their bounds`() {
        assertEquals(0.5f, PlayerOsd.fraction(50, 100))
        assertEquals(1f, PlayerOsd.fraction(500, 100))
        assertEquals(0f, PlayerOsd.fraction(50, 0))
        val seg = Segment(0.25f, 0.75f)
        assertEquals(0f, PlayerOsd.filled(seg, 0.1f))
        assertEquals(0.5f, PlayerOsd.filled(seg, 0.5f))
        assertEquals(1f, PlayerOsd.filled(seg, 0.9f))
    }

    @Test
    fun `tabs follow what the title has`() {
        assertEquals(listOf(Tab.Info), PlayerOsd.tabs(chapters = 1, cast = 0, episodes = 0))
        assertEquals(listOf(Tab.Info, Tab.Chapters, Tab.Cast, Tab.Episodes), PlayerOsd.tabs(chapters = 4, cast = 3, episodes = 10))
        assertEquals(listOf(Tab.Info, Tab.Cast), PlayerOsd.tabs(chapters = 0, cast = 2, episodes = 0))
    }

    @Test
    fun `speed steps on and wraps`() {
        val speeds = listOf(0.5f, 1f, 2f)
        assertEquals(2f, PlayerOsd.nextSpeed(speeds, 1f))
        assertEquals(0.5f, PlayerOsd.nextSpeed(speeds, 2f))
        assertEquals(1f, PlayerOsd.nextSpeed(speeds, 3f))
    }
}
