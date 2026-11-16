package org.arcmediaserver.android.data

/**
 * Episodes on disk that the show's episode order doesn't include ("Not in This Order", the
 * `unplaced` list of GET /series/{id}/seasons). They're shown so nothing goes missing, but they
 * have no place in the order: Play, Resume and Up Next follow the seasons only.
 */
data class EpisodeList(val episodes: List<Item>, val unplaced: Boolean = false)

/**
 * An episode's season and number in the show's order, or null when it has none: Arc numbers an
 * episode outside the order season 0, episode 0 (specials are season 0 from episode 1).
 */
val Item.orderNumbers: Pair<Int, Int>?
    get() {
        val s = seasonNumber ?: return null
        val e = episodeNumber ?: return null
        return if (s == 0 && e == 0) null else s to e
    }

/**
 * The episodes the player lists beside [itemId]: the season in the show's order that holds it,
 * else the episodes not in that order when it's one of them (shown without numbers), else the
 * season numbered [seasonNumber] (an older server, or one that didn't find it).
 */
fun episodesAround(response: SeasonsResponse, itemId: String, seasonNumber: Int?): EpisodeList {
    response.seasons.firstOrNull { s -> s.episodes.any { it.id == itemId } }?.let { return EpisodeList(it.episodes) }
    if (response.unplaced.any { it.id == itemId }) return EpisodeList(response.unplaced, unplaced = true)
    return EpisodeList(response.seasons.firstOrNull { it.seasonNumber == seasonNumber }?.episodes.orEmpty())
}
