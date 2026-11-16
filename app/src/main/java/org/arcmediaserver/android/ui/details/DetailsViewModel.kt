package org.arcmediaserver.android.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.arcmediaserver.android.AppContainer
import org.arcmediaserver.android.data.ArcLog
import org.arcmediaserver.android.data.Item
import org.arcmediaserver.android.data.Season
import org.arcmediaserver.android.ui.components.LoadState
import org.arcmediaserver.android.ui.components.toUiError

/**
 * A title's page. For a show, its seasons and episodes come in the show's display order, which
 * Arc works out (GET /series/{id}/seasons without an ordering), so the TV shows what the web does.
 */
data class Details(
    val item: Item,
    val seasons: List<Season> = emptyList(),
    /** For a show: the episode Play starts (the one part-way through, or the next unwatched). */
    val next: Item? = null,
    /** The season to open on: the [focus] episode's, else the next episode's. */
    val initialSeason: Int = 0,
    /** An episode opened from a row (Continue Watching, Recently Added): the page lands on it. */
    val focus: Item? = null,
    /** Rows under the title, loaded after it: each collection it's in, then More Like This. */
    val related: List<RelatedRow> = emptyList(),
)

/** A row of titles under a title's page: a collection it's in ([title]), or More Like This (no title). */
data class RelatedRow(val key: String, val title: String?, val items: List<Item>, val landscape: Boolean = false)

/**
 * The rows under [item], as the web's title page (CollectionRows, SimilarRow): each collection
 * it's in without the title itself, then More Like This. A row that fails or is empty is left out.
 */
suspend fun relatedRows(api: org.arcmediaserver.android.data.ArcApi, item: Item): List<RelatedRow> = kotlinx.coroutines.coroutineScope {
    val collections = item.collections.map { c ->
        async {
            runCatching { api.collection(c.id) }.getOrNull()?.let { page ->
                RelatedRow("collection:${c.id}", c.title, page.items.filter { it.id != item.id }, landscape = page.collection.kind == "folder")
            }
        }
    }
    val similar = async { runCatching { api.similar(item.id) }.getOrNull()?.let { RelatedRow("similar", null, it) } }
    (collections.map { it.await() } + similar.await()).filterNotNull().filter { it.items.isNotEmpty() }
}

/** The episode to carry on with: one part-way through, else the first not watched, specials last. */
fun nextEpisode(seasons: List<Season>): Item? {
    val ordered = seasons.filter { it.seasonNumber > 0 } + seasons.filter { it.seasonNumber <= 0 }
    val episodes = ordered.flatMap { it.episodes }.filter { it.available }
    return episodes.firstOrNull { it.resumeMs > 0 } ?: episodes.firstOrNull { it.userData?.played != true } ?: episodes.firstOrNull()
}

/** A season counts as watched when it has episodes and every one is watched, as on the web. */
fun Season.watched(): Boolean = episodes.isNotEmpty() && episodes.all { it.userData?.played == true }

/** [seasons] with the season numbered [seasonNumber] marked watched or unwatched (resume points cleared). */
fun withSeasonWatched(seasons: List<Season>, seasonNumber: Int, played: Boolean): List<Season> =
    seasons.map { s -> if (s.seasonNumber != seasonNumber) s else s.copy(episodes = s.episodes.map { it.withWatched(played) }) }

/** A title action's outcome to tell the viewer about: it couldn't be saved, so it was put back. */
enum class ActionProblem { Watched, Watchlist, Season }

/**
 * Changes a title on screen straight away, then asks Arc; if Arc says no (or can't be reached),
 * puts the title back as it was and returns false. [show] gets each version of the title.
 */
suspend fun optimistic(before: Item, after: Item, show: (Item) -> Unit, save: suspend () -> Unit): Boolean {
    show(after)
    return try {
        save()
        true
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        show(before)
        false
    }
}

class DetailsViewModel(private val container: AppContainer, private val itemId: String, private val episodeId: String? = null) : ViewModel() {
    private val _state = MutableStateFlow<LoadState<Details>>(LoadState.Loading)
    val state: StateFlow<LoadState<Details>> = _state

    private val _problem = MutableStateFlow<ActionProblem?>(null)
    /** The last action that failed and was undone, until the screen has said so. */
    val problem: StateFlow<ActionProblem?> = _problem

    fun problemShown() {
        _problem.value = null
    }

    private fun showItem(item: Item) {
        val cur = (_state.value as? LoadState.Ready)?.value ?: return
        _state.value = LoadState.Ready(cur.copy(item = item))
    }

    /** Mark watched or unwatched (a show: all its episodes), as the web's Watched button. */
    fun toggleWatched() {
        val api = container.api() ?: return
        val item = (_state.value as? LoadState.Ready)?.value?.item ?: return
        val played = !item.watched
        viewModelScope.launch {
            val ok = optimistic(item, item.withWatched(played), ::showItem) { api.setPlayed(item.id, played) }
            if (ok) container.itemChanges[item.id] = item.withWatched(played)
            if (!ok) {
                ArcLog.Android.warn("marking ${item.kind} ${if (played) "watched" else "unwatched"} failed")
                _problem.value = ActionProblem.Watched
            } else if (item.isSeries) {
                load(quiet = true) // the episodes' ticks and what Play starts change too
            }
        }
    }

