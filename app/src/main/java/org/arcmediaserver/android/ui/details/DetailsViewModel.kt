package org.arcmediaserver.android.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    /** The season to open on: the next episode's. */
    val initialSeason: Int = 0,
)

/** The episode to carry on with: one part-way through, else the first not watched, specials last. */
fun nextEpisode(seasons: List<Season>): Item? {
    val ordered = seasons.filter { it.seasonNumber > 0 } + seasons.filter { it.seasonNumber <= 0 }
    val episodes = ordered.flatMap { it.episodes }.filter { it.available }
    return episodes.firstOrNull { it.resumeMs > 0 } ?: episodes.firstOrNull { it.userData?.played != true } ?: episodes.firstOrNull()
}

/** A title action's outcome to tell the viewer about: it couldn't be saved, so it was put back. */
enum class ActionProblem { Watched, Watchlist }

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

class DetailsViewModel(private val container: AppContainer, private val itemId: String) : ViewModel() {
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
            if (!ok) {
                ArcLog.Android.warn("marking ${item.kind} ${if (played) "watched" else "unwatched"} failed")
                _problem.value = ActionProblem.Watched
            } else if (item.isSeries) {
                load(quiet = true) // the episodes' ticks and what Play starts change too
            }
        }
    }

    fun toggleWatchlist() {
        val api = container.api() ?: return
        val item = (_state.value as? LoadState.Ready)?.value?.item?.takeIf { it.canWatchlist } ?: return
        val on = !item.onWatchlist
        viewModelScope.launch {
            val ok = optimistic(item, item.withWatchlist(on), ::showItem) { api.setWatchlist(item.id, on) }
            if (!ok) {
                ArcLog.Android.warn("${if (on) "adding to" else "removing from"} the watchlist failed")
                _problem.value = ActionProblem.Watchlist
            }
        }
    }

    init {
        load()
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
                    val idx = seasons.indexOfFirst { s -> s.episodes.any { it.id == next?.id } }.coerceAtLeast(0)
                    LoadState.Ready(Details(item, seasons, next, idx))
                } else {
                    LoadState.Ready(Details(item))
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                ArcLog.Android.warn("details failed to load: ${e.javaClass.simpleName}")
                if (quiet && _state.value is LoadState.Ready) return@launch
                LoadState.Failed(e.toUiError())
            }
        }
    }
}
