package org.arcmediaserver.android.ui.details

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.arcmediaserver.android.R
import org.arcmediaserver.android.data.Item
import org.arcmediaserver.android.data.MediaFile
import org.arcmediaserver.android.data.MediaStream
import org.arcmediaserver.android.playback.sameLanguage
import org.arcmediaserver.android.ui.components.ArcIcons
import org.arcmediaserver.android.ui.components.channelsName
import org.arcmediaserver.android.ui.components.codecName
import org.arcmediaserver.android.ui.components.hdrLabels
import org.arcmediaserver.android.ui.components.requestWhenReady
import org.arcmediaserver.android.ui.components.resolutionName
import org.arcmediaserver.android.ui.theme.ArcColors
import java.util.Locale

/** What's open over a title's page: nothing, the ⋯ menu, or Media Info. */
/** What a queue entry's menu can do: move it earlier or later, or take it out (null where it can't). */
data class QueueMoves(val earlier: (() -> Unit)? = null, val later: (() -> Unit)? = null, val remove: (() -> Unit)? = null)

enum class DetailsPanel { None, More, MediaInfo, About, Version, Rating }

/**
 * A panel over the page with a dimmed backdrop; the focus stays inside it until Back (or a choice)
 * closes it, as a Google TV dialog does.
 */
@Composable
internal fun Overlay(width: androidx.compose.ui.unit.Dp, onBack: () -> Unit, content: @Composable () -> Unit) {
    BackHandler(onBack = onBack)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .width(width)
                .heightIn(max = 500.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(ArcColors.Surface)
                .padding(horizontal = 24.dp, vertical = 22.dp)
                .focusProperties { onExit = { cancelFocusChange() } }
                .focusGroup(),
        ) { content() }
    }
}

/**
 * The ⋯ menu, as the web's (web/src/components/ItemActions.tsx MoreMenu) for what a TV viewer can
 * do: Watched, Watchlist and Media Info. (Play Next and Add to Queue are the web player's own queue,
 * which Arc's API doesn't offer to apps; the server owner's tools stay in the browser.)
 */
@Composable
fun MoreMenu(
    item: Item,
    onWatched: () -> Unit,
    onWatchlist: () -> Unit,
    onMediaInfo: () -> Unit,
    onClose: () -> Unit,
    /** Play Next ([true]) or Add to Queue; null when this server keeps no queue. */
    onQueue: ((next: Boolean) -> Unit)? = null,
    /** The opener (a card or the ⋯ button), in root coordinates: the menu opens beside it. */
    anchor: androidx.compose.ui.geometry.Rect? = null,
    /** Play Version…, for a title with more than one version on disk (as Plex). */
    onPlayVersion: (() -> Unit)? = null,
    /** Remove from Continue Watching; given only for a card on Home's Continue Watching row. */
    onRemoveContinue: (() -> Unit)? = null,
    /** Move Earlier, Move Later, Remove from Queue; given only for a card on Home's Queue row. */
    queueMoves: QueueMoves? = null,
    /** Add to Playlist…, for what a playlist can hold (films, episodes, shows). */
    onAddToPlaylist: (() -> Unit)? = null,
    /** Rate…: the profile's own stars; given on a title's page. */
    onRate: (() -> Unit)? = null,
    /** Watch Together: a room for this title, with a code to share (films and episodes). */
    onWatchTogether: (() -> Unit)? = null,
) {
    val first = remember { FocusRequester() }
    // The Apple TV app's floating menu beside the opener (owner, 31 October).
    org.arcmediaserver.android.ui.components.Popover(anchor = anchor, onClose = onClose, title = item.title) {
        org.arcmediaserver.android.ui.components.PopoverItem(
            stringResource(if (item.watched) R.string.action_mark_unwatched else R.string.action_mark_watched),
            if (item.watched) ArcIcons.CircleCheck else ArcIcons.Check, focus = first,
        ) { onWatched(); onClose() }
        if (item.canWatchlist) {
            org.arcmediaserver.android.ui.components.PopoverItem(
                stringResource(if (item.onWatchlist) R.string.action_remove_watchlist else R.string.action_add_watchlist),
                if (item.onWatchlist) ArcIcons.BookmarkCheck else ArcIcons.Bookmark,
            ) { onWatchlist(); onClose() }
        }
        // The web's queue choices, for what can be played (a show queues its episodes).
        if (onQueue != null && item.queueable) {
            org.arcmediaserver.android.ui.components.PopoverItem(stringResource(R.string.queue_play_next), ArcIcons.ListStart) { onQueue(true); onClose() }
            org.arcmediaserver.android.ui.components.PopoverItem(stringResource(R.string.queue_add), ArcIcons.ListVideo) { onQueue(false); onClose() }
        }
        // The web's Watch Together, for what plays (the web starts it from the title's page).
        if (onWatchTogether != null && item.playable) {
            org.arcmediaserver.android.ui.components.PopoverItem(stringResource(R.string.together_title), ArcIcons.Users) { onWatchTogether(); onClose() }
        }
        // The web's Add to Playlist…, after the queue's choices.
        if (onAddToPlaylist != null && item.queueable) {
            org.arcmediaserver.android.ui.components.PopoverItem(stringResource(R.string.playlist_add), ArcIcons.ListOrdered) { onAddToPlaylist() }
        }
        queueMoves?.earlier?.let { f -> org.arcmediaserver.android.ui.components.PopoverItem(stringResource(R.string.queue_move_earlier), ArcIcons.ArrowLeft) { f(); onClose() } }
        queueMoves?.later?.let { f -> org.arcmediaserver.android.ui.components.PopoverItem(stringResource(R.string.queue_move_later), ArcIcons.ArrowRight) { f(); onClose() } }
        queueMoves?.remove?.let { f -> org.arcmediaserver.android.ui.components.PopoverItem(stringResource(R.string.queue_remove), ArcIcons.Close) { f(); onClose() } }
        if (onRemoveContinue != null) {
            org.arcmediaserver.android.ui.components.PopoverItem(stringResource(R.string.action_remove_continue), ArcIcons.Close) { onRemoveContinue(); onClose() }
        }
        if (onPlayVersion != null) {
            org.arcmediaserver.android.ui.components.PopoverItem(stringResource(R.string.details_play_version), ArcIcons.Film) { onPlayVersion() }
        }
        // Rate…, with the stars given so far, as Plex's Rate in its ⋯ menu.
        if (onRate != null) {
            org.arcmediaserver.android.ui.components.PopoverItem(
                stringResource(R.string.rating_rate), ArcIcons.Star,
                detail = item.myRating.takeIf { it > 0 }?.let { org.arcmediaserver.android.data.Ratings.stars(it) },
            ) { onRate() }
        }
        if (item.files.isNotEmpty()) {
            org.arcmediaserver.android.ui.components.PopoverItem(stringResource(R.string.media_info), ArcIcons.Info) { onMediaInfo() }
        }
    }
    LaunchedEffect(Unit) { first.requestWhenReady() }
}

