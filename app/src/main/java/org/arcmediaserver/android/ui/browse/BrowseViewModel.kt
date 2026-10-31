package org.arcmediaserver.android.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.arcmediaserver.android.AppContainer
import org.arcmediaserver.android.data.ArcApi
import org.arcmediaserver.android.data.ArcLog
import org.arcmediaserver.android.data.HomeRowSpec
import org.arcmediaserver.android.data.Item
import org.arcmediaserver.android.data.Library
import org.arcmediaserver.android.data.MediaCard
import org.arcmediaserver.android.data.Profile
import org.arcmediaserver.android.data.RowSource
import org.arcmediaserver.android.data.cardFor
import org.arcmediaserver.android.data.QueueRow
import org.arcmediaserver.android.data.queueCards
import org.arcmediaserver.android.data.customRowQuery
import org.arcmediaserver.android.data.resolveHomeRows
import org.arcmediaserver.android.ui.components.LoadState
import org.arcmediaserver.android.ui.components.toUiError

data class HomeRowState(val spec: HomeRowSpec, val cards: LoadState<List<MediaCard>>)

/** What the sidebar lists and who's watching. */
data class BrowseFrame(
    val libraries: List<Library>,
    val profile: Profile?,
    val canSwitchProfile: Boolean,
    /** How many libraries come before the Watchlist in the sidebar. */
    val watchlistAt: Int = 0,
)

/**
 * How a library's titles are sorted and filtered, as the web's library page: a sort and its
 * direction ([reverse] flips the usual one), unwatched only, and a genre, decade or quality.
 */
data class LibraryQuery(
    val sort: String = "title",
    val reverse: Boolean = false,
    val unwatched: Boolean = false,
    val genre: String? = null,
    val decade: Int? = null,
    val resolution: String? = null,
) {
    val filterCount get() = listOf(unwatched, genre != null, decade != null, resolution != null).count { it }

    /** /items parameters for this view of [library]: a page of [limit] from [offset]. */
    fun params(library: Library, limit: Int, offset: Int = 0): Map<String, String> = libraryQuery(library, sort, limit, offset) + buildMap {
        if (reverse && sort != "shuffle") put("reverse", "1")
        if (unwatched) put("unwatched", "1")
        genre?.let { put("genre", it) }
        decade?.let { put("decade", it.toString()) }
        resolution?.let { put("resolution", it) }
        // The A–Z bar needs to know where each letter starts.
        if (offset == 0 && sort == "title") put("letters", "1")
    }
}

/**
 * A library's titles as far as they're loaded: [items] by position in the whole list of [total],
 * loaded a page at a time wherever the grid is (so a jump to a letter needn't load what's before
 * it). [letters] counts titles by first character, for the A–Z bar (title sort only).
 */
data class LibraryPage(
    val library: Library,
    val total: Int,
    val items: Map<Int, Item>,
    val query: LibraryQuery = LibraryQuery(),
    val letters: List<org.arcmediaserver.android.data.LetterCount> = emptyList(),
) {
    companion object {
        /** A page whose titles are all loaded, in order. */
        fun of(library: Library, items: List<Item>, total: Int = items.size, query: LibraryQuery = LibraryQuery()) =
            LibraryPage(library, total, items.withIndex().associate { it.index to it.value }, query)
    }
}

/** A library's tabs, as Plex's and the web's: Recommended, its titles, and its collections. */
enum class LibraryTab { Recommended, Titles, Collections }

/** What a library shows besides its titles; loaded alongside them. */
data class LibraryExtras(
    val recommended: LoadState<List<org.arcmediaserver.android.data.RecommendedRow>> = LoadState.Loading,
    val collections: LoadState<List<org.arcmediaserver.android.data.CollectionRef>> = LoadState.Loading,
    /** What the library's filters can be set to; empty until loaded (or if it fails). */
    val facets: org.arcmediaserver.android.data.Facets = org.arcmediaserver.android.data.Facets(),
)

