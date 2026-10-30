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
    val menu = remember {
        object : CardMenu {
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
            )
        }
    }
}
