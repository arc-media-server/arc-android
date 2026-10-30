package org.arcmediaserver.android.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** What a Home row shows. Built-in rows are named by the app; custom rows keep their own title. */
sealed interface RowSource {
    data class Continue(val withUpNext: Boolean) : RowSource
    data object NextUp : RowSource
    data object Watchlist : RowSource
    data class Recent(val library: Library, val showsOnly: Boolean) : RowSource
    data class Custom(val title: String, val library: Library, val filter: JsonObject) : RowSource
}

/** Posters, wide thumbnails, or Backdrops (the web's "arc": squarer cards with background art and the logo). */
enum class CardStyle { Poster, Thumb, Backdrop }

/** A Home row and its look; [seasonPosters] shows an episode's or season's own poster rather than its show's. */
data class HomeRowSpec(val id: String, val source: RowSource, val style: CardStyle, val seasonPosters: Boolean = false)

private fun styleOf(saved: String?): CardStyle? = when (saved) {
    "poster" -> CardStyle.Poster
    "thumb" -> CardStyle.Thumb
    "arc" -> CardStyle.Backdrop
    else -> null
}

/**
 * The profile's Home rows, as the web arranges them (web/src/lib/home.ts resolveRows). Their order
 * follows the profile's library order, as Plex's Home follows its pinned libraries: carrying on
 * watching and the watchlist first, then each library's newest followed by its own filter rows.
 * [libraries] must be in the profile's order. Only the rows carrying on watching take a saved look;
 * the rest keep their usual one. The layout is arranged on the web (Edit Home, Reorder libraries);
 * the TV follows it. Rows the TV can't show yet (seasonal, collections, playlists) and hidden rows
 * are left out.
 */
fun resolveHomeRows(prefs: Prefs, libraries: List<Library>): List<HomeRowSpec> {
    val saved = prefs.home.associateBy { it.id }
    fun shown(id: String) = saved[id]?.hidden != true
    val out = mutableListOf<HomeRowSpec>()
    fun carryOn(id: String, source: RowSource) {
        val row = saved[id]
        out += HomeRowSpec(id, source, styleOf(row?.style) ?: CardStyle.Thumb, seasonPosters = row?.posters == "season")
    }
    if (shown("continue")) carryOn("continue", RowSource.Continue(withUpNext = !prefs.splitUpNext))
    if (prefs.splitUpNext && shown("next-up")) carryOn("next-up", RowSource.NextUp)
    if (shown("watchlist")) out += HomeRowSpec("watchlist", RowSource.Watchlist, CardStyle.Poster)
    for (lib in libraries) {
        // Home videos rarely have posters worth showing: their rows use thumbnails.
        val look = if (lib.kind == "videos") CardStyle.Thumb else CardStyle.Poster
        val recent = "recent:${lib.id}"
        if (shown(recent)) out += HomeRowSpec(recent, RowSource.Recent(lib, saved[recent]?.showsOnly == true), look, seasonPosters = true)
        for (row in prefs.home) {
            val filter = row.filter
            if (!row.id.startsWith("custom:") || row.hidden || filter == null || row.title.isNullOrBlank()) continue
            if ((filter["library_id"] as? JsonPrimitive)?.content?.toLongOrNull() != lib.id) continue
            out += HomeRowSpec(row.id, RowSource.Custom(row.title, lib, filter), look)
        }
    }
    return out
}

/** A card on a row: a title, or a season of a show with new episodes. */
data class MediaCard(
    val key: String,
    val item: Item,
    /** A season of new episodes (Recently Added in a TV library): its number and how many. */
    val seasonNumber: Int? = null,
    val newEpisodes: Int = 0,
    val poster: String?,
    /** The episode's season poster, or a new season's own: shown on rows set to season posters. */
    val seasonPoster: String? = null,
    val thumb: String?,
    /** A Backdrops card's art: the title's background (the show's, for an episode), and its logo. */
    val backdrop: String? = null,
    val logo: String? = null,
    /** How far through, 0–1, or null when not started. */
    val progress: Float?,
)

/** Turns an item into a card, choosing artwork as the web's PosterCard and EpisodeCard do. */
fun cardFor(item: Item, key: String = item.id): MediaCard {
    val img = item.images
    val poster = if (item.isEpisode) img?.seriesPrimary ?: img?.primary else img?.primary
    val thumb = if (item.isEpisode) img?.primary ?: img?.seriesBackdrop ?: img?.backdrop else img?.backdrop ?: img?.primary
    val duration = item.durationMs
    val progress = if (item.resumeMs > 0 && duration > 0) (item.resumeMs.toFloat() / duration).coerceIn(0.02f, 1f) else null
    return MediaCard(
        key = key,
        item = item,
        poster = poster,
        seasonPoster = if (item.isEpisode) img?.seasonPrimary else null,
        thumb = thumb,
        backdrop = if (item.isEpisode) img?.seriesBackdrop ?: img?.primary else img?.backdrop ?: img?.primary,
        logo = if (item.isEpisode) img?.seriesLogo else img?.logo,
        progress = progress,
    )
}

/** A TV library's Recently Added card: a new show, new episodes of a season, or one episode. */
fun cardFor(card: RecentCard): MediaCard {
    val base = cardFor(card.item, key = "${card.type}:${card.item.id}:${card.seasonNumber ?: ""}")
    return if (card.type == "season") {
        base.copy(seasonNumber = card.seasonNumber, newEpisodes = card.episodes, seasonPoster = card.seasonPoster ?: base.poster)
    } else {
        base
    }
}

/** /items query parameters for a custom row's filter (web/src/lib/home.ts rowQuery). */
fun customRowQuery(filter: JsonObject, library: Library, limit: Int = 24): Map<String, String> {
    val keys = listOf(
        "library_id", "genre", "language", "label", "decade", "resolution", "unwatched", "added_within",
        "min_runtime", "max_runtime", "min_rating", "sort",
    )
    val out = linkedMapOf<String, String>()
    for (k in keys) {
        val v = (filter[k] as? JsonPrimitive)?.asQueryValue() ?: continue
        if (v.isNotEmpty() && v != "0") out[k] = v
    }
    out["kind"] = if (library.kind == "shows") "series" else "movie"
    out["limit"] = limit.toString()
    return out
}
