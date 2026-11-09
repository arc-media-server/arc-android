package org.arcmediaserver.android.ui.components

import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
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
fun Artwork(url: String?, label: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val resolve = LocalImageUrl.current
    Box(modifier.background(Brush.linearGradient(listOf(ArcColors.Surface3, ArcColors.Surface)))) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = ArcColors.Muted,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.Center).padding(12.dp),
        )
        if (url != null) {
            AsyncImage(model = resolve(url), contentDescription = null, contentScale = contentScale, modifier = Modifier.fillMaxSize())
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
        colors = CardDefaults.colors(containerColor = ArcColors.Surface, focusedContainerColor = ArcColors.Surface),
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
        Artwork(url = card.backdrop, label = "", modifier = Modifier.fillMaxSize())
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.5f).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))))
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
fun BoxScope.WatchBadge(item: Item) {
    val unwatched = item.unwatchedCount ?: 0
    when {
        item.isSeries && unwatched > 0 -> Box(
            Modifier.align(Alignment.TopEnd).padding(6.dp).clip(RoundedCornerShape(6.dp)).background(ArcColors.AccentStrong).padding(horizontal = 7.dp, vertical = 2.dp),
        ) { Text(unwatched.toString(), style = MaterialTheme.typography.labelMedium, color = ArcColors.OnAccent) }
        item.userData?.played == true -> Box(
            Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).clip(CircleShape).background(ArcColors.AccentStrong),
            contentAlignment = Alignment.Center,
        ) { Icon(ArcIcons.Check, contentDescription = stringResource(R.string.watched), modifier = Modifier.size(15.dp), tint = ArcColors.OnAccent) }
    }
}

/** The line under a card: "S1 · E3" for an episode, "Season 2 · 3 new episodes", or the year. */
@Composable
fun cardSubtitle(card: MediaCard): String? {
    val item = card.item
    return when {
        card.sub != null -> card.sub
        card.seasonNumber != null -> stringResource(R.string.season_number, card.seasonNumber) + " · " +
            pluralStringResource(R.plurals.new_episodes, card.newEpisodes, card.newEpisodes)
        item.isEpisode -> episodeCode(item)
        else -> item.year?.toString()
    }
}

@Composable
fun episodeCode(item: Item): String? {
    val s = item.seasonNumber ?: return null
    val e = item.episodeNumber ?: return stringResource(R.string.season_number, s)
    return stringResource(R.string.episode_code, s, e)
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
            } else {
                Artwork(
                    url = if (landscape) card.thumb else if (seasonPoster) card.seasonPoster ?: card.poster else card.poster,
                    label = cardTitle(card),
                    modifier = Modifier.fillMaxWidth().aspectRatio(if (landscape) 16f / 9f else 2f / 3f),
                )
            }
            card.tag?.let { CornerTag(it) }
            WatchBadge(card.item)
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
        val sub = cardSubtitle(card)
        val line = if (card.item.isEpisode && landscape) listOfNotNull(sub, card.item.title.takeIf { it != cardTitle(card) }).joinToString(" · ") else sub
        if (!line.isNullOrEmpty()) {
            Text(line, style = MaterialTheme.typography.bodySmall, color = ArcColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * A titled row of cards (search results, a title's cast and related rows): the heading, then a
 * row that leaves room for the focused card to grow. [start] lines it up with the screen's text.
 */
@Composable
fun Shelf(title: String, start: Dp = 48.dp, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = start, bottom = 8.dp))
        // Scrolling keeps the row's own start beside the card it brings in, so coming back to the
        // first card puts it where it began.
        @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides minimalBringIntoView(start)) {
            androidx.compose.foundation.lazy.LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = start, vertical = 10.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(20.dp),
                content = content,
            )
        }
    }
}

/** A person: a round picture (or initials), their name and a line under it (a character, how many titles). */
@Composable
fun PersonCard(name: String, image: String?, sub: String?, onClick: () -> Unit) {
    Column(Modifier.width(112.dp), horizontalAlignment = Alignment.CenterHorizontally) {
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
 * along them switches what's shown, as Google TV's tabs do.
 */
@Composable
fun PillTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    // The Apple TV app's segmented control: a glass pill holding the tabs, the current one lighter
    // and the focused one white (owner, 1 November).
    androidx.tv.material3.TabRow(
        selectedTabIndex = selected,
        modifier = modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f)).padding(3.dp),
        containerColor = Color.Transparent,
        indicator = { tabPositions, doesTabRowHaveFocus ->
            androidx.tv.material3.TabRowDefaults.PillIndicator(
                currentTabPosition = tabPositions[selected],
                doesTabRowHaveFocus = doesTabRowHaveFocus,
                activeColor = ArcColors.Foreground,
                inactiveColor = Color.White.copy(alpha = 0.18f),
            )
        },
    ) {
        labels.forEachIndexed { i, label ->
            Tab(
                selected = i == selected,
                onFocus = { onSelect(i) },
                colors = androidx.tv.material3.TabDefaults.pillIndicatorTabColors(
                    contentColor = ArcColors.Foreground.copy(alpha = 0.65f),
                    selectedContentColor = ArcColors.Foreground,
                    focusedContentColor = ArcColors.Background,
                    focusedSelectedContentColor = ArcColors.Background,
                ),
            ) {
                Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 15.dp, vertical = 6.dp))
            }
        }
    }
}