    /**
     * Mark Season as Watched / Unwatched, as the web's show page: every episode of the season
     * numbered [seasonNumber] (`season_number` on the show's watched call). The ticks change at
     * once; the page then refreshes, since what Play starts and the show's count follow.
     */
    fun toggleSeasonWatched(seasonNumber: Int) {
        val api = container.api() ?: return
        val details = (_state.value as? LoadState.Ready)?.value ?: return
        val season = details.seasons.firstOrNull { it.seasonNumber == seasonNumber } ?: return
        val played = !season.watched()
        viewModelScope.launch {
            val shown = withSeasonWatched(details.seasons, seasonNumber, played)
            _state.value = LoadState.Ready(details.copy(seasons = shown))
            try {
                api.setPlayed(details.item.id, played, seasonNumber)
                ArcLog.Android.info("marked a season ${if (played) "watched" else "unwatched"} (${season.episodes.size} episodes)")
                load(quiet = true)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                ArcLog.Android.warn("marking a season ${if (played) "watched" else "unwatched"} failed: ${e.javaClass.simpleName}")
                (_state.value as? LoadState.Ready)?.value?.let { cur ->
                    _state.value = LoadState.Ready(cur.copy(seasons = cur.seasons.map { s -> if (s.seasonNumber == seasonNumber) season else s }))
                }
                _problem.value = ActionProblem.Season
            }
        }
    }

    fun toggleWatchlist() {
        val api = container.api() ?: return
        val item = (_state.value as? LoadState.Ready)?.value?.item?.takeIf { it.canWatchlist } ?: return
        val on = !item.onWatchlist
        viewModelScope.launch {
            val ok = optimistic(item, item.withWatchlist(on), ::showItem) { api.setWatchlist(item.id, on) }
            if (ok) container.itemChanges[item.id] = item.withWatchlist(on)
            if (!ok) {
                ArcLog.Android.warn("${if (on) "adding to" else "removing from"} the watchlist failed")
                _problem.value = ActionProblem.Watchlist
            }
        }
    }

    private val _canShuffle = MutableStateFlow(false)
    /** A show can be shuffled when this server keeps play queues (the episodes play on in that order). */
    val canShuffle: StateFlow<Boolean> = _canShuffle

    init {
        load()
        viewModelScope.launch { _canShuffle.value = container.queues.supported() }
    }

    /** Shuffle (a show): its available episodes in a random order, as a new queue; [play] starts it. */
    fun shuffle(play: (Item, String) -> Unit) {
        val details = (_state.value as? LoadState.Ready)?.value ?: return
        val episodes = details.seasons.flatMap { it.episodes }.filter { it.available }.shuffled()
        if (episodes.isEmpty()) return
        viewModelScope.launch {
            val queue = container.queues.start(episodes.map { it.id })
            val first = queue?.entries?.firstOrNull { it.id == queue.currentEntryId && it.playable }
            if (queue == null || first?.item == null) {
                ArcLog.Android.warn("shuffling a show failed")
                return@launch
            }
            ArcLog.Android.debug("shuffled ${episodes.size} episodes")
            play(first.item, first.id)
        }
    }

    /** Loads the page; [quiet] keeps what's shown while it refreshes (coming back from playing). */
    fun load(quiet: Boolean = false) {
        val api = container.api() ?: return
        if (!quiet || _state.value !is LoadState.Ready) _state.value = LoadState.Loading
        viewModelScope.launch {
            _state.value = try {
                val item = api.item(itemId)
                if (item.isSeries) {
                    val seasons = api.seasons(item.id).seasons.filter { it.episodes.isNotEmpty() }
                    val next = nextEpisode(seasons)
                    val focus = episodeId?.let { id -> seasons.firstNotNullOfOrNull { s -> s.episodes.firstOrNull { it.id == id } } }
                    val land = focus ?: next
                    val idx = seasons.indexOfFirst { s -> s.episodes.any { it.id == land?.id } }.coerceAtLeast(0)
                    LoadState.Ready(Details(item, seasons, next, idx, focus, related = currentRelated()))
                } else {
                    LoadState.Ready(Details(item, related = currentRelated()))
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                ArcLog.Android.warn("details failed to load: ${e.javaClass.simpleName}")
                if (quiet && _state.value is LoadState.Ready) return@launch
                LoadState.Failed(e.toUiError())
            }
            // The rows under the title come after it, so the page shows without waiting for them.
            val item = (_state.value as? LoadState.Ready)?.value?.item ?: return@launch
            if (item.isEpisode) return@launch
            val related = relatedRows(api, item)
            (_state.value as? LoadState.Ready)?.value?.let { _state.value = LoadState.Ready(it.copy(related = related)) }
        }
    }

    private fun currentRelated() = (_state.value as? LoadState.Ready)?.value?.related.orEmpty()
}
