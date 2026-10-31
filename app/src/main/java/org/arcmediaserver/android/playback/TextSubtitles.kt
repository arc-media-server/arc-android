package org.arcmediaserver.android.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.SubtitleParser
import androidx.media3.extractor.text.webvtt.WebvttParser

/**
 * A WebVTT file from Arc, parsed by the app rather than handed to the player. The player waits
 * for a sideloaded subtitle file to load completely before it starts, and Arc may still be
 * extracting it from the film; drawn this way the film starts at once and the subtitles appear
 * when they arrive.
 *
 * Times are the player's (Arc shifts the file to match a converted stream's start).
 */
@OptIn(UnstableApi::class)
class TextSubtitles private constructor(private val segments: List<CuesWithTiming>) {
    val size: Int get() = segments.size

    /** What shows at [positionUs]. WebvttParser gives non-overlapping segments, sorted by start. */
    fun cuesAt(positionUs: Long): List<Cue> {
        var lo = 0
        var hi = segments.size - 1
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (segments[mid].startTimeUs <= positionUs) { found = mid; lo = mid + 1 } else hi = mid - 1
        }
        if (found < 0) return emptyList()
        val s = segments[found]
        val end = if (s.durationUs == C.TIME_UNSET) segments.getOrNull(found + 1)?.startTimeUs ?: Long.MAX_VALUE else s.endTimeUs
        return if (positionUs < end) s.cues else emptyList()
    }

    companion object {
        fun parse(bytes: ByteArray): TextSubtitles {
            val out = ArrayList<CuesWithTiming>()
            WebvttParser().parse(bytes, SubtitleParser.OutputOptions.allCues()) { if (it.cues.isNotEmpty()) out.add(it) }
            out.sortBy { it.startTimeUs }
            return TextSubtitles(out)
        }
    }
}
