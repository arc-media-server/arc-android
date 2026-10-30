package org.arcmediaserver.android.playback

import org.arcmediaserver.android.data.Item
import org.arcmediaserver.android.data.TrickplayInfo

/**
 * When the player offers the next episode, as the web player's Up Next does (Player.tsx): a
 * 10-second countdown when the credits start and again at the end, with Play Now and Cancel. With
 * the profile's Auto-Play off there's no next episode to offer, and the player closes at the end.
 */
object UpNext {
    const val COUNTDOWN_SECONDS = 10

    /** What happens when the title ends. */
    enum class AtEnd { Countdown, Close }

    /** The episode to offer while this one plays (Next, Play Next, the countdown), if any. */
    fun offered(next: Item?, autoPlay: Boolean): Item? = next?.takeIf { autoPlay }

    fun atEnd(next: Item?, autoPlay: Boolean): AtEnd = if (offered(next, autoPlay) != null) AtEnd.Countdown else AtEnd.Close

    /** Whether the credits starting should start the countdown: once per title, when there's a next one. */
    fun atCredits(next: Item?, autoPlay: Boolean, alreadyOffered: Boolean): Boolean = !alreadyOffered && offered(next, autoPlay) != null
}

/** One timeline thumbnail: which sheet, and the tile's place on it in the sheet's pixels. */
data class TrickplayFrame(val sheet: String, val x: Int, val y: Int, val width: Int, val height: Int)

/**
 * The thumbnail for a moment in the file (web Player.tsx `Thumb`): the tile at or before it, one
 * per interval, clamped to the ones made. Null when there's no sheet for it.
 */
fun trickplayFrame(info: TrickplayInfo, sheets: List<String>, fileMs: Long): TrickplayFrame? {
    if (info.count <= 0 || info.intervalMs <= 0 || info.columns <= 0 || info.rows <= 0) return null
    val index = (fileMs / info.intervalMs).toInt().coerceIn(0, info.count - 1)
    val perSheet = info.columns * info.rows
    val sheet = sheets.getOrNull(index / perSheet) ?: return null
    val pos = index % perSheet
    return TrickplayFrame(sheet, (pos % info.columns) * info.width, (pos / info.columns) * info.height, info.width, info.height)
}