@Composable
internal fun MenuItem(icon: ImageVector?, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    ListItem(
        selected = false,
        onClick = onClick,
        modifier = modifier,
        headlineContent = { Text(label) },
        // A choice that isn't the current one keeps the icon's space, so labels line up.
        leadingContent = { if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) else Spacer(Modifier.size(20.dp)) },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = ArcColors.Foreground,
            contentColor = ArcColors.Foreground,
            focusedContentColor = ArcColors.Background,
        ),
    )
}

/**
 * Media Info, laid out as the web's (web/src/components/SourceFormats.tsx): for each file, labelled
 * rows for its video, audio, subtitles and the file itself; values Arc doesn't know are left out.
 */
@Composable
fun MediaInfoDialog(item: Item, onClose: () -> Unit) {
    val close = remember { FocusRequester() }
    Overlay(720.dp, onBack = onClose) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.media_info), style = MaterialTheme.typography.titleLarge)
                Text(item.title, style = MaterialTheme.typography.bodyMedium, color = ArcColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Button(onClick = onClose, modifier = Modifier.focusRequester(close)) { Text(stringResource(R.string.player_close)) }
        }
        Spacer(Modifier.height(14.dp))
        val several = item.files.size > 1
        LazyColumn(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            items(item.files, key = { it.id }) { FileInfo(it, several) }
        }
    }
    LaunchedEffect(Unit) { close.requestWhenReady() }
}

private fun languageName(code: String?): String? = org.arcmediaserver.android.playback.languageName(code)

private fun fixed(v: Double, places: Int) = String.format(Locale.getDefault(), "%.${places}f", v).let { if (places > 0) it.trimEnd('0').trimEnd('.', ',') else it }

@Composable
private fun rate(bitsPerSecond: Long): String? = when {
    bitsPerSecond <= 0 -> null
    bitsPerSecond >= 1_000_000 -> stringResource(R.string.media_megabits, fixed(bitsPerSecond / 1e6, 1))
    else -> stringResource(R.string.media_kilobits, fixed(bitsPerSecond / 1e3, 0))
}

private fun aspect(r: Double): String? = when {
    r <= 0 -> null
    kotlin.math.abs(r - 4.0 / 3) < 0.02 -> "4:3"
    kotlin.math.abs(r - 16.0 / 9) < 0.02 -> "16:9"
    else -> String.format(Locale.US, "%.2f:1", r)
}

