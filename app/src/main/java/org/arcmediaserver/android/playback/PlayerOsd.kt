package org.arcmediaserver.android.playback

import org.arcmediaserver.android.data.Chapter

/**
 * The pure parts of the player's controls (the Apple TV app's PlayerOSD arrangement): the
 * timeline's chapter segments, where the marker sits, and which tabs a title has.
 */
object PlayerOsd {
    /** A stretch of the timeline, as fractions of its width. */
    data class Segment(val start: Float, val end: Float)

    /**
     * The timeline split at the chapters: one segment per chapter that starts inside the title,
     * in order, else the whole line. Chapters are trusted for their starts only: each segment
     * runs to the next chapter's start (the last to the end), so gaps and overlaps in the file's
     * chapter list don't show.
     */
    fun segments(chapters: List<Chapter>, totalMs: Long): List<Segment> {
        if (totalMs <= 0) return listOf(Segment(0f, 1f))
        val starts = chapters.map { it.startMs }.filter { it in 0 until totalMs }.distinct().sorted()
        if (starts.size < 2) return listOf(Segment(0f, 1f))
        val edges = (if (starts.first() > 0) listOf(0L) + starts else starts) + totalMs
        return edges.zipWithNext { a, b -> Segment(a.toFloat() / totalMs, b.toFloat() / totalMs) }
    }

    /** How far along the line [ms] is, from 0 to 1. */
    fun fraction(ms: Long, totalMs: Long): Float = if (totalMs <= 0) 0f else (ms.toFloat() / totalMs).coerceIn(0f, 1f)

    /** How much of [segment] is filled at [fraction] along the whole line, from 0 to 1. */
    fun filled(segment: Segment, fraction: Float): Float {
        val width = segment.end - segment.start
        return if (width <= 0f) 0f else ((fraction - segment.start) / width).coerceIn(0f, 1f)
    }

    /** The tab row under the timeline, as the Apple TV app's. */
    enum class Tab { Info, Chapters, Cast, Episodes }

    /** The tabs shown: Info always, the rest when the title has them. */
    fun tabs(chapters: Int, cast: Int, episodes: Int): List<Tab> = buildList {
        add(Tab.Info)
        if (chapters > 1) add(Tab.Chapters)
        if (cast > 0) add(Tab.Cast)
        if (episodes > 0) add(Tab.Episodes)
    }

    /** The speed after [current] (Playback's Speed row steps through them, back to the first). */
    fun nextSpeed(speeds: List<Float>, current: Float): Float {
        val i = speeds.indexOf(current)
        return if (i < 0) 1f else speeds[(i + 1) % speeds.size]
    }
}
