package org.arcmediaserver.android.playback

import org.arcmediaserver.android.data.Item
import org.arcmediaserver.android.data.MediaFile
import org.arcmediaserver.android.data.MediaStream
import org.arcmediaserver.android.data.PlaybackPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameRateTest {
    private val film = 24000f / 1001f // 23.976

    @Test
    fun `rates that show the frames evenly`() {
        assertTrue(FrameRate.fits(film, 23.976025f))
        assertTrue(FrameRate.fits(film, 119.88f)) // five refreshes a frame
        assertTrue(FrameRate.fits(25f, 50f))
        assertTrue(FrameRate.fits(29.97f, 59.94f))
        assertTrue(FrameRate.fits(24f, 24f))
    }

    @Test
    fun `rates that judder`() {
        assertFalse(FrameRate.fits(film, 59.94f)) // 3:2 pulldown
        assertFalse(FrameRate.fits(film, 60f))
        assertFalse(FrameRate.fits(film, 24f)) // drops a frame every 42 seconds
        assertFalse(FrameRate.fits(24f, 23.976f))
        assertFalse(FrameRate.fits(25f, 60f))
        assertFalse(FrameRate.fits(0f, 60f))
        assertFalse(FrameRate.fits(24f, 0f))
    }

    private val uhd60 = DisplayMode(1, 3840, 2160, 60f)
    private val modes = listOf(
        uhd60,
        DisplayMode(2, 3840, 2160, 59.94f),
        DisplayMode(3, 3840, 2160, 50f),
        DisplayMode(4, 3840, 2160, 23.976f),
        DisplayMode(5, 3840, 2160, 24f),
        DisplayMode(6, 1920, 1080, 25f),
        DisplayMode(7, 1920, 1080, 23.976f),
    )

    @Test
    fun `a film goes to 23,976 Hz at the same resolution`() {
        assertEquals(4, FrameRate.modeFor(film, uhd60, modes)?.id)
        assertEquals(5, FrameRate.modeFor(24f, uhd60, modes)?.id)
    }

    @Test
    fun `25p goes to 50 Hz, the nearest rate that fits, not a 1080p 25 Hz mode`() {
        assertEquals(3, FrameRate.modeFor(25f, uhd60, modes)?.id)
    }

    @Test
    fun `nothing changes when the current mode already fits`() {
        assertNull(FrameRate.modeFor(30f, uhd60, modes))
        assertNull(FrameRate.modeFor(60f, uhd60, modes))
    }

    @Test
    fun `nothing changes when no mode at this resolution fits`() {
        val only1080 = DisplayMode(9, 1920, 1080, 60f)
        assertNull(FrameRate.modeFor(film, only1080, listOf(only1080, DisplayMode(10, 3840, 2160, 23.976f))))
    }

    @Test
    fun `the playing file's frame rate comes from Arc's probe`() {
        val file = MediaFile(id = 7, streams = listOf(MediaStream(0, "video", "hevc", frameRate = 23.976), MediaStream(1, "audio", "eac3")))
        val other = MediaFile(id = 8, streams = listOf(MediaStream(0, "video", "h264", frameRate = 25.0)))
        val item = Item(id = "m1", kind = "movie", title = "Film", files = listOf(other, file))
        val plan = PlaybackPlan(method = "direct", url = "/f", fileId = 7)
        assertEquals(23.976f, FrameRate.of(item, plan), 0.0001f)
        assertEquals(0f, FrameRate.of(item, null), 0f)
        assertEquals(0f, FrameRate.of(item.copy(files = listOf(MediaFile(id = 7))), plan), 0f)
    }
}
