package org.arcmediaserver.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.arcmediaserver.android.AppContainer
import org.arcmediaserver.android.data.ArcLog
import org.arcmediaserver.android.data.Item
import org.arcmediaserver.android.ui.components.CardMenu
import org.arcmediaserver.android.ui.components.LocalCardMenu
import org.arcmediaserver.android.ui.components.toUiError
import org.arcmediaserver.android.ui.details.MediaInfoDialog
import org.arcmediaserver.android.ui.details.MoreMenu

/**
 * Gives every card in the app its menu (hold OK, as Plex): the title page's ⋯ menu, so Watched
 * and Watchlist work from any row or grid. A change shows at once on every card of the title and
 * is saved to Arc; if Arc can't save it, the card goes back as it was.
 */
@Composable
fun CardMenuHost(container: AppContainer, content: @Composable () -> Unit) {
    var open by remember { mutableStateOf<Pair<Item, () -> Unit>?>(null) }
    var anchor by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var info by remember { mutableStateOf(false) }
    var inContinue by remember { mutableStateOf(false) }
    var queueEntry by remember { mutableStateOf<String?>(null) }
    // Whether this server keeps a play queue (Play Next, Add to Queue), and what was just queued.
    var queueable by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    // Add to Playlist…: the title being added (and where the focus goes back), and the playlists.
    var picking by remember { mutableStateOf<Pair<Item, () -> Unit>?>(null) }
    var playlists by remember { mutableStateOf<org.arcmediaserver.android.ui.components.LoadState<List<org.arcmediaserver.android.data.PlaylistInfo>>>(org.arcmediaserver.android.ui.components.LoadState.Loading) }
    var playlistLoads by remember { mutableStateOf(0) }
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(picking != null, playlistLoads) {
        if (picking == null) return@LaunchedEffect
        playlists = org.arcmediaserver.android.ui.components.LoadState.Loading
        val api = container.api() ?: return@LaunchedEffect
        playlists = try {
            org.arcmediaserver.android.ui.components.LoadState.Ready(api.playlists())
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            ArcLog.Android.warn("playlists failed to load for Add to Playlist: ${e.javaClass.simpleName}")
            org.arcmediaserver.android.ui.components.LoadState.Failed(e.toUiError())
        }
    }
    fun addToPlaylist(item: Item, playlist: org.arcmediaserver.android.data.PlaylistInfo) {
        val api = container.api() ?: return
        container.appScope.launch {
            val ok = try {
                api.addToPlaylist(playlist.id, listOf(item.id))
                true
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                ArcLog.Android.warn("adding to a playlist failed: ${e.javaClass.simpleName}")
                false
            }
            val text = context.getString(
                if (ok) org.arcmediaserver.android.R.string.playlist_added else org.arcmediaserver.android.R.string.playlist_add_failed,
                item.title, playlist.title,
            )
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { notice = text }
        }
    }
    // Asked again when a menu opens (the profile may have changed); cached by the queue store.
    androidx.compose.runtime.LaunchedEffect(open) { queueable = container.queues.supported() }
    // Watch Together, when this server and profile have it (asked once per profile).
    val together by container.together.available.collectAsState()
    androidx.compose.runtime.LaunchedEffect(open) { container.together.check() }
    // Starts a room; the player then opens on its lobby (ArcNavHost follows the room). A show's
    // room starts on the episode its Play would start (part-way through, else the next unwatched).
    fun watchTogether(item: Item) {
        container.appScope.launch {
            val target = if (!item.isSeries) item else runCatching {
                container.api()?.seasons(item.id)?.seasons?.filter { it.episodes.isNotEmpty() }
                    ?.let { org.arcmediaserver.android.ui.details.nextEpisode(it) }
            }.getOrNull()
            val problem = when {
                target == null -> org.arcmediaserver.android.R.string.together_no_episode
                !container.together.start(target, target.resumeMs) -> org.arcmediaserver.android.R.string.together_start_failed
                else -> null
            }
            if (problem != null) {
                val text = context.getString(problem)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { notice = text }
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(notice) {
        if (notice != null) {
            kotlinx.coroutines.delay(3_000)
            notice = null
        }
    }
    fun queue(item: Item, next: Boolean) {
        container.appScope.launch {
            val done = container.queues.add(listOf(item.id), next) != null
            val text = context.getString(
                when {
                    !done -> org.arcmediaserver.android.R.string.queue_failed
                    next -> org.arcmediaserver.android.R.string.queue_added_next
                    else -> org.arcmediaserver.android.R.string.queue_added
                },
                item.title,
            )
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { notice = text }
        }
    }
    val menu = remember(queueable, together) {
        object : CardMenu {
            override val queue: ((Item, Boolean) -> Unit)? = if (queueable) ::queue else null
            override val watchTogether: ((Item) -> Unit)? = if (together) ::watchTogether else null
            override fun current(item: Item) = container.withChanges(item)
            override fun offContinue(id: String) = container.offContinue[id] == true
            override fun addToPlaylist(item: Item, onClose: () -> Unit) {
                picking = item to onClose
            }
            override fun open(item: Item, onClose: () -> Unit, at: androidx.compose.ui.geometry.Rect?, continueRow: Boolean, entry: String?) {
                info = false
                inContinue = continueRow
                queueEntry = entry
                anchor = at
                open = item to onClose
            }
        }
    }
    // A card on Home's Queue row: arrange the queue, as the web player's queue list.
    fun queueEdit(edit: suspend () -> org.arcmediaserver.android.data.PlayQueue?) {
        container.appScope.launch {
            if (edit() == null) {
                val text = context.getString(org.arcmediaserver.android.R.string.queue_edit_failed)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { notice = text }
            }
        }
    }
    fun change(before: Item, after: Item, what: String, save: suspend () -> Unit) {
        container.itemChanges[before.id] = after
        container.appScope.launch {
            try {
                save()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                ArcLog.Android.warn("$what from a card failed: ${e.javaClass.simpleName}")
                container.itemChanges[before.id] = before
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalCardMenu provides menu) { content() }
        notice?.let { QueueNotice(it, Modifier.align(androidx.compose.ui.Alignment.BottomCenter)) }
        picking?.let { (item, restore) ->
            val done = {
                picking = null
                restore()
            }
            org.arcmediaserver.android.ui.details.PlaylistPicker(
                item, playlists,
                onPick = { p -> addToPlaylist(item, p); done() },
                onRetry = { playlistLoads++ },
                onClose = done,
            )
            return@Box
        }
        val (opened, restore) = open ?: return@Box
        val item = container.withChanges(opened)
        val close = {
            open = null
            restore()
        }
        val api = container.api() ?: return@Box
        if (info) {
            MediaInfoDialog(item, onClose = close)
        } else {
            MoreMenu(
                item,
                onWatched = { change(item, item.withWatched(!item.watched), "marking watched") { api.setPlayed(item.id, !item.watched) } },
                onWatchlist = { change(item, item.withWatchlist(!item.onWatchlist), "changing the watchlist") { api.setWatchlist(item.id, !item.onWatchlist) } },
                onMediaInfo = { info = true },
                onClose = close,
                onQueue = menu.queue?.let { q -> { next: Boolean -> q(item, next) } },
                onWatchTogether = menu.watchTogether?.let { w -> { w(item) } },
                // The menu gives way to the playlist list; closing that puts the focus back on the card.
                onAddToPlaylist = {
                    open = null
                    picking = item to restore
                },
                anchor = anchor,
                queueMoves = queueEntry?.let { entry ->
                    val q = container.queues.queue.value ?: return@let null
                    val at = q.entries.indexOfFirst { it.id == entry }
                    val current = q.currentIndex
                    org.arcmediaserver.android.ui.details.QueueMoves(
                        earlier = if (at > current + 1) { { queueEdit { container.queues.move(entry, -1) } } } else null,
                        later = if (at > current && at < q.entries.size - 1) { { queueEdit { container.queues.move(entry, 1) } } } else null,
                        remove = if (at > current) { { queueEdit { container.queues.remove(entry) } } } else null,
                    )
                },
                // Only on Home's Continue Watching row, as the web's card menu.
                onRemoveContinue = if (!inContinue) null else { ->
                    container.offContinue[item.id] = true
                    container.appScope.launch {
                        try {
                            api.removeFromContinue(item.id)
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            ArcLog.Android.warn("removing from Continue Watching failed: ${e.javaClass.simpleName}")
                            container.offContinue.remove(item.id)
                            val text = context.getString(org.arcmediaserver.android.R.string.action_remove_continue_failed)
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { notice = text }
                        }
                    }
                },
            )
        }
    }
}

/** A short note at the bottom of the screen (what was just queued), gone after a few seconds. */
@Composable
private fun QueueNotice(text: String, modifier: Modifier = Modifier) {
    androidx.tv.material3.Text(
        text,
        style = androidx.tv.material3.MaterialTheme.typography.bodyLarge,
        color = org.arcmediaserver.android.ui.theme.ArcColors.Foreground,
        modifier = modifier
            .padding(bottom = 40.dp)
            .background(org.arcmediaserver.android.ui.theme.ArcColors.Surface3, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .padding(horizontal = 20.dp, vertical = 12.dp),
    )
}
