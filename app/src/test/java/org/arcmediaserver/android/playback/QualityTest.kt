package org.arcmediaserver.android.playback

import org.arcmediaserver.android.data.MediaFile
import org.arcmediaserver.android.data.MediaStream
import org.arcmediaserver.android.playback.Quality.Capsule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QualityTest {
    private fun file(vararg s: MediaStream) = MediaFile(id = 1, streams = s.toList())

    @Test
    fun `4K Dolby Vision with an HDR10 base and TrueHD Atmos`() {
        val f = file(
            MediaStream(0, "video", "hevc", width = 3840, height = 1600, dolbyVision = true, colorTransfer = "smpte2084"),
            MediaStream(1, "audio", "truehd", "Dolby TrueHD + Dolby Atmos", channels = 8),
        )
        assertEquals(
            listOf(Capsule(listOf("4K"), filled = true), Capsule(listOf("DV", "HDR10")), Capsule(listOf("TrueHD", "7.1")), Capsule(listOf("Atmos"))),
            Quality.capsules(f),
        )
        assertEquals("4K · Dolby Vision · Dolby Atmos", Quality.summary(f))
    }

    @Test
    fun `an SDR 1080p file with DD+ 5_1`() {
        val f = file(MediaStream(0, "video", "h264", width = 1920, height = 800), MediaStream(1, "audio", "eac3", channels = 6))
        assertEquals(listOf(Capsule(listOf("1080p"), filled = true), Capsule(listOf("SDR")), Capsule(listOf("DD+", "5.1"))), Quality.capsules(f))
        assertEquals("1080p · DD+ 5.1", Quality.summary(f))
    }

    @Test
    fun `resolutions and no file`() {
        assertEquals("720p", Quality.resolution(1280, 720))
        assertEquals("SD", Quality.resolution(720, 480))
        assertNull(Quality.resolution(0, 0))
        assertEquals(emptyList<Capsule>(), Quality.capsules(null))
        assertNull(Quality.summary(null))
    }
}
