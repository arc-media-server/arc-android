package org.arcmediaserver.android.playback

import org.arcmediaserver.android.data.AudioTrack
import org.arcmediaserver.android.data.Item
import org.arcmediaserver.android.data.SubtitleTrack
import org.arcmediaserver.android.data.TrickplayInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Up Next, remembered track choices and timeline thumbnails, as the web player has them. */
class PlayerRulesTest {
    private val next = Item(id = "ep2", kind = "episode", title = "Next")

    @Test
    fun `up next counts down at the end when there's a next episode`() {
        assertEquals(UpNext.AtEnd.Countdown, UpNext.atEnd(next, autoPlay = true))
        assertEquals(UpNext.AtEnd.Close, UpNext.atEnd(null, autoPlay = true))
    }

    @Test
    fun `with auto-play off nothing is offered and the player closes at the end`() {
        assertNull(UpNext.offered(next, autoPlay = false))
        assertEquals(UpNext.AtEnd.Close, UpNext.atEnd(next, autoPlay = false))
        assertFalse(UpNext.atCredits(next, autoPlay = false, alreadyOffered = false))
    }

    @Test
    fun `the credits start the countdown once`() {
        assertTrue(UpNext.atCredits(next, autoPlay = true, alreadyOffered = false))
        assertFalse(UpNext.atCredits(next, autoPlay = true, alreadyOffered = true))
        assertFalse(UpNext.atCredits(null, autoPlay = true, alreadyOffered = false))
    }

    @Test
    fun `picking subtitles remembers their language, and Off forgets it only in the default mode`() {
        assertEquals("fra", rememberedSubtitleLanguage(SubtitleTrack("s:1", "fra"), ""))
        assertEquals("fra", rememberedSubtitleLanguage(SubtitleTrack("s:1", "fra"), "foreign"))
        assertNull(rememberedSubtitleLanguage(SubtitleTrack("s:2", "eng", forced = true), ""))
        assertEquals("", rememberedSubtitleLanguage(null, ""))
        assertNull(rememberedSubtitleLanguage(null, "always"))
    }

    @Test
    fun `picking audio remembers a new language only`() {
        assertEquals("jpn", audioLanguageToRemember(AudioTrack(2, "jpn"), "eng"))
        assertNull(audioLanguageToRemember(AudioTrack(1, "eng"), "eng"))
        assertNull(audioLanguageToRemember(AudioTrack(3, null), "eng"))
    }

    @Test
    fun `the remembered subtitle language starts subtitles as the profile's mode says`() {
        val tracks = listOf(SubtitleTrack("s:1", "eng"), SubtitleTrack("s:2", "eng", forced = true), SubtitleTrack("s:3", "fra"))
        assertEquals("s:3", chooseSubtitle(tracks, "fra", "eng", "")?.id)
        assertEquals("s:2", chooseSubtitle(tracks, "fra", "eng", "manual")?.id) // forced ones for the audio only
        assertEquals("s:3", chooseSubtitle(tracks, "fra", "eng", "foreign")?.id)
        assertEquals("s:2", chooseSubtitle(tracks, "eng", "eng", "foreign")?.id) // audio already in English
    }

    @Test
    fun `timeline thumbnails find the tile for a moment`() {
        val info = TrickplayInfo(intervalMs = 10_000, width = 320, height = 180, columns = 10, rows = 10, count = 150, sheets = 2)
        val sheets = listOf("/s/0", "/s/1")
        assertEquals(TrickplayFrame("/s/0", 0, 0, 320, 180), trickplayFrame(info, sheets, 0))
        assertEquals(TrickplayFrame("/s/0", 3 * 320, 1 * 180, 320, 180), trickplayFrame(info, sheets, 139_999)) // 13th
        assertEquals(TrickplayFrame("/s/1", 2 * 320, 0, 320, 180), trickplayFrame(info, sheets, 1_020_000)) // 102nd, second sheet
        assertEquals(TrickplayFrame("/s/1", 9 * 320, 4 * 180, 320, 180), trickplayFrame(info, sheets, 99_000_000)) // past the end: the last
        assertNull(trickplayFrame(info, listOf("/s/0"), 1_020_000)) // its sheet isn't listed
        assertNull(trickplayFrame(info.copy(count = 0), sheets, 0))
    }

    @Test
    fun `language codes read as names, whichever form the file uses`() {
        java.util.Locale.setDefault(java.util.Locale.UK)
        assertEquals("French", languageName("fra"))
        assertEquals("French", languageName("fre"))
        assertEquals("English", languageName("en-GB"))
        assertNull(languageName("und"))
        assertEquals("xyz", languageName("xyz"))
    }
}
