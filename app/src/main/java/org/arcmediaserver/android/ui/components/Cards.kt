package org.arcmediaserver.android.ui.components

import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.layout.onPlaced
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.BitmapImage
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.transformations
import org.arcmediaserver.android.R
import org.arcmediaserver.android.data.Item
import org.arcmediaserver.android.data.MediaCard
import org.arcmediaserver.android.data.cardFor
import org.arcmediaserver.android.data.queueEntryId
import org.arcmediaserver.android.ui.theme.ArcColors

/** Turns an address Arc gave for artwork into one to load (the server's, under /api/v1). */
val LocalImageUrl = compositionLocalOf<(String) -> String> { { it } }

/** Holding OK on a card opens its title's menu (Watched, Watchlist), as Plex's long press. */
interface CardMenu {
    /** The title as it is now, with changes made in the app. */
    fun current(item: Item): Item
    /** Opens [item]'s menu beside [anchor] (the card, in root coordinates); [onClose] puts the focus back on the card. */
    fun open(item: Item, onClose: () -> Unit, anchor: androidx.compose.ui.geometry.Rect? = null, inContinue: Boolean = false, queueEntry: String? = null)
    /** Whether [id] was taken out of Continue Watching in this session (Home leaves it off that row). */
    fun offContinue(id: String): Boolean = false
    /** Play Next ([next]) or Add to Queue, with a note of what happened; null when the server keeps no queue. */
    val queue: ((item: Item, next: Boolean) -> Unit)? get() = null
    /** Add to Playlist…: a list of the profile's playlists over the page; [onClose] puts the focus back. */
    fun addToPlaylist(item: Item, onClose: () -> Unit) {}
    /**
     * Watch Together: choose people to invite (or use a code) for the title; [onClose] puts the
     * focus back. Null when unavailable.
     */
    val watchTogether: ((item: Item, onClose: () -> Unit) -> Unit)? get() = null
}

val LocalCardMenu = compositionLocalOf<CardMenu?> { null }

/** Set around Home's Continue Watching row, so its cards' menus offer Remove from Continue Watching. */
val LocalInContinueRow = compositionLocalOf { false }

val CardShape = RoundedCornerShape(10.dp)
val PosterWidth = 132.dp
val ThumbWidth = 220.dp

/**
 * Artwork with a quiet placeholder underneath: the title on a dark gradient, which is what shows
 * for titles Arc couldn't identify (no artwork) and while the picture loads.
 */
@Composable
fun Artwork(
    url: String?,
    label: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    /** Darkened towards the bottom for text over it: drawn into the picture ([BottomShade]). */
    shadeBottom: Boolean = false,
) {
    val resolved = url?.let(LocalImageUrl.current)
    val context = LocalContext.current
    val request = remember(resolved, context, shadeBottom) {
        resolved?.let {
            ImageRequest.Builder(context).data(it).crossfade(false)
                .apply { if (shadeBottom) transformations(BottomShade()) }
                .build()
        }
    }
    var opaque by remember(request) { mutableStateOf(false) }
    val showPlaceholder = !opaque || contentScale != ContentScale.Crop
    val placeholder = if (showPlaceholder) {
        Modifier.background(Brush.linearGradient(listOf(ArcColors.Surface3, ArcColors.Surface)))
    } else Modifier
    Box(modifier.then(placeholder)) {
        if (showPlaceholder && label.isNotEmpty()) {
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                color = ArcColors.Muted,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center).padding(12.dp),
            )
        }
        if (request != null) {
            AsyncImage(
                model = request,
                contentDescription = label.takeIf { !showPlaceholder && it.isNotEmpty() },
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
                onSuccess = {
                    // Opaque artwork covers the placeholder. Transparent artwork still needs it.
                    opaque = (it.result.image as? BitmapImage)?.bitmap?.hasAlpha() == false
                },
            )
        }
        // Until a shaded picture covers it, the placeholder gets the same shading.
        if (shadeBottom && showPlaceholder) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.5f).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))))
        }
    }
}

/**
 * A focusable card: it grows a little, takes a white outline and a dark shadow when focused, as
 * cards do in the Apple TV app (no coloured glow, owner, 31 October).
 */
@Composable
fun FocusCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: (Boolean) -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.onFocusChanged { onFocused(it.isFocused) },
        shape = CardDefaults.shape(CardShape),
        scale = CardDefaults.scale(focusedScale = 1.08f),
        border = CardDefaults.border(focusedBorder = Border(BorderStroke(3.dp, ArcColors.Foreground), shape = CardShape)),
        glow = CardDefaults.glow(focusedGlow = Glow(elevationColor = Color.Black.copy(alpha = 0.55f), elevation = 18.dp)),
        // No fill of its own: every card's artwork (or its placeholder) covers the whole card, and
        // on a Chromecast every card painted twice costs frame time.
        colors = CardDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
    ) {
        Box(Modifier.fillMaxWidth()) { content() }
    }
}

