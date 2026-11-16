package org.arcmediaserver.android.playback

import org.arcmediaserver.android.data.SubtitleTrack

/**
 * Subtitle timing, as the web player's: half a second at a time, up to ten seconds either way,
 * per file and track for this playback only (not saved). Positive shows the subtitles later.
 */
object SubtitleTiming {
    const val STEP_MS = 500L
    const val MAX_MS = 10_000L

    /** The offsets so far are kept by file and track. */
    fun key(fileId: Long, track: SubtitleTrack): String = "$fileId:${track.id}"

    /** The offset after a nudge of [deltaMs], or 0 for Reset ([deltaMs] null). */
    fun shifted(currentMs: Long, deltaMs: Long?): Long =
        if (deltaMs == null) 0 else (currentMs + deltaMs).coerceIn(-MAX_MS, MAX_MS)

    /** The offset for [track] of [fileId]: none when no subtitles are shown. */
    fun offsetFor(offsets: Map<String, Long>, fileId: Long, track: SubtitleTrack?): Long =
        track?.let { offsets[key(fileId, it)] } ?: 0

    /** The point in the subtitles to show at [positionMs]: a later offset shows earlier cues. */
    fun cueTimeMs(positionMs: Long, offsetMs: Long): Long = positionMs - offsetMs

    /** Only subtitles the app draws itself can be moved: not ASS (libass keeps its own timing) or pictures. */
    fun adjustable(track: SubtitleTrack?): Boolean =
        track != null && !track.image && track.codec != "ass" && track.codec != "ssa"
}
