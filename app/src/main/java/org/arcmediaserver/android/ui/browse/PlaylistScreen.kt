package org.arcmediaserver.android.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.res.stringResource
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
import org.arcmediaserver.android.data.Item
import org.arcmediaserver.android.data.MediaCard
import org.arcmediaserver.android.data.PlaylistInfo
import org.arcmediaserver.android.data.PlaylistPage
import org.arcmediaserver.android.ui.arcViewModel
import org.arcmediaserver.android.ui.components.LoadState
import org.arcmediaserver.android.ui.components.Loaded
import org.arcmediaserver.android.ui.components.MediaCardView
import org.arcmediaserver.android.ui.components.requestWhenReady
import org.arcmediaserver.android.ui.components.toUiError
import org.arcmediaserver.android.ui.theme.ArcColors

// Playlists, as the web's and Plex's: the profile's playlists (made on the web) from the sidebar,
// each a mosaic of its posters, and a playlist's page with Play, Shuffle and its titles in order.

/** A playlist's count and length: "6 titles · 12 hr 49 min". */
@Composable
private fun playlistLine(p: PlaylistInfo, n: Int): String {
    val minutes = (p.durationMs / 60000).toInt()
    return listOfNotNull(pluralStringResource(R.plurals.library_count, n, n), if (minutes > 0) runtimeText(minutes) else null).joinToString(" · ")
}

/** The sidebar's Playlists: its name and count, then the playlists in the library's grid. */
@Composable
fun PlaylistsContent(state: LoadState<List<PlaylistInfo>>, onOpen: (PlaylistInfo) -> Unit, onRetry: () -> Unit) {
    Loaded(state, onRetry) { list ->
        val first = remember { FocusRequester() }
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(start = 48.dp, top = 28.dp, bottom = 4.dp).height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.nav_playlists), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.width(14.dp))
                Text(pluralStringResource(R.plurals.playlists_count, list.size, list.size), style = MaterialTheme.typography.titleMedium, color = ArcColors.Muted)
            }
            if (list.isEmpty()) {
                Text(stringResource(R.string.playlists_empty), style = MaterialTheme.typography.bodyLarge, color = ArcColors.Muted, modifier = Modifier.padding(start = 48.dp, top = 24.dp))
            } else {
                CardGrid(list.size, landscape = false, key = { list[it].id }) { i, mod ->
                    val p = list[i]
                    val card = MediaCard(
                        key = p.id, item = Item(id = p.id, kind = "playlist", title = p.title),
                        poster = p.images.firstOrNull(), thumb = null, progress = null, mosaic = p.images, sub = pluralStringResource(R.plurals.library_count, p.count, p.count),
                    )
                    MediaCardView(card, landscape = false, onClick = { onOpen(p) }, modifier = if (i == 0) mod.focusRequester(first) else mod)
                }
            }
        }
        LaunchedEffect(list.isEmpty()) { if (list.isNotEmpty()) first.requestWhenReady() }
    }
}

class PlaylistViewModel(private val container: AppContainer, private val id: String) : ViewModel() {
    private val _state = MutableStateFlow<LoadState<PlaylistPage>>(LoadState.Loading)
    val state: StateFlow<LoadState<PlaylistPage>> = _state

    /** Whether this server keeps play queues (Play and Shuffle go through one). */
    private val _canQueue = MutableStateFlow(false)
    val canQueue: StateFlow<Boolean> = _canQueue

    init {
        load()
        viewModelScope.launch { _canQueue.value = container.queues.supported() }
    }

    /** Plays the playlist through a queue: in order, Arc snapshots the playlist itself; shuffled, its titles go in mixed up. */
    fun play(shuffle: Boolean, onPlay: (Item, String) -> Unit) {
        val page = (_state.value as? LoadState.Ready)?.value ?: return
        if (page.entries.isEmpty()) return
        viewModelScope.launch {
            val first = container.queues.playAll(page.entries.map { it.item.id }, shuffle, playlistId = id)
            if (first?.item == null) {
                ArcLog.Android.warn("playing a playlist failed (shuffle=$shuffle)")
                return@launch
            }
            onPlay(first.item, first.id)
        }
    }

    fun load() {
        val api = container.api() ?: return
        _state.value = LoadState.Loading
        viewModelScope.launch {
            _state.value = try {
                LoadState.Ready(api.playlist(id))
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                ArcLog.Android.warn("playlist failed to load: ${e.javaClass.simpleName}")
                LoadState.Failed(e.toUiError())
            }
        }
    }
}

@Composable
fun PlaylistRoute(id: String, onOpen: (Item) -> Unit, onPlayQueued: (Item, String) -> Unit) {
    val vm = arcViewModel { PlaylistViewModel(it, id) }
    val state by vm.state.collectAsStateWithLifecycle()
    val canQueue by vm.canQueue.collectAsStateWithLifecycle()
    PlaylistScreen(state, onOpen, onRetry = vm::load, onPlay = if (canQueue) { shuffle -> vm.play(shuffle, onPlayQueued) } else null)
}

/** A playlist's page: its name, count and length, Play and Shuffle, then its titles in order. */
@Composable
fun PlaylistScreen(state: LoadState<PlaylistPage>, onOpen: (Item) -> Unit, onRetry: () -> Unit, onPlay: ((shuffle: Boolean) -> Unit)? = null) {
    Box(Modifier.fillMaxSize().background(ArcColors.Background)) {
        Loaded(state, onRetry) { page ->
            val items = page.entries.map { it.item }
            TitlesPage(
                key = page.playlist.id,
                title = page.playlist.title,
                line = playlistLine(page.playlist, items.size),
                items = items,
                landscape = false,
                empty = stringResource(R.string.playlist_empty),
                onOpen = onOpen,
                onPlay = onPlay,
            )
        }
    }
}
