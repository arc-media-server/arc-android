package org.arcmediaserver.android.playback

import org.arcmediaserver.android.data.SubtitleTrack
import org.arcmediaserver.android.ui.player.timingSeconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleTimingTest {
    private val srt = SubtitleTrack("s:3", "eng", codec = "subrip")

    @Test
    fun `nudges go half a second at a time, no further than ten seconds, and reset to zero`() {
        assertEquals(500L, SubtitleTiming.shifted(0, SubtitleTiming.STEP_MS))
        assertEquals(-1_000L, SubtitleTiming.shifted(-500, -500))
        assertEquals(10_000L, SubtitleTiming.shifted(10_000, 500))
        assertEquals(-10_000L, SubtitleTiming.shifted(-9_800, -500))
        assertEquals(0L, SubtitleTiming.shifted(3_500, null))
    }

    @Test
    fun `a later offset shows the cues due earlier in the file`() {
        // 1.5 s later: at 61.5 s the player shows what the file has at 60 s.
        assertEquals(60_000L, SubtitleTiming.cueTimeMs(61_500, 1_500))
        assertEquals(62_000L, SubtitleTiming.cueTimeMs(61_500, -500))
    }

    @Test
    fun `timing is kept per file and track, none without subtitles`() {
        val offsets = mapOf(SubtitleTiming.key(7, srt) to 1_500L)
        assertEquals(1_500L, SubtitleTiming.offsetFor(offsets, 7, srt))
        assertEquals(0L, SubtitleTiming.offsetFor(offsets, 8, srt)) // another version of the title
        assertEquals(0L, SubtitleTiming.offsetFor(offsets, 7, srt.copy(id = "s:4")))
        assertEquals(0L, SubtitleTiming.offsetFor(offsets, 7, null))
    }

    @Test
    fun `only text subtitles the app draws can be moved`() {
        assertTrue(SubtitleTiming.adjustable(srt))
        assertFalse(SubtitleTiming.adjustable(srt.copy(codec = "ass")))
        assertFalse(SubtitleTiming.adjustable(srt.copy(codec = "ssa")))
        assertFalse(SubtitleTiming.adjustable(srt.copy(codec = "hdmv_pgs_subtitle", image = true)))
        assertFalse(SubtitleTiming.adjustable(null))
    }

    @Test
    fun `the offset reads as the web shows it`() {
        assertEquals("+1.5", timingSeconds(1_500))
        assertEquals("\u22120.5", timingSeconds(-500))
        assertEquals("\u221210.0", timingSeconds(-10_000))
        assertEquals("+10.0", timingSeconds(10_000))
        assertEquals("0.0", timingSeconds(0))
    }
}
