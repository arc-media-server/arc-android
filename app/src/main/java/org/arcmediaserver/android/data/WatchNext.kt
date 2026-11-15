package org.arcmediaserver.android.data

/**
 * Google TV's "Continue watching" row (the system's Watch Next), from Arc's Continue Watching:
 * what to add, change and take away, worked out here so it can be tested without the TV's
 * provider. Plex and other media apps fill the same row.
 */
data class WatchNextEntry(
    val itemId: String,
    /** Part-way through (continue) or the next episode to start (next). */
    val partWay: Boolean,
    val isEpisode: Boolean,
    /** The show's name for an episode, else the title's. */
    val title: String,
    val episodeTitle: String?,
    val season: Int?,
    val episode: Int?,
    val year: Int?,
    val overview: String?,
    val positionMs: Long,
    val durationMs: Long,
    /** Arc paths: a wide picture (the episode's still or the title's background) and the poster. */
    val thumb: String?,
    val poster: String?,
    /** Newest first: the launcher orders the row by this. */
    val engagedAt: Long,
)

/** A program this app already put in the row: its id there and what it stood for. */
data class WatchNextPublished(val programId: Long, val itemId: String, val positionMs: Long, val browsable: Boolean)

data class WatchNextPlan(
    val add: List<WatchNextEntry>,
    val update: List<Pair<Long, WatchNextEntry>>,
    val remove: List<Long>,
)

/** At most this many titles go in the row (the launcher shows a handful). */
const val WATCH_NEXT_MAX = 20

/** Arc's Continue Watching (with Up Next) as the row's entries, in Arc's order, newest first. */
fun watchNextEntries(items: List<Item>, now: Long): List<WatchNextEntry> =
    items.filter { it.playable }.distinctBy { it.id }.take(WATCH_NEXT_MAX).mapIndexed { i, item ->
        val img = item.images
        WatchNextEntry(
            itemId = item.id,
            partWay = item.resumeMs > 0,
            isEpisode = item.isEpisode,
            title = if (item.isEpisode) item.seriesTitle ?: item.title else item.title,
            episodeTitle = if (item.isEpisode) item.title else null,
            // None for an episode outside the show's order (Arc's 0 and 0): no "S0 E0" on the TV's home.
            season = item.orderNumbers?.first,
            episode = item.orderNumbers?.second,
            year = item.year,
            overview = item.overview,
            positionMs = item.resumeMs,
            durationMs = item.durationMs,
            thumb = if (item.isEpisode) img?.primary ?: img?.seriesBackdrop else img?.backdrop ?: img?.primary,
            poster = if (item.isEpisode) img?.seriesPrimary ?: img?.primary else img?.primary,
            // A second apart, so the launcher keeps Arc's order.
            engagedAt = now - i * 1000L,
        )
    }

/**
 * What to change so the row matches [entries]. A program the person took out of the row
 * themselves (no longer browsable) stays out until they watch more of it, as Google asks.
 */
fun planWatchNext(entries: List<WatchNextEntry>, published: List<WatchNextPublished>): WatchNextPlan {
    val wanted = entries.associateBy { it.itemId }
    val add = mutableListOf<WatchNextEntry>()
    val update = mutableListOf<Pair<Long, WatchNextEntry>>()
    val remove = mutableListOf<Long>()
    val seen = mutableSetOf<String>()
    for (p in published) {
        val entry = wanted[p.itemId]
        when {
            entry == null || !seen.add(p.itemId) -> remove += p.programId
            p.browsable -> update += p.programId to entry
            p.positionMs == entry.positionMs -> Unit // taken out by the person, nothing watched since
            else -> { remove += p.programId; add += entry }
        }
    }
    entries.filter { it.itemId !in seen }.forEach { add += it }
    return WatchNextPlan(add, update, remove)
}
