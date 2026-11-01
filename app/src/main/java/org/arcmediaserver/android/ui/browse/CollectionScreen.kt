package org.arcmediaserver.android.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.arcmediaserver.android.AppContainer
import org.arcmediaserver.android.R
import org.arcmediaserver.android.data.ArcLog
import org.arcmediaserver.android.data.CollectionPage
import org.arcmediaserver.android.data.Item
import org.arcmediaserver.android.ui.arcViewModel
import org.arcmediaserver.android.ui.components.LoadState
import org.arcmediaserver.android.ui.components.Loaded
import org.arcmediaserver.android.ui.components.requestWhenReady
import org.arcmediaserver.android.ui.components.toUiError
import org.arcmediaserver.android.ui.theme.ArcColors

class CollectionViewModel(private val container: AppContainer, private val id: String) : ViewModel() {
    private val _state = MutableStateFlow<LoadState<CollectionPage>>(LoadState.Loading)
    val state: StateFlow<LoadState<CollectionPage>> = _state

    /** Whether this server keeps play queues (Play and Shuffle go through one). */
    private val _canQueue = MutableStateFlow(false)
    val canQueue: StateFlow<Boolean> = _canQueue

    init {
        load()
        viewModelScope.launch { _canQueue.value = container.queues.supported() }
    }

    /**
     * Plays the collection through Arc's play queue, in its order or shuffled, as the web's: Arc
     * puts a show's episodes in its place, and Shuffle then mixes up everything after the first.
     */
    fun play(shuffle: Boolean, onPlay: (Item, String) -> Unit) {
        val page = (_state.value as? LoadState.Ready)?.value ?: return
        val ids = page.items.map { it.id }.let { if (shuffle) it.shuffled() else it }
        if (ids.isEmpty()) return
        viewModelScope.launch {
            var queue = container.queues.start(ids)
            if (queue != null && shuffle) queue = container.queues.shuffle() ?: queue
            val first = queue?.entries?.firstOrNull { it.id == queue.currentEntryId && it.playable }
            if (first?.item == null) {
                ArcLog.Android.warn("playing a collection failed (shuffle=$shuffle)")
                return@launch
            }
            ArcLog.Android.debug("collection queued: ${queue.entries.size} entries, shuffle=$shuffle")
            onPlay(first.item, first.id)
        }
    }

    fun load() {
        val api = container.api() ?: return
        _state.value = LoadState.Loading
        viewModelScope.launch {
            _state.value = try {
                LoadState.Ready(api.collection(id))
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                ArcLog.Android.warn("collection failed to load: ${e.javaClass.simpleName}")
                LoadState.Failed(e.toUiError())
            }
        }
    }
}

/** A collection's page: its name, how many titles, and the titles in the library's grid. */
@Composable
fun CollectionRoute(id: String, onOpen: (Item) -> Unit, onPlayQueued: (Item, String) -> Unit = { _, _ -> }) {
    val vm = arcViewModel { CollectionViewModel(it, id) }
    val state by vm.state.collectAsStateWithLifecycle()
    val canQueue by vm.canQueue.collectAsStateWithLifecycle()
    CollectionScreen(
        state, onOpen, onRetry = vm::load,
        onPlay = if (canQueue) { shuffle -> vm.play(shuffle, onPlayQueued) } else null,
    )
}

/** A collection's page: its name and count, Play and Shuffle (as the web's), then its titles. */
@Composable
fun CollectionScreen(state: LoadState<CollectionPage>, onOpen: (Item) -> Unit, onRetry: () -> Unit, onPlay: ((shuffle: Boolean) -> Unit)? = null) {
    Box(Modifier.fillMaxSize().background(ArcColors.Background)) {
        Loaded(state, onRetry) { page ->
            val first = remember { FocusRequester() }
            val play = remember { FocusRequester() }
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.padding(start = 48.dp, top = 28.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
                    Text(page.collection.title, style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.width(14.dp))
                    Text(pluralStringResource(R.plurals.library_count, page.items.size, page.items.size), style = MaterialTheme.typography.titleMedium, color = ArcColors.Muted, modifier = Modifier.padding(bottom = 6.dp))
                    if (onPlay != null && page.items.isNotEmpty()) {
                        Spacer(Modifier.width(24.dp))
                        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                            HeaderButton(androidx.compose.ui.res.stringResource(R.string.action_play), Modifier.focusRequester(play), icon = org.arcmediaserver.android.ui.components.ArcIcons.Play) { onPlay(false) }
                            HeaderButton(androidx.compose.ui.res.stringResource(R.string.action_shuffle), icon = org.arcmediaserver.android.ui.components.ArcIcons.Shuffle) { onPlay(true) }
                        }
                    }
                }
                TitleGrid(page.items, landscape = page.collection.kind == "folder", first, onOpen)
            }
            // The focus starts on Play when there is one, as Plex's collection page.
            LaunchedEffect(page.collection.id, onPlay != null) { if (onPlay == null || page.items.isEmpty() || !play.requestWhenReady()) first.requestWhenReady() }
        }
    }
}
