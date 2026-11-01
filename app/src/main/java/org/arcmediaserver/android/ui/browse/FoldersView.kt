package org.arcmediaserver.android.ui.browse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.arcmediaserver.android.R
import org.arcmediaserver.android.data.FolderPage
import org.arcmediaserver.android.data.Item
import org.arcmediaserver.android.data.Library
import org.arcmediaserver.android.data.MediaCard
import org.arcmediaserver.android.data.cardFor
import org.arcmediaserver.android.ui.components.LoadState
import org.arcmediaserver.android.ui.components.Loaded
import org.arcmediaserver.android.ui.components.MediaCardView
import org.arcmediaserver.android.ui.components.requestWhenReady
import org.arcmediaserver.android.ui.theme.ArcColors

/**
 * A home videos library folder by folder, as the web's VideoFolders: the folders in the current
 * one (a video's picture, a Folder tag and how many videos), then its own videos, with the way
 * down shown above. Back goes up a folder, landing on the one it came out of.
 */
@Composable
fun FoldersView(
    library: Library,
    path: String,
    state: LoadState<FolderPage>,
    focus: String?,
    first: FocusRequester,
    onOpenFolder: (String) -> Unit,
    onUp: () -> Unit,
    onOpen: (Item) -> Unit,
    onRetry: () -> Unit,
) {
    BackHandler(enabled = path.isNotEmpty(), onBack = onUp)
    Loaded(state, onRetry) { page ->
        Column {
            if (page.trail.isNotEmpty()) {
                Text(
                    (listOf(library.name) + page.trail.map { it.name }).joinToString("  ›  "),
                    style = MaterialTheme.typography.titleSmall, color = ArcColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 48.dp, top = 4.dp, bottom = 4.dp),
                )
            }
            val count = page.folders.size + page.items.size
            if (count == 0) {
                Text(stringResource(R.string.library_empty), style = MaterialTheme.typography.bodyLarge, color = ArcColors.Muted, modifier = Modifier.padding(start = 48.dp, top = 24.dp))
                return@Column
            }
            val tag = stringResource(R.string.folder_tag)
            // Where the focus lands: the folder it came out of, else the first card.
            val target = remember(page) { FocusRequester() }
            val at = focus?.let { f -> page.folders.indexOfFirst { it.path == f }.coerceAtLeast(0) }
            CardGrid(count, landscape = true, key = { if (it < page.folders.size) "f:" + page.folders[it].path else "v:$it" }) { i, mod ->
                val m = mod.then(if (i == 0) Modifier.focusRequester(first) else Modifier).then(if (i == at) Modifier.focusRequester(target) else Modifier)
                if (i < page.folders.size) {
                    val f = page.folders[i]
                    val card = MediaCard(
                        key = "f:" + f.path, item = Item(id = "", kind = "folder", title = f.name), poster = null, thumb = f.cover, progress = null,
                        sub = pluralStringResource(R.plurals.folder_videos, f.count, f.count), tag = tag,
                    )
                    MediaCardView(card, landscape = true, onClick = { onOpenFolder(f.path) }, modifier = m)
                } else {
                    val item = page.items[i - page.folders.size]
                    MediaCardView(cardFor(item), landscape = true, onClick = { onOpen(item) }, modifier = m)
                }
            }
            if (at != null) LaunchedEffect(page) { target.requestWhenReady() }
        }
    }
}