/**
 * Add to Playlist…, as the web's picker: the profile's ordinary playlists (smart ones fill
 * themselves), each with its title count; choosing one adds [item] (a show, its episodes).
 * Playlists are made on the web, so with none there's a note saying so.
 */
@Composable
fun PlaylistPicker(
    item: Item,
    playlists: org.arcmediaserver.android.ui.components.LoadState<List<org.arcmediaserver.android.data.PlaylistInfo>>,
    onPick: (org.arcmediaserver.android.data.PlaylistInfo) -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    val first = remember { FocusRequester() }
    val ready = (playlists as? org.arcmediaserver.android.ui.components.LoadState.Ready)?.value?.filter { it.editable }
    val body = when {
        playlists is org.arcmediaserver.android.ui.components.LoadState.Loading -> stringResource(R.string.playlist_loading)
        playlists is org.arcmediaserver.android.ui.components.LoadState.Failed -> stringResource(R.string.playlist_load_failed)
        ready.isNullOrEmpty() -> stringResource(R.string.playlist_none_editable)
        item.isSeries -> stringResource(R.string.playlist_add_show_to, item.title)
        else -> stringResource(R.string.playlist_add_to, item.title)
    }
    org.arcmediaserver.android.ui.components.Popover(
        anchor = null, onClose = onClose, place = org.arcmediaserver.android.ui.components.PopoverPlace.Center,
        title = stringResource(R.string.playlist_add_title), body = body, width = 360.dp,
    ) {
        if (!ready.isNullOrEmpty()) {
            org.arcmediaserver.android.ui.components.PopoverList(maxHeight = 320.dp) {
                ready.forEachIndexed { i, p ->
                    org.arcmediaserver.android.ui.components.PopoverItem(
                        p.title, ArcIcons.ListVideo,
                        detail = pluralStringResource(R.plurals.playlist_title_count, p.count, p.count),
                        focus = if (i == 0) first else null,
                    ) { onPick(p) }
                }
            }
        } else if (playlists is org.arcmediaserver.android.ui.components.LoadState.Failed) {
            org.arcmediaserver.android.ui.components.PopoverItem(stringResource(R.string.action_retry), ArcIcons.RotateCw, focus = first) { onRetry() }
        } else {
            // Something to hold the focus (Back also closes it).
            org.arcmediaserver.android.ui.components.PopoverItem(stringResource(R.string.player_close), ArcIcons.Close, focus = first) { onClose() }
        }
    }
    LaunchedEffect(playlists is org.arcmediaserver.android.ui.components.LoadState.Ready, playlists is org.arcmediaserver.android.ui.components.LoadState.Failed) { first.requestWhenReady() }
}

/**
 * Rate…: five stars in halves, best first, then Clear Rating when it has one, as the web's
 * StarRating. The current rating is ticked and has the focus; choosing saves it.
 */
@Composable
fun RatingMenu(item: Item, onClose: () -> Unit, onRate: (Int) -> Unit) {
    val first = remember { FocusRequester() }
    val current = item.myRating
    org.arcmediaserver.android.ui.components.Popover(anchor = null, onClose = onClose, place = org.arcmediaserver.android.ui.components.PopoverPlace.Center, title = stringResource(R.string.rating_your), width = 300.dp) {
        org.arcmediaserver.android.ui.components.PopoverList(maxHeight = 380.dp) {
            org.arcmediaserver.android.data.Ratings.choices.forEach { r ->
                org.arcmediaserver.android.ui.components.PopoverItem(
                    org.arcmediaserver.android.data.Ratings.stars(r),
                    icon = if (r == current) ArcIcons.Check else null,
                    detail = stringResource(R.string.rating_out_of_five, org.arcmediaserver.android.data.Ratings.outOfFive(r)),
                    focus = if (r == current || (current == 0 && r == 10)) first else null,
                ) { onRate(r) }
            }
            if (current > 0) {
                org.arcmediaserver.android.ui.components.PopoverItem(stringResource(R.string.rating_clear), ArcIcons.Close) { onRate(0) }
            }
        }
    }
    org.arcmediaserver.android.ui.components.FocusOnOpen(first)
}

/** A title's versions to choose from: its files on disk (a multi-part file counts once). */
fun versionsOf(item: Item): List<MediaFile> = item.files.filter { !it.missing && it.part <= 1 }

/**
 * Plex's Play Version…: each version by its edition (else resolution) with its HDR, codec and
 * size, as the player's Version list; choosing one plays it.
 */
