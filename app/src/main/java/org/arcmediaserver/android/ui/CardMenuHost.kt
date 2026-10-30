package org.arcmediaserver.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
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
    var info by remember { mutableStateOf(false) }
    // Whether this server keeps a play queue (Play Next, Add to Queue), and what was just queued.
    var queueable by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    // Asked again when a menu opens (the profile may have changed); cached by the queue store.
    androidx.compose.runtime.LaunchedEffect(open) { queueable = container.queues.supported() }
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
    val menu = remember(queueable) {
        object : CardMenu {
            override val queue: ((Item, Boolean) -> Unit)? = if (queueable) ::queue else null
            override fun current(item: Item) = container.withChanges(item)
            override fun open(item: Item, onClose: () -> Unit) {
                info = false
                open = item to onClose
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
