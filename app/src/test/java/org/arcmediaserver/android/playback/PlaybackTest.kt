package org.arcmediaserver.android.playback

import org.arcmediaserver.android.data.PlaybackPlan
import org.arcmediaserver.android.data.PlaybackRequest
import org.arcmediaserver.android.data.PlayerCaps
import org.arcmediaserver.android.data.SubtitleTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackTest {
    private val direct = PlaybackPlan(method = "direct", url = "/api/items/m1/stream?file=1", durationMs = 6_000_000)
    private val converted = PlaybackPlan(method = "transcode", url = "/api/transcode/s/index.m3u8", hls = true, sessionId = "s", offsetMs = 600_000, durationMs = 6_000_000)

    @Test
    fun `a converted stream's clock starts at offset_ms`() {
        assertEquals(615_000, PlaybackClock.filePosition(converted, 15_000))
        assertEquals(15_000, PlaybackClock.filePosition(direct, 15_000))
        assertEquals(5_000, PlaybackClock.playerStart(converted, 605_000))
        // A remux can begin at a keyframe before the point asked for.
        assertEquals(0, PlaybackClock.playerStart(converted.copy(offsetMs = 610_000), 605_000))
        assertEquals(605_000, PlaybackClock.playerStart(direct, 605_000))
    }

    @Test
    fun `seeks stay in the stream only within what's converted`() {
        assertTrue(PlaybackClock.seekInStream(direct, 5_000_000, 0))
        assertTrue(PlaybackClock.seekInStream(converted, 700_000, 300_000))
        assertFalse(PlaybackClock.seekInStream(converted, 500_000, 300_000)) // before the stream starts
        assertFalse(PlaybackClock.seekInStream(converted, 950_000, 300_000)) // beyond what's converted
    }

    @Test
    fun `a receiver taking DTS-HD or DTS-X takes DTS too, and says how much more`() {
        val hd = passthroughNames { it == androidx.media3.common.C.ENCODING_DTS_HD || it == androidx.media3.common.C.ENCODING_AC3 }
        assertEquals(setOf("ac3", "dts", "dts_hd"), hd)
        val uhd = passthroughNames { it == androidx.media3.common.C.ENCODING_DTS_UHD_P2 }
        assertEquals(setOf("dts", "dts_uhd"), uhd)
        assertEquals(setOf("dts"), passthroughNames { it == androidx.media3.common.C.ENCODING_DTS })
        // DTS plays only by passthrough; the profiles are listed in a fixed order for Arc.
        val caps = capsFrom(setOf("video/avc"), passthrough = setOf("truehd", "dts_hd", "dts", "eac3"), maxHeight = 1080)
        assertEquals(listOf("eac3", "dts", "dts_hd", "truehd"), caps.audioPassthrough)
        assertEquals(listOf("eac3"), caps.audio)
    }

    @Test
    fun `caps come from decoders, passthrough and the display`() {
        val caps = capsFrom(setOf("video/avc", "video/hevc", "audio/mp4a-latm", "audio/opus", "audio/flac", "audio/mpeg"), setOf("ac3", "eac3"), 2160)
        assertEquals(listOf("h264", "hevc"), caps.video)
        assertEquals(listOf("aac", "mp3", "opus", "flac", "ac3", "eac3"), caps.audio)
        assertEquals(listOf("mp4", "mkv", "webm", "mpegts"), caps.containers)
        assertEquals(2160, caps.maxHeight)
        assertEquals(listOf("ac3", "eac3"), caps.audioPassthrough)
        assertNull(caps.hdrFormats) // the display said nothing: unknown, not SDR only
    }

    @Test
    fun `output channels are independent of codec and passthrough names and survive retries`() {
        for (channels in listOf(1, 2, 6, 8, 32)) {
            val caps = capsFrom(setOf("audio/mp4a-latm"), setOf("truehd"), 1080,
                maxAudioChannels = channels)
            assertEquals(channels, caps.maxAudioChannels)
            assertEquals(channels, fallbackCaps(caps, "direct").maxAudioChannels)
            assertEquals(channels, fallbackCaps(caps, "remux").maxAudioChannels)
        }
        // A decoder or receiver codec name alone says nothing about the channel limit.
        assertNull(capsFrom(setOf("audio/eac3"), setOf("truehd"), 1080).maxAudioChannels)
        for (unknown in listOf(-1, 0, 33, Int.MAX_VALUE)) {
            assertNull(capsFrom(emptySet(), emptySet(), 1080, maxAudioChannels = unknown).maxAudioChannels)
        }
    }

    @Test
    fun `TrueHD by passthrough is for files played as they are, HDR and Dolby Vision come from the display and decoder`() {
        // A Chromecast with Google TV on a Dolby Vision TV and a receiver taking TrueHD.
        val caps = capsFrom(
            setOf("video/hevc", "video/dolby-vision", "audio/mp4a-latm", "audio/eac3", "audio/ac3"),
            passthrough = setOf("eac3", "truehd"), maxHeight = 2160,
            hdrTypes = setOf(1, 2, 3), dolbyVisionProfileBits = listOf(0x20, 0x100, 0x100),
        )
        assertEquals(listOf("aac", "ac3", "eac3"), caps.audio)
        assertEquals(listOf("eac3", "truehd"), caps.audioPassthrough)
        assertEquals(listOf("hdr10", "hlg", "dolby_vision"), caps.hdrFormats)
        assertEquals(listOf(5, 8), caps.dolbyVisionProfiles)
        // A Dolby Vision TV without a Dolby Vision decoder: HDR10 only.
        val noDecoder = capsFrom(setOf("video/hevc"), emptySet(), 2160, hdrTypes = setOf(1, 2))
        assertEquals(listOf("hdr10"), noDecoder.hdrFormats)
        assertNull(noDecoder.dolbyVisionProfiles)
    }

    @Test
    fun `after a failure Arc is asked to do more`() {
        val caps = PlayerCaps(listOf("mp4", "mkv"), listOf("h264"), listOf("aac"), 1080)
        assertEquals(caps.copy(containers = emptyList()), fallbackCaps(caps, "direct"))
        assertEquals(caps.copy(containers = emptyList(), video = emptyList()), fallbackCaps(caps, "remux"))
    }

    private fun sub(id: String, lang: String?, forced: Boolean = false, sdh: Boolean = false, image: Boolean = false, external: Boolean = false) =
        SubtitleTrack(id = id, language = lang, forced = forced, hearingImpaired = sdh, image = image, external = external)

    @Test
    fun `subtitles start in the profile's language, else forced ones for the audio`() {
        val tracks = listOf(sub("1", "eng", forced = true), sub("2", "eng", sdh = true), sub("3", "en"), sub("4", "fre"), sub("5", "jpn", image = true))
        assertEquals("3", chooseSubtitle(tracks, "eng", "jpn")?.id)
        assertEquals("4", chooseSubtitle(tracks, "fr", "eng")?.id)
        assertEquals("1", chooseSubtitle(tracks, "", "en")?.id)
        assertNull(chooseSubtitle(tracks, "", "jpn"))
    }

    @Test
    fun `a picture track is chosen when it's the only match, as on the web`() {
        // Full text subtitles first, even after a picture track in the file.
        val blurayAndText = listOf(sub("1", "eng", image = true), sub("2", "eng"))
        assertEquals("2", chooseSubtitle(blurayAndText, "eng", "fre")?.id)
        // Only PGS in the profile's language: that, to be burned in.
        assertEquals("5", chooseSubtitle(listOf(sub("4", "fre"), sub("5", "jpn", image = true)), "jpn", "eng")?.id)
        // Forced PGS for the audio's language, with no language chosen ("manual" mode too).
        val forcedPgs = listOf(sub("7", "eng", forced = true, image = true), sub("8", "eng", image = true))
        assertEquals("7", chooseSubtitle(forcedPgs, "", "eng")?.id)
        assertEquals("7", chooseSubtitle(forcedPgs, "eng", "eng", mode = "manual")?.id)
        // A picture file beside the video can't be burned in, so it's never chosen.
        assertNull(chooseSubtitle(listOf(sub("9", "eng", image = true, external = true)), "eng", "fre"))
    }

    @Test
    fun `picture tracks are offered and burned in, text tracks and Off aren't`() {
        val text = sub("1", "eng")
        val pgs = sub("2", "eng", image = true)
        val besideFile = sub("3", "eng", image = true, external = true)
        assertEquals(listOf(text, pgs), BurnIn.offered(listOf(text, pgs, besideFile)))
        assertEquals("2", BurnIn.burnFor(pgs))
        assertNull(BurnIn.burnFor(text))
        assertNull(BurnIn.burnFor(null))
        assertNull(BurnIn.burnFor(besideFile))
        // Choosing a picture track restarts; another text track or Off restarts only to leave a burn.
        assertTrue(BurnIn.restarts(null, pgs))
        assertFalse(BurnIn.restarts("2", pgs))
        assertTrue(BurnIn.restarts("2", text))
        assertTrue(BurnIn.restarts("2", null))
        assertFalse(BurnIn.restarts(null, text))
        assertFalse(BurnIn.restarts(null, null))
    }

    @Test
    fun `restarts ask for the burn again, with the file, audio and quality`() {
        val caps = PlayerCaps(listOf("mp4"), listOf("h264"), listOf("aac"), 1080)
        val plan = PlaybackPlan(method = "transcode", url = "/x", fileId = 7, audioStream = 2, burnSubtitle = "s:5")
        // Quality, a seek past what's converted, a retry: the same file, audio and burn.
        val again = BurnIn.request(caps, 90_000, plan, audioStream = null, fileId = null, maxKbps = 4000, burn = "s:5")
        assertEquals(PlaybackRequest(caps, 90_000, fileId = 7, audioStream = 2, maxKbps = 4000, burnSubtitle = "s:5"), again)
        // Another audio track keeps the burn too.
        assertEquals("s:5", BurnIn.request(caps, 0, plan, audioStream = 3, fileId = null, maxKbps = null, burn = "s:5").burnSubtitle)
        // Without a burn the field isn't sent at all.
        val raw = org.arcmediaserver.android.data.ArcJson.encodeToString(PlaybackRequest.serializer(), BurnIn.request(caps, 0, plan, null, null, null, null))
        assertFalse("burn_subtitle" in raw)
    }

    @Test
    fun `languages match by language, not spelling`() {
        assertTrue(sameLanguage("en", "eng"))
        assertTrue(sameLanguage("en-GB", "eng"))
        assertFalse(sameLanguage("en", "fr"))
        assertFalse(sameLanguage(null, "en"))
    }
}
