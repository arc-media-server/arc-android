package org.arcmediaserver.android.playback

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaCodecList
import android.view.Display
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioCapabilities
import org.arcmediaserver.android.data.PlayerCaps

/** Arc's codec names (as ffprobe calls them) and the decoder MIME types that play them. */
internal val videoMimes = linkedMapOf(
    "h264" to "video/avc",
    "hevc" to "video/hevc",
    "vp9" to "video/x-vnd.on2.vp9",
    "av1" to "video/av01",
    "mpeg2video" to "video/mpeg2",
    "mpeg4" to "video/mp4v-es",
)

internal val audioMimes = linkedMapOf(
    "aac" to "audio/mp4a-latm",
    "mp3" to "audio/mpeg",
    "opus" to "audio/opus",
    "flac" to "audio/flac",
    "vorbis" to "audio/vorbis",
    "ac3" to "audio/ac3",
    "eac3" to "audio/eac3",
    "dts" to "audio/vnd.dts",
    "truehd" to "audio/true-hd",
)

/** Formats the TV can send on to a receiver or soundbar as they are (HDMI passthrough). */
internal val passthroughEncodings = mapOf(
    "ac3" to C.ENCODING_AC3,
    "eac3" to C.ENCODING_E_AC3,
    "dts" to C.ENCODING_DTS,
    "truehd" to C.ENCODING_DOLBY_TRUEHD,
)

/**
 * What this device can play, worked out from what it has rather than a list per model:
 * the decoders Android lists, the sound formats the TV or receiver takes by passthrough, and the
 * tallest picture the display can show.
 *
 * Containers are the ones Media3 opens itself (MP4, Matroska/WebM, MPEG-TS), so such files play
 * as they are, which is what Plex does on Android TV too.
 */
fun capsFrom(
    decoders: Set<String>,
    passthrough: Set<String>,
    maxHeight: Int,
    hdrTypes: Set<Int> = emptySet(),
    dolbyVisionProfileBits: List<Int> = emptyList(),
): PlayerCaps {
    val dvProfiles = dolbyVisionProfiles(dolbyVisionProfileBits)
    return PlayerCaps(
        containers = listOf("mp4", "mkv", "webm", "mpegts"),
        video = videoMimes.filterValues { it in decoders }.keys.toList(),
        // Dolby Digital (Plus) by passthrough plays in Arc's converted streams too, so it counts
        // as playable; TrueHD and DTS by passthrough only for files played as they are.
        audio = audioMimes.filter { (name, mime) -> mime in decoders || name in passthrough && (name == "ac3" || name == "eac3") }.keys.toList(),
        maxHeight = maxHeight,
        audioPassthrough = audioMimes.keys.filter { it in passthrough },
        hdrFormats = hdrFormats(hdrTypes, dolbyVisionDecoder = "video/dolby-vision" in decoders && dvProfiles.isNotEmpty()),
        dolbyVisionProfiles = dvProfiles.takeIf { it.isNotEmpty() },
    )
}

/**
 * Arc's names for the HDR the screen shows (Display.HdrCapabilities types). Dolby Vision also
 * needs a Dolby Vision decoder. None found is sent as "unknown" (null), not "SDR only": a TV
 * that reports nothing (or is at an SDR menu mode) shouldn't have every HDR film converted.
 */
internal fun hdrFormats(types: Set<Int>, dolbyVisionDecoder: Boolean): List<String>? = buildList {
    if (2 in types) add("hdr10") // HDR_TYPE_HDR10
    if (3 in types) add("hlg") // HDR_TYPE_HLG
    if (4 in types) add("hdr10_plus") // HDR_TYPE_HDR10_PLUS
    if (1 in types && dolbyVisionDecoder) add("dolby_vision") // HDR_TYPE_DOLBY_VISION
}.takeIf { it.isNotEmpty() }

/**
 * Dolby Vision profile numbers from a decoder's profile flags (MediaCodecInfo.CodecProfileLevel
 * DolbyVisionProfile*: profile n is the flag 1 shl n, e.g. DvheSt 0x100 is profile 8).
 */
internal fun dolbyVisionProfiles(bits: List<Int>): List<Int> =
    bits.filter { it > 0 && it and (it - 1) == 0 }.map { Integer.numberOfTrailingZeros(it) }.filter { it <= 10 }.distinct().sorted()

/**
 * Caps for trying again after the player failed: a file played as it is failed, so have it
 * repackaged; repackaged failed, so have the video converted too (web/src/lib/playback.ts).
 */
fun fallbackCaps(caps: PlayerCaps, failedMethod: String): PlayerCaps =
    if (failedMethod == "direct") caps.copy(containers = emptyList()) else caps.copy(containers = emptyList(), video = emptyList())

@OptIn(UnstableApi::class)
fun deviceCaps(context: Context): PlayerCaps {
    val decoders = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
        .filter { !it.isEncoder }
        .flatMap { it.supportedTypes.map(String::lowercase) }
        .toSet()
    // Deprecated in Media3 1.11 in favour of the player's own audio output handling, but it is
    // still the way to ask, before playing, what the TV or receiver takes by passthrough.
    @Suppress("DEPRECATION")
    val audio = AudioCapabilities.getCapabilities(context, AudioAttributes.DEFAULT, null)
    val passthrough = passthroughEncodings.filterValues { audio.supportsEncoding(it) }.keys
    val dvBits = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
        .filter { !it.isEncoder && "video/dolby-vision" in it.supportedTypes.map(String::lowercase) }
        .flatMap { info -> runCatching { info.getCapabilitiesForType("video/dolby-vision").profileLevels.map { it.profile } }.getOrDefault(emptyList()) }
    return capsFrom(decoders, passthrough, displayHeight(context), hdrTypes(context), dvBits)
}

/** The HDR types the screen supports (in any of its modes, and as the display reports overall). */
private fun hdrTypes(context: Context): Set<Int> {
    val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY) ?: return emptySet()
    val types = mutableSetOf<Int>()
    @Suppress("DEPRECATION")
    display.hdrCapabilities?.supportedHdrTypes?.let { types += it.toList() }
    if (android.os.Build.VERSION.SDK_INT >= 34) display.supportedModes.forEach { types += it.supportedHdrTypes.toList() }
    return types
}

/** The tallest mode the display offers (a 4K TV often runs its menus at 1080p). */
private fun displayHeight(context: Context): Int {
    val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
        ?: return 1080
    val modes = display.supportedModes.map { minOf(it.physicalHeight, it.physicalWidth) }
    return (modes + minOf(display.mode.physicalHeight, display.mode.physicalWidth)).maxOrNull()?.takeIf { it > 0 } ?: 1080
}
