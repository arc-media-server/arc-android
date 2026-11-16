package org.arcmediaserver.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.arcmediaserver.android.R
import org.arcmediaserver.android.data.Item
import org.arcmediaserver.android.data.episodeArt
import org.arcmediaserver.android.data.spoilerHidden

/** The profile's Hide Spoilers (`hide_spoilers` in /me/prefs), for every screen. */
val LocalHideSpoilers = compositionLocalOf { false }

/**
 * An episode's picture ([thumb]: its still) that keeps spoilers back while the profile hides them:
 * the show's background, else the still blurred. Blur needs Android 12; older TVs show the show's
 * background where Arc has one.
 */
@Composable
fun EpisodeArtwork(item: Item, thumb: String?, label: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val art = episodeArt(item, thumb, LocalHideSpoilers.current)
    Artwork(art.url, label = label, modifier = if (art.blur) modifier.clipToBounds().blur(24.dp) else modifier, contentScale = contentScale)
}

/** A title's description, or a note in its place while it's an episode whose spoilers are hidden. */
@Composable
fun spoilerSafeOverview(item: Item): String? =
    if (item.spoilerHidden(LocalHideSpoilers.current)) stringResource(R.string.spoiler_hidden_description)
    else item.overview?.takeIf { it.isNotBlank() }