/** The tabs a library has: no Recommended for home videos, no Collections tab when it has none. */
fun libraryTabs(library: Library, extras: LibraryExtras): List<LibraryTab> = buildList {
    if (library.kind != "videos") add(LibraryTab.Recommended)
    add(LibraryTab.Titles)
    if ((extras.collections as? LoadState.Ready)?.value?.isNotEmpty() == true) add(LibraryTab.Collections)
}

sealed interface Section {
    data object Home : Section
    data object Search : Section
    data class Library(val id: Long) : Section
    data object Watchlist : Section
}

data class BrowseUiState(
    val frame: LoadState<BrowseFrame> = LoadState.Loading,
    val section: Section = Section.Home,
    val rows: List<HomeRowState> = emptyList(),
    val library: LoadState<LibraryPage> = LoadState.Loading,
    val libraryTab: LibraryTab = LibraryTab.Recommended,
    val libraryExtras: LibraryExtras = LibraryExtras(),
    /** The profile's watchlist, while it's shown from the sidebar. */
    val watchlist: LoadState<List<Item>> = LoadState.Loading,
)

/** Loads one Home row's cards. */
suspend fun loadRow(api: ArcApi, spec: HomeRowSpec): List<MediaCard> = when (val s = spec.source) {
    is RowSource.Continue -> api.continueWatching(s.withUpNext).map { cardFor(it) }
    RowSource.NextUp -> api.nextUp().map { cardFor(it) }
    RowSource.Watchlist -> api.watchlist().map { cardFor(it) }
    is RowSource.Recent ->
        if (s.library.kind == "shows" && !s.showsOnly) api.recentShows(s.library.id).map { cardFor(it) }
        else api.items(libraryQuery(s.library, sort = "added", limit = 24)).items.map { cardFor(it) }
    is RowSource.Custom -> api.items(customRowQuery(s.filter, s.library)).items.map { cardFor(it) }
    is RowSource.Seasonal -> api.items(s.pick.query + ("limit" to "24")).items.map { cardFor(it) }
    is RowSource.Collection -> api.collection(s.id).items.map { cardFor(it) }
    is RowSource.Playlist -> api.playlist(s.id).entries.map { cardFor(it.item, key = "entry:${it.entryId}") }
    RowSource.Queue -> emptyList() // BrowseViewModel follows the queue itself
}

fun libraryQuery(library: Library, sort: String, limit: Int, offset: Int = 0): Map<String, String> = buildMap {
    put("library_id", library.id.toString())
    put("kind", if (library.kind == "shows") "series" else "movie")
    put("sort", sort)
    put("limit", limit.toString())
    if (offset > 0) put("offset", offset.toString())
}

class BrowseViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(BrowseUiState())
    val state: StateFlow<BrowseUiState> = _state

    init {
        load()
        // Play Next and Add to Queue (from any card's menu) show in the Queue row at once.
        viewModelScope.launch {
            container.queues.queue.collect { q ->
                val cards = queueCards(q)
                _state.update { s ->
                    val rows = s.rows.filter { it.spec.source != RowSource.Queue }
                    s.copy(rows = if (cards.isEmpty() || rows.isEmpty()) rows else rows + HomeRowState(QueueRow, LoadState.Ready(cards)))
                }
            }
        }
    }

    /** Loads the sidebar and Home; called again by Retry and when coming back from playing. */
    fun load() {
        val api = container.api() ?: return
        _state.update { it.copy(frame = if (it.frame is LoadState.Ready) it.frame else LoadState.Loading) }
        viewModelScope.launch {
            try {
                val started = System.currentTimeMillis()
                val (libraries, prefs, status) = coroutineScope {
                    val libs = async { api.libraries() }
                    val prefs = async { api.prefs() }
                    val status = async { api.authStatus() }
                    Triple(libs.await(), prefs.await(), status.await())
                }
                val order = prefs.libraryOrder
                val sorted = libraries.sortedBy { lib -> order.indexOf(lib.id).let { if (it < 0) Int.MAX_VALUE else it } }
                _state.update {
                    it.copy(frame = LoadState.Ready(BrowseFrame(sorted, status.profile, canSwitchProfile = !status.friend, watchlistAt = prefs.watchlistPosition.coerceIn(0, sorted.size))))
                }
                // The queue (when this server keeps one) comes after everything the profile arranged
                // (owner, 31 October).
                val queue = if (container.queues.supported()) container.queues.refresh() else null
                val specs = resolveHomeRows(prefs, sorted) + (if (queue != null) listOf(QueueRow) else emptyList())
                val current = _state.value.rows.associateBy { it.spec.id }
                _state.update { s -> s.copy(rows = specs.map { spec -> current[spec.id]?.copy(spec = spec) ?: HomeRowState(spec, LoadState.Loading) }) }
                if (queue != null) setRow(QueueRow, LoadState.Ready(queueCards(container.queues.queue.value)))
                specs.filter { it.source != RowSource.Queue }.map { spec -> async { loadRowInto(api, spec) } }.awaitAll()
                ArcLog.Android.debug("home loaded: ${specs.size} rows in ${System.currentTimeMillis() - started} ms")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                ArcLog.Android.warn("home failed to load: ${e.javaClass.simpleName}")
                _state.update { it.copy(frame = LoadState.Failed(e.toUiError())) }
            }
        }
    }

    fun retryRow(spec: HomeRowSpec) {
        val api = container.api() ?: return
        setRow(spec, LoadState.Loading)
        viewModelScope.launch { loadRowInto(api, spec) }
    }

    private suspend fun loadRowInto(api: ArcApi, spec: HomeRowSpec) {
        val result = try {
            LoadState.Ready(loadRow(api, spec))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            ArcLog.Android.warn("home row ${spec.id} failed: ${e.javaClass.simpleName}")
            LoadState.Failed(e.toUiError())
        }
        setRow(spec, result)
    }

    private fun setRow(spec: HomeRowSpec, cards: LoadState<List<MediaCard>>) = _state.update { s ->
        s.copy(rows = s.rows.map { if (it.spec.id == spec.id) it.copy(cards = cards) else it })
    }

    fun show(section: Section) {
        if (section == _state.value.section && section !is Section.Library) return
        _state.update { it.copy(section = section) }
        if (section is Section.Library) loadLibrary(section.id)
        if (section == Section.Watchlist) loadWatchlist()
    }

    fun loadWatchlist() {
        val api = container.api() ?: return
        if (_state.value.watchlist !is LoadState.Ready) _state.update { it.copy(watchlist = LoadState.Loading) }
        viewModelScope.launch {
            val result = try {
                LoadState.Ready(api.watchlist())
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                ArcLog.Android.warn("watchlist failed to load: ${e.javaClass.simpleName}")
                LoadState.Failed(e.toUiError())
            }
            _state.update { it.copy(watchlist = result) }
        }
    }

    // Each library's sort and filters, kept while the app runs, as the web keeps them per library.
    private val libraryQueries = mutableMapOf<Long, LibraryQuery>()
    private val pagesLoading = mutableSetOf<Int>()

    fun loadLibrary(id: Long) {
        val api = container.api() ?: return
        val lib = (state.value.frame as? LoadState.Ready)?.value?.libraries?.find { it.id == id } ?: return
        // A library opens on Recommended, as Plex's TV app; home videos on their videos.
        val tab = if (lib.kind == "videos") LibraryTab.Titles else LibraryTab.Recommended
        _state.update { it.copy(library = LoadState.Loading, libraryTab = tab, libraryExtras = LibraryExtras()) }
        loadLibraryExtras(api, lib)
        loadTitles(api, lib, libraryQueries[lib.id] ?: LibraryQuery())
    }

    /** Sorts or filters the library's titles another way, and remembers it for this library. */
    fun setLibraryQuery(query: LibraryQuery) {
        val api = container.api() ?: return
        val lib = (state.value.library as? LoadState.Ready)?.value?.library ?: return
        libraryQueries[lib.id] = query
        loadTitles(api, lib, query)
    }

    private fun loadTitles(api: ArcApi, lib: Library, query: LibraryQuery) {
        _state.update { it.copy(library = LoadState.Loading) }
        pagesLoading.clear()
        viewModelScope.launch {
            val result = try {
                val page = api.items(query.params(lib, PAGE))
                LoadState.Ready(LibraryPage(lib, page.total, page.items.withIndex().associate { it.index to it.value }, query, page.letters))
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                ArcLog.Android.warn("library ${lib.id} failed to load: ${e.javaClass.simpleName}")
                LoadState.Failed(e.toUiError())
            }
            _state.update { it.copy(library = result) }
        }
    }

    /** Loads the page holding position [index] (and the next one), when the grid gets near it. */
    fun needTitles(index: Int) {
        val api = container.api() ?: return
        val page = (state.value.library as? LoadState.Ready)?.value ?: return
        for (p in listOf(index / PAGE, index / PAGE + 1)) {
            val from = p * PAGE
            if (from >= page.total || page.items.containsKey(from) || !pagesLoading.add(p)) continue
            viewModelScope.launch {
                val more = try {
                    api.items(page.query.params(page.library, PAGE, offset = from)).items
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    ArcLog.Android.warn("library ${page.library.id} page failed: ${e.javaClass.simpleName}")
                    emptyList()
                } finally {
                    pagesLoading.remove(p)
                }
                _state.update { s ->
                    val cur = (s.library as? LoadState.Ready)?.value ?: return@update s
                    if (cur.library.id != page.library.id || cur.query != page.query) return@update s
                    s.copy(library = LoadState.Ready(cur.copy(items = cur.items + more.withIndex().associate { from + it.index to it.value })))
                }
            }
        }
    }

    fun showLibraryTab(tab: LibraryTab) = _state.update { it.copy(libraryTab = tab) }

    /** Recommended and the collections, each on its own so a slow one doesn't hold the other. */
    private fun loadLibraryExtras(api: ArcApi, lib: Library) {
        fun <T> load(what: String, fetch: suspend () -> T, put: (LibraryExtras, LoadState<T>) -> LibraryExtras) = viewModelScope.launch {
            val result: LoadState<T> = try {
                LoadState.Ready(fetch())
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                ArcLog.Android.warn("library ${lib.id} $what failed: ${e.javaClass.simpleName}")
                LoadState.Failed(e.toUiError())
            }
            _state.update { s ->
                if ((s.section as? Section.Library)?.id != lib.id) s else s.copy(libraryExtras = put(s.libraryExtras, result))
            }
        }
        if (lib.kind != "videos") load("recommended", { api.recommended(lib.id) }) { x, r -> x.copy(recommended = r) }
        load("collections", { api.libraryCollections(lib.id) }) { x, r -> x.copy(collections = r) }
        viewModelScope.launch {
            val facets = runCatching { api.facets(lib.id) }.getOrNull() ?: return@launch
            _state.update { s -> if ((s.section as? Section.Library)?.id != lib.id) s else s.copy(libraryExtras = s.libraryExtras.copy(facets = facets)) }
        }
    }

    fun retryLibraryExtras() {
        val api = container.api() ?: return
        val lib = (state.value.library as? LoadState.Ready)?.value?.library ?: return
        _state.update { it.copy(libraryExtras = LibraryExtras()) }
        loadLibraryExtras(api, lib)
    }

    fun signOut(done: () -> Unit) = container.signOut(done)

    private companion object {
        const val PAGE = 60
    }
}