/**
 * A Backdrops card's art, as the web's (web/src/components/ArcArtwork.tsx): the background art,
 * darkened towards the bottom, with the title's logo (or its name) centred low on it.
 */
@Composable
private fun BackdropArt(card: MediaCard) {
    val title = cardTitle(card)
    Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f)) {
        // Darkened towards the bottom for the logo, drawn into the picture once.
        if (card.item.isEpisode) EpisodeArtwork(card.item, card.backdrop, label = "", modifier = Modifier.fillMaxSize(), shadeBottom = true)
        else Artwork(url = card.backdrop, label = "", modifier = Modifier.fillMaxSize(), shadeBottom = true)
        var failed by remember(card.logo) { mutableStateOf(false) }
        val logo = card.logo
        val place = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp)
        if (logo != null && !failed) {
            AsyncImage(
                model = LocalImageUrl.current(logo),
                contentDescription = title,
                contentScale = ContentScale.Fit,
                onError = { failed = true },
                modifier = place.fillMaxWidth(0.72f).fillMaxHeight(0.26f),
            )
        } else {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = place.padding(horizontal = 12.dp))
        }
    }
}

/** How far through a title is, along the bottom of its card. */
@Composable
fun BoxScope.ProgressLine(progress: Float?) {
    if (progress == null) return
    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color.Black.copy(alpha = 0.55f))) {
        Box(Modifier.fillMaxWidth(progress).height(4.dp).background(ArcColors.Accent))
    }
}

/** A tick in the corner for something watched; the number of episodes left for a show. */
@Composable
fun BoxScope.WatchBadge(item: Item, newEpisodes: Int? = null) {
    val unwatched = newEpisodes ?: item.unwatchedCount ?: 0
    when {
        (newEpisodes != null || item.isSeries) && unwatched > 0 -> Box(
            Modifier.align(Alignment.TopEnd).padding(6.dp).clip(RoundedCornerShape(6.dp)).background(ArcColors.AccentStrong).padding(horizontal = 7.dp, vertical = 2.dp),
        ) { Text(unwatched.toString(), style = MaterialTheme.typography.labelMedium, color = ArcColors.OnAccent) }
        item.userData?.played == true -> Box(
            Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).clip(CircleShape).background(ArcColors.AccentStrong),
            contentAlignment = Alignment.Center,
        ) { Icon(ArcIcons.Check, contentDescription = stringResource(R.string.watched), modifier = Modifier.size(15.dp), tint = ArcColors.OnAccent) }
    }
}

/** Recently Added captions follow the web: season, batch count or episode code. */
@Composable
fun cardSubtitle(card: MediaCard): String? {
    val item = card.item
    return when {
        card.sub != null -> card.sub
        card.seasonNumber != null -> stringResource(R.string.season_number, card.seasonNumber)
        card.newEpisodes > 0 -> pluralStringResource(R.plurals.new_episodes, card.newEpisodes, card.newEpisodes)
        item.isEpisode -> episodeCode(item)
        else -> item.yearAndEdition
    }
}

/**
 * An episode's number, as every Arc app writes it: "S1 · E4", or "Special 3" for season 0. Only
 * "·" separates; no leading zeros.
 */
@Composable
fun episodeCode(season: Int, episode: Int): String =
    if (season == 0) stringResource(R.string.episode_special, episode) else stringResource(R.string.episode_code, season, episode)

@Composable
fun episodeCode(item: Item): String? {
    val s = item.seasonNumber ?: return null
    val e = item.episodeNumber ?: return stringResource(R.string.season_number, s)
    // An episode outside the show's order (Arc's 0 and 0) has no number to show.
    if (s == 0 && e == 0) return null
    return episodeCode(s, e)
}

/** Where the episode itself is the subject: "S1 · E4 · Pilot", or the name alone without a number. */
@Composable
fun episodeWithTitle(item: Item): String {
    val code = episodeCode(item) ?: return item.title
    return if (item.title.isBlank()) code else stringResource(R.string.episode_with_title, code, item.title)
}

/** A card's title: the show's for an episode or a season, as Plex labels them on Home. */
fun cardTitle(card: MediaCard): String = if (card.item.isEpisode) card.item.seriesTitle ?: card.item.title else card.item.title

