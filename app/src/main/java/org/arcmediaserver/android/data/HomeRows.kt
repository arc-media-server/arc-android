package org.arcmediaserver.android.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonPrimitive

/** What a Home row shows. Built-in rows are named by the app; custom rows keep their own title. */
sealed interface RowSource {
    data class Continue(val withUpNext: Boolean) : RowSource
    data object NextUp : RowSource
    data object Watchlist : RowSource
    data class Recent(val library: Library, val showsOnly: Boolean) : RowSource
    data class Custom(val title: String, val library: Library, val filter: JsonObject) : RowSource
}

enum class CardStyle { Poster, Thumb }

/** [defaultStyle] is the look the web gives the row when the profile hasn't chosen one. */
data class HomeRowSpec(val id: String, val source: RowSource, val style: CardStyle, val defaultStyle: CardStyle = style)

/**
 * The profile's Home rows in its order, as the web app arranges them (web/src/lib/home.ts):
 * the saved layout first, then built-in rows it hasn't placed where they'd be by default. Rows the
 * TV app can't show yet (seasonal, collections, playlists) are left out; hidden rows too.
 */
fun resolveHomeRows(prefs: Prefs, libraries: List<Library>): List<HomeRowSpec> {
    val out = mutableListOf<Pair<HomeRowSpec?, String>>() // spec (null: placed but not shown here), id
    val placed = mutableSetOf<String>()
    fun builtIn(id: String, saved: HomeRowPref?): HomeRowSpec? {
        val style = saved?.style
        fun look(default: CardStyle) = when (style) {
            "poster" -> CardStyle.Poster
            "thumb", "arc" -> CardStyle.Thumb
            else -> default
        }
        return when {
            id == "continue" -> HomeRowSpec(id, RowSource.Continue(withUpNext = !prefs.splitUpNext), look(CardStyle.Thumb), CardStyle.Thumb)
            id == "next-up" -> if (prefs.splitUpNext) HomeRowSpec(id, RowSource.NextUp, look(CardStyle.Thumb), CardStyle.Thumb) else null
            id == "watchlist" -> HomeRowSpec(id, RowSource.Watchlist, look(CardStyle.Poster), CardStyle.Poster)
            id.startsWith("recent:") -> libraries.find { "recent:${it.id}" == id }?.let { lib ->
                val default = if (lib.kind == "videos") CardStyle.Thumb else CardStyle.Poster
                HomeRowSpec(id, RowSource.Recent(lib, saved?.showsOnly == true), look(default), default)
            }
            else -> null
        }
    }
    for (row in prefs.home) {
        placed += row.id
        val spec = when {
            row.hidden -> null
            row.id.startsWith("custom:") -> {
                val filter = row.filter
                val libId = (filter?.get("library_id") as? JsonPrimitive)?.content?.toLongOrNull()
                val lib = libraries.find { it.id == libId }
                if (filter == null || lib == null || row.title.isNullOrBlank()) null
                // The web shows a custom row as posters unless it was given another look.
                else HomeRowSpec(row.id, RowSource.Custom(row.title, lib, filter), if (row.style == "thumb" || row.style == "arc" || lib.kind == "videos") CardStyle.Thumb else CardStyle.Poster, CardStyle.Poster)
            }
            else -> builtIn(row.id, row)
        }
        out += spec to row.id
    }
    return withDefaults(out, { it.second }, libraries) { id -> builtIn(id, null) to id }.mapNotNull { it.first }
}

/**
 * [saved] with the built-in rows it hasn't placed (e.g. a new library's) where they'd be by
 * default: after the row that comes before it by default, or first (web/src/lib/home.ts).
 */
private fun <T> withDefaults(saved: List<T>, idOf: (T) -> String, libraries: List<Library>, make: (String) -> T): List<T> {
    val out = saved.toMutableList()
    val placed = saved.map(idOf).toSet()
    val defaults = listOf("continue", "next-up", "watchlist", "seasonal") + libraries.map { "recent:${it.id}" }
    defaults.forEachIndexed { i, id ->
        if (id in placed) return@forEachIndexed
        val prev = defaults.subList(0, i).asReversed().firstOrNull { p -> out.any { idOf(it) == p } }
        val at = if (prev == null) 0 else out.indexOfFirst { idOf(it) == prev } + 1
        out.add(at, make(id))
    }
    return out
}

private val JsonObject.rowId get() = this["id"]?.jsonPrimitive?.content.orEmpty()

/**
 * Edits to the profile's saved Home layout ([saved], the rows exactly as Arc stores them, so fields
 * this app doesn't know survive). Rows the TV doesn't show (hidden, seasonal, collections) keep
 * their places; the result lists every row, as the web's Edit Home saves it.
 */
object HomeLayout {
    /** Moves row [id] past its neighbour [step] places away among [shown], the rows Home shows; null when there's none. */
    fun move(saved: List<JsonObject>, libraries: List<Library>, shown: List<String>, id: String, step: Int): List<JsonObject>? {
        val other = shown.getOrNull(shown.indexOf(id).takeIf { it >= 0 }?.plus(step) ?: return null) ?: return null
        val out = withDefaults(saved, { it.rowId }, libraries) { JsonObject(mapOf("id" to JsonPrimitive(it))) }.toMutableList()
        val from = out.indexOfFirst { it.rowId == id }
        if (from < 0 || out.none { it.rowId == other }) return null
        val row = out.removeAt(from)
        val to = out.indexOfFirst { it.rowId == other }
        out.add(if (step > 0) to + 1 else to, row)
        return out
    }

    /** Gives row [spec] the [style] (its default look is saved as no style, as the web does). */
    fun style(saved: List<JsonObject>, libraries: List<Library>, spec: HomeRowSpec, style: CardStyle): List<JsonObject> =
        withDefaults(saved, { it.rowId }, libraries) { JsonObject(mapOf("id" to JsonPrimitive(it))) }.map { row ->
            if (row.rowId != spec.id) return@map row
            val rest = row - "style"
            JsonObject(if (style == spec.defaultStyle) rest else rest + ("style" to JsonPrimitive(if (style == CardStyle.Thumb) "thumb" else "poster")))
        }
}

/** A card on a row: a title, or a season of a show with new episodes. */
data class MediaCard(
    val key: String,
    val item: Item,
    /** A season of new episodes (Recently Added in a TV library): its number and how many. */
    val seasonNumber: Int? = null,
    val newEpisodes: Int = 0,
    val poster: String?,
    val thumb: String?,
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
        thumb = thumb,
        progress = progress,
    )
}

/** A TV library's Recently Added card: a new show, new episodes of a season, or one episode. */
fun cardFor(card: RecentCard): MediaCard {
    val base = cardFor(card.item, key = "${card.type}:${card.item.id}:${card.seasonNumber ?: ""}")
    return if (card.type == "season") {
        base.copy(seasonNumber = card.seasonNumber, newEpisodes = card.episodes, poster = card.seasonPoster ?: base.poster)
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
