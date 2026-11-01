package org.arcmediaserver.android.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.arcmediaserver.android.AppContainer
import org.arcmediaserver.android.data.ArcLog
import org.arcmediaserver.android.data.MediaCard
import org.arcmediaserver.android.data.Person
import org.arcmediaserver.android.data.SearchResults
import org.arcmediaserver.android.data.cardFor
import org.arcmediaserver.android.ui.components.UiError
import org.arcmediaserver.android.ui.components.toUiError

/** The groups of results, in the web's order (web/src/pages/Search.tsx). */
/** Top: movies and shows together, as the Apple TV app's Top Results (owner, 1 November). */
enum class SearchGroup { Top, People, Collections, Episodes, Genres }

data class SearchSection(
    val group: SearchGroup,
    val cards: List<MediaCard> = emptyList(),
    val people: List<Person> = emptyList(),
    val genres: List<org.arcmediaserver.android.data.GenreMatch> = emptyList(),
)

/** A collection as a poster card (its item's kind is "collection"). */
fun collectionCard(c: org.arcmediaserver.android.data.CollectionRef) = MediaCard(
    key = c.id, item = org.arcmediaserver.android.data.Item(id = c.id, kind = "collection", title = c.title, images = c.images),
    poster = c.images?.primary, thumb = c.images?.backdrop ?: c.images?.primary, progress = null,
)

/**
 * Arc's results as the screen shows them: one row per group that found something. Movies and shows
 * come together as Top Results, alternating, each keeping Arc's best-first order.
 */
fun searchSections(r: SearchResults): List<SearchSection> = listOf(
    SearchSection(SearchGroup.Top, cards = (0 until maxOf(r.movies.size, r.shows.size)).flatMap { i -> listOfNotNull(r.movies.getOrNull(i), r.shows.getOrNull(i)) }.map { cardFor(it) }),
    SearchSection(SearchGroup.People, people = r.people),
    SearchSection(SearchGroup.Collections, cards = r.collections.map { collectionCard(it) }),
    SearchSection(SearchGroup.Episodes, cards = r.episodes.map { cardFor(it) }),
    SearchSection(SearchGroup.Genres, genres = r.genres),
).filter { it.cards.isNotEmpty() || it.people.isNotEmpty() || it.genres.isNotEmpty() }

/** What happened to the words typed so far. */
sealed interface SearchOutcome {
    data object Cleared : SearchOutcome
    data class Started(val query: String) : SearchOutcome
    data class Found(val query: String, val sections: List<SearchSection>) : SearchOutcome
    data class Failed(val query: String, val error: UiError) : SearchOutcome
}

/**
 * Live search: waits until typing pauses for [debounceMs] (clearing the field is immediate), then
 * asks Arc; a newer query cancels a search still on its way, so only the latest one's results show.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
fun searchOutcomes(typed: Flow<String>, debounceMs: Long = 300, log: ArcLog = ArcLog.Android, search: suspend (String) -> SearchResults): Flow<SearchOutcome> =
    typed.map { it.trim() }
        .distinctUntilChanged()
        .debounce { if (it.isEmpty()) 0 else debounceMs }
        .transformLatest { q ->
            if (q.isEmpty()) {
                emit(SearchOutcome.Cleared)
                return@transformLatest
            }
            emit(SearchOutcome.Started(q))
            val outcome = try {
                SearchOutcome.Found(q, searchSections(search(q)))
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                log.warn("search failed: ${e.javaClass.simpleName}")
                SearchOutcome.Failed(q, e.toUiError())
            }
            emit(outcome)
        }

data class SearchUiState(
    val text: String = "",
    /** The query the sections answer. */
    val query: String = "",
    val sections: List<SearchSection> = emptyList(),
    val searching: Boolean = false,
    val error: UiError? = null,
)

class SearchViewModel(container: AppContainer) : ViewModel() {
    private val typed = MutableStateFlow("")
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state

    init {
        val api = container.api()
        if (api != null) viewModelScope.launch {
            searchOutcomes(typed, search = api::search).collect { o ->
                _state.update {
                    when (o) {
                        SearchOutcome.Cleared -> it.copy(query = "", sections = emptyList(), searching = false, error = null)
                        is SearchOutcome.Started -> it.copy(searching = true, error = null)
                        is SearchOutcome.Found -> it.copy(query = o.query, sections = o.sections, searching = false, error = null)
                        is SearchOutcome.Failed -> it.copy(query = o.query, searching = false, error = o.error)
                    }
                }
            }
        }
    }

    fun type(text: String) {
        _state.update { it.copy(text = text) }
        typed.value = text
    }
}
