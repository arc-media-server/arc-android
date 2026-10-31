package org.arcmediaserver.android.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Arc's WebVTT, as the server streams it (header, keepalive blank lines, then cues), read by the app. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TextSubtitlesTest {
    private val vtt = "WEBVTT\n\n\n\n" +
        "00:00:01.000 --> 00:00:03.000\nHello\n\n" +
        "00:00:02.500 --> 00:00:04.000\nThere\n\n" +
        "00:01:00.000 --> 00:01:02.000\nLater\n"

    private fun text(subs: TextSubtitles, ms: Long) = subs.cuesAt(ms * 1000).map { it.text.toString() }

    @Test
    fun `cues show only while they're due, overlapping ones together`() {
        val subs = TextSubtitles.parse(vtt.toByteArray())
        assertTrue(text(subs, 0).isEmpty())
        assertEquals(listOf("Hello"), text(subs, 1_500))
        assertEquals(setOf("Hello", "There"), text(subs, 2_700).toSet())
        assertEquals(listOf("There"), text(subs, 3_500))
        assertTrue(text(subs, 10_000).isEmpty())
        assertEquals(listOf("Later"), text(subs, 61_000))
        assertTrue(text(subs, 63_000).isEmpty())
    }

    @Test
    fun `an empty file has nothing to show`() {
        val subs = TextSubtitles.parse("WEBVTT\n\n".toByteArray())
        assertEquals(0, subs.size)
        assertTrue(text(subs, 1_000).isEmpty())
    }
}