/** A label over a card's top corner, as the web's folder tag. */
@Composable
private fun BoxScope.CornerTag(label: String) {
    androidx.compose.foundation.layout.Row(
        Modifier.align(Alignment.TopStart).padding(8.dp).background(Color.Black.copy(alpha = 0.6f), androidx.compose.foundation.shape.RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.tv.material3.Icon(ArcIcons.Folder, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White)
    }
}

/** Four posters two by two (a playlist's picture, as the web's PlaylistCover). */
@Composable
private fun Mosaic(urls: List<String>, modifier: Modifier) {
    Column(modifier) {
        for (row in 0..1) {
            androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().weight(1f)) {
                for (col in 0..1) Artwork(urls[row * 2 + col], label = "", modifier = Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable
fun MediaCardView(
    card: MediaCard,
    landscape: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Home shows no captions: the overview above names whatever has focus, as Plex does. */
    caption: Boolean = true,
    width: Dp? = null,
    /** Home's Backdrops look: a squarer card with the title's background art and its logo. */
    backdrop: Boolean = false,
    /** Home's season posters: an episode's or new season's own poster rather than the show's. */
    seasonPoster: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    // Changes made in the app (the card menu, a title's page) show straight away.
    val menu = LocalCardMenu.current
    val inContinue = LocalInContinueRow.current
    val changed = menu?.current(card.item)
    @Suppress("NAME_SHADOWING")
    val card = if (changed == null || changed === card.item) card else card.copy(item = changed, progress = cardFor(changed).progress)
    val cardWidth: Dp = width ?: if (landscape) ThumbWidth else PosterWidth
    Column(modifier.width(cardWidth)) {
        val self = remember { androidx.compose.ui.focus.FocusRequester() }
        // Where the card is, so its menu opens beside it.
        val bounds = remember { arrayOfNulls<androidx.compose.ui.geometry.Rect>(1) }
        FocusCard(
            onClick = onClick,
            modifier = Modifier.focusRequester(self).onGloballyPositioned { bounds[0] = it.boundsInRoot() },
            onFocused = { focused = it },
            onLongClick = menu?.takeIf { card.item.kind == "movie" || card.item.kind == "series" || card.item.kind == "episode" }?.let { m -> { m.open(card.item, { runCatching { self.requestFocus() } }, bounds[0], inContinue, card.queueEntryId()) } },
        ) {
            if (backdrop) {
                BackdropArt(card)
            } else if (!landscape && card.mosaic.size >= 4) {
                Mosaic(card.mosaic, Modifier.fillMaxWidth().aspectRatio(2f / 3f))
            } else if (landscape && card.item.isEpisode) {
                // An episode's still keeps spoilers back while the profile hides them.
                EpisodeArtwork(card.item, card.thumb, label = cardTitle(card), modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            } else {
                Artwork(
                    url = if (landscape) card.thumb else if (seasonPoster) card.seasonPoster ?: card.poster else card.poster,
                    label = cardTitle(card),
                    modifier = Modifier.fillMaxWidth().aspectRatio(if (landscape) 16f / 9f else 2f / 3f),
                )
            }
            card.tag?.let { CornerTag(it) }
            WatchBadge(card.item, newEpisodes = card.newEpisodes.takeIf { card.seasonNumber != null })
            ProgressLine(card.progress)
        }
        if (!caption) return@Column
        Spacer(Modifier.height(8.dp))
        Text(
            cardTitle(card),
            style = MaterialTheme.typography.titleSmall,
            color = if (focused) ArcColors.Foreground else ArcColors.Foreground.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // Under the show's name an episode's card shows only its number, never its name.
        val line = cardSubtitle(card)
        if (!line.isNullOrEmpty()) {
            Text(line, style = MaterialTheme.typography.bodySmall, color = ArcColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * A titled row of cards (search results, a title's cast and related rows): the heading, then a
 * row that leaves room for the focused card to grow. [start] lines it up with the screen's text.
 * [leadingInset] moves the start of the scrolling viewport into that clearance.
 */
@Composable
fun Shelf(
    title: String,
    start: Dp = 48.dp,
    modifier: Modifier = Modifier,
    leadingInset: Dp = 0.dp,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = start, bottom = 8.dp))
        // Scrolling keeps the row's own start beside the card it brings in, so coming back to the
        // first card puts it where it began.
        // Entering the row lands on the card last used in it, or its first (RowFocus).
        val row = androidx.compose.foundation.lazy.rememberLazyListState()
        val focus = rememberRowFocus(row)
        @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides minimalBringIntoView(start - leadingInset, after = start)) {
            androidx.compose.foundation.lazy.LazyRow(
                state = row,
                modifier = (if (leadingInset > 0.dp) Modifier.padding(start = leadingInset).clipToBounds() else Modifier).then(focus.modifier()),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = start - leadingInset, end = start, top = 10.dp, bottom = 10.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(20.dp),
            ) { focus.wrap(this).content() }
        }
    }
}

/** A person: a round picture (or initials), their name and a line under it (a character, how many titles). */
@Composable
fun PersonCard(name: String, image: String?, sub: String?, onClick: () -> Unit) {
    Column(Modifier.width(112.dp).revealOnFocus(), horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.tv.material3.Surface(
            onClick = onClick,
            modifier = Modifier.size(104.dp),
            shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(CircleShape),
            scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
            border = androidx.tv.material3.ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, ArcColors.Foreground), shape = CircleShape)),
            colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(containerColor = ArcColors.Surface, focusedContainerColor = ArcColors.Surface),
        ) {
            Artwork(image, label = initials(name), modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(10.dp))
        Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        if (!sub.isNullOrBlank()) {
            Text(sub, style = MaterialTheme.typography.bodySmall, color = ArcColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}

private fun initials(name: String) = name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }

/**
 * Pill tabs (a show's seasons, a library's Recommended / titles / Collections): moving the focus
 * along them switches what's shown, as Google TV's tabs do. [onClick] is OK on a tab (a season's
 * menu); [selectedTab] goes on the selected tab (to return the focus to it, or to place a menu).
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PillTabs(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onClick: ((Int) -> Unit)? = null,
    selectedTab: Modifier = Modifier,
    /** Space before the first pill, inside the scrolling row (so pills scroll under it, not clipped at it). */
    start: Dp = 0.dp,
    /** Space after the last pill, inside the scrolling row. */
    end: Dp = 0.dp,
) {
    // Standalone header-style pills: no holder, 10dp between pills (owner, server #499).
    // One focus group, so a focusRestorer in [modifier] decides which pill focus enters on.
    // More pills than fit (a show with many seasons) scroll sideways, as far as the focused one
    // needs; before, the ones past the edge were squeezed to nothing, and focus moving onto one
    // couldn't find its way back up.
    val spec = minimalBringIntoView(start, after = end)
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides spec,
    ) {
    val scroll = androidx.compose.foundation.rememberScrollState()
    // Where the chosen pill and the visible stretch of the row are, to show the chosen pill when
    // the row isn't focused (a page opened on Season 38 from Home): read only by the effect below.
    val viewport = remember { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(2) }
    val placedFor = remember { intArrayOf(-1) } // which pill viewport[1] is
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(selected) {
        // Wait for the row's first layout, when the pills' places and how far it scrolls are known.
        while (placedFor[0] != selected || viewport[0]?.isAttached != true || viewport[1]?.isAttached != true || scroll.maxValue == Int.MAX_VALUE) {
            androidx.compose.runtime.withFrameNanos { }
        }
        if (focused) return@LaunchedEffect // Focus brings its pill into view itself.
        val pill = viewport[0]!!.localBoundingBoxOf(viewport[1]!!, clipBounds = false)
        // The same distance focus would scroll, so the row sits as if you'd moved along it to here.
        val distance = spec.calculateScrollDistance(pill.left, pill.width, viewport[0]!!.size.width.toFloat())
        if (distance != 0f) scroll.scrollBy(distance)
    }
    // The space after the last pill only when they scroll: otherwise the row ends at its last
    // pill, so whatever follows it (a season's warning) sits right after.
    val endSpace = if (scroll.maxValue > 0) end else 0.dp
    // The scrolling area reaches a little past the row on both sides (the pills stay where they
    // were), so a focused pill, drawn a little larger, isn't clipped at the row's ends.
    val slack = 8.dp
    Row(
        modifier.onFocusChanged { focused = it.hasFocus }.focusGroup().selectableGroup()
            .layout { measurable, constraints ->
                val px = slack.roundToPx()
                val placeable = measurable.measure(constraints.offset(horizontal = 2 * px))
                layout((placeable.width - 2 * px).coerceAtLeast(0), placeable.height) { placeable.place(-px, 0) }
            }
            .onPlaced { viewport[0] = it }
            .horizontalScroll(scroll).padding(start = start + slack, end = endSpace + slack),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val chosen = i == selected
            androidx.tv.material3.Button(
                onClick = { onClick?.invoke(i) },
                modifier = (if (chosen) selectedTab.onPlaced { viewport[1] = it; placedFor[0] = i } else Modifier)
                    .height(36.dp)
                    .onFocusChanged { if (it.isFocused) onSelect(i) }
                    .semantics { this.selected = chosen },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                scale = androidx.tv.material3.ButtonDefaults.scale(focusedScale = 1.04f),
                colors = androidx.tv.material3.ButtonDefaults.colors(
                    containerColor = if (chosen) ArcColors.SelectedPill else ArcColors.Pill,
                    contentColor = ArcColors.Foreground,
                    focusedContainerColor = Color.White,
                    focusedContentColor = Color.Black,
                ),
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
    }
}