@Composable
fun VersionMenu(item: Item, onClose: () -> Unit, onPlay: (fileId: Long) -> Unit) {
    val first = remember { FocusRequester() }
    org.arcmediaserver.android.ui.components.Popover(anchor = null, onClose = onClose, place = org.arcmediaserver.android.ui.components.PopoverPlace.Center, title = stringResource(R.string.details_choose_version), width = 360.dp) {
        versionsOf(item).forEachIndexed { i, f ->
            val video = f.streams.firstOrNull { it.kind == "video" }
            val res = video?.let { resolutionName(it.width, it.height) }
            val rest = listOfNotNull(*(video?.let { hdrLabels(it).toTypedArray() } ?: emptyArray()), video?.let { codecName(it.codec) }, fileSize(f.size))
            val (name, detail) = if (!f.edition.isNullOrBlank()) f.edition to listOfNotNull(res, *rest.toTypedArray()) else (res ?: f.container?.uppercase() ?: "?") to rest
            org.arcmediaserver.android.ui.components.PopoverItem(name, ArcIcons.Play, detail = detail.joinToString(" · ").ifBlank { null }, focus = if (i == 0) first else null) { onPlay(f.id) }
        }
    }
    org.arcmediaserver.android.ui.components.FocusOnOpen(first)
}

@Composable
private fun fileSize(bytes: Long): String? = when {
    bytes <= 0 -> null
    bytes >= 1_000_000_000_000 -> stringResource(R.string.media_terabytes, fixed(bytes / 1e12, 1))
    bytes >= 1_000_000_000 -> stringResource(R.string.media_gigabytes, fixed(bytes / 1e9, 1))
    else -> stringResource(R.string.media_megabytes, (bytes / 1_000_000).toString())
}

@Composable
private fun FileInfo(file: MediaFile, several: Boolean) {
    val video = file.streams.firstOrNull { it.kind == "video" }
    val audio = file.streams.filter { it.kind == "audio" }
    val subs = file.streams.filter { it.kind == "subtitle" }
    Column {
        if (several) {
            val parts = listOfNotNull(file.edition, video?.let { resolutionName(it.width, it.height) }, if (file.part > 0) stringResource(R.string.media_part, file.part) else null)
            Text(stringResource(R.string.media_version, parts.joinToString(" · ")), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
        }
        if (video != null) {
            val lines = listOf(
                listOfNotNull(resolutionName(video.width, video.height), codecName(video.codec)) + hdrLabels(video),
                listOfNotNull(if (video.width > 0 && video.height > 0) "${video.width}×${video.height}" else null, aspect(video.aspectRatio)),
                listOfNotNull(
                    video.frameRate.takeIf { it > 0 }?.let { stringResource(R.string.media_fps, fixed(it, 3)) },
                    rate(video.bitrate),
                    video.bitDepth.takeIf { it > 0 }?.let { stringResource(R.string.media_bit_depth, it) },
                    video.dolbyVisionProfile.takeIf { it > 0 }?.let { stringResource(R.string.media_dv_profile, it) },
                ),
                listOfNotNull(video.colorPrimaries, video.colorTransfer, video.pixFmt),
            ).map { it.joinToString(" · ") }
            InfoRow(stringResource(R.string.media_video), lines)
        }
        if (audio.isNotEmpty()) {
            InfoRow(stringResource(R.string.player_audio), audio.map { a ->
                listOfNotNull(languageName(a.language), codecName(a.codec, a.profile), channelsName(a.channels), rate(a.bitrate), a.title?.takeIf { t -> !sameLanguage(t, a.language) }).joinToString(" · ")
            }, allBright = true)
        }
        if (subs.isNotEmpty()) {
            InfoRow(stringResource(R.string.player_subtitles), subs.map { s ->
                listOfNotNull(languageName(s.language) ?: stringResource(R.string.media_unknown), codecName(s.codec), if (s.forced) stringResource(R.string.player_forced) else null).joinToString(" · ")
            }, allBright = true)
        }
        InfoRow(
            stringResource(R.string.media_file),
            listOf(
                listOfNotNull(file.container?.uppercase(), fileSize(file.size), rate(file.bitrate)).joinToString(" · "),
                if (file.missing) stringResource(R.string.details_missing) else "",
            ),
        )
        file.path?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = ArcColors.Subtle, modifier = Modifier.padding(top = 6.dp))
        }
        file.probeError?.takeIf { it.isNotBlank() }?.let {
            Text(stringResource(R.string.media_probe_error, it), style = MaterialTheme.typography.bodySmall, color = ArcColors.Warning, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** A label on the left and its lines on the right; left out when there's nothing to say. */
@Composable
private fun InfoRow(label: String, lines: List<String>, allBright: Boolean = false) {
    val shown = lines.filter { it.isNotBlank() }
    if (shown.isEmpty()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = ArcColors.Subtle, modifier = Modifier.width(120.dp).padding(top = 2.dp))
        Column(Modifier.weight(1f)) {
            shown.forEachIndexed { i, line ->
                Text(line, style = MaterialTheme.typography.bodyMedium, color = if (i == 0 || allBright) ArcColors.Foreground else ArcColors.Muted)
            }
        }
    }
}
