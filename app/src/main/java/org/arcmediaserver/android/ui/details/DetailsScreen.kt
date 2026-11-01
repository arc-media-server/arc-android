package org.arcmediaserver.android.ui.details

import androidx.compose.foundation.focusable

import androidx.compose.ui.layout.boundsInRoot

import androidx.compose.ui.layout.onGloballyPositioned

import org.arcmediaserver.android.ui.components.MediaCardView
import org.arcmediaserver.android.ui.components.PersonCard
import org.arcmediaserver.android.ui.components.Shelf
import org.arcmediaserver.android.ui.components.PillTabs
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import org.arcmediaserver.android.ui.components.minimalBringIntoView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.arcmediaserver.android.R
import org.arcmediaserver.android.ui.components.requestWhenReady
import org.arcmediaserver.android.data.Item
import org.arcmediaserver.android.data.Season
import org.arcmediaserver.android.data.cardFor
import org.arcmediaserver.android.ui.arcViewModel
import org.arcmediaserver.android.ui.browse.runtimeText
import org.arcmediaserver.android.ui.components.ArcIcons
import org.arcmediaserver.android.ui.components.Artwork
import org.arcmediaserver.android.ui.components.FocusCard
import org.arcmediaserver.android.ui.components.Loaded
import org.arcmediaserver.android.ui.components.ProgressLine
import org.arcmediaserver.android.ui.components.RoundIconButton
import org.arcmediaserver.android.ui.components.ThumbWidth
import org.arcmediaserver.android.ui.components.WatchBadge
import org.arcmediaserver.android.ui.components.episodeCode
import org.arcmediaserver.android.ui.theme.ArcColors

@Composable
fun DetailsRoute(
    itemId: String,
    onPlay: (Item, Long) -> Unit,
    onOpen: (String) -> Unit,
    episodeId: String? = null,
    onPerson: (Long) -> Unit = {},
    onExtra: (org.arcmediaserver.android.data.Extra) -> Unit = {},
    onPlayQueued: (Item, String) -> Unit = { _, _ -> },
    onPlayVersion: (Item, Long, Long) -> Unit = { _, _, _ -> },
) {
    val vm = arcViewModel { DetailsViewModel(it, itemId, episodeId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val canShuffle by vm.canShuffle.collectAsStateWithLifecycle()
    var resumed by remember { mutableStateOf(false) }
    // Coming back from the player: show the new resume point and watched state.
    LifecycleResumeEffect(Unit) {
        if (resumed) vm.load(quiet = true)
        resumed = true
        onPauseOrDispose { }
    }
    val problem by vm.problem.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().background(ArcColors.Background)) {
        Loaded(state, onRetry = { vm.load() }) { details ->
            DetailsScreen(
                details, onPlay, onOpen,
                actions = TitleActions(
                    onWatched = vm::toggleWatched, onWatchlist = vm::toggleWatchlist, problem = problem, onProblemShown = vm::problemShown,
                    onShuffle = if (canShuffle) { { vm.shuffle(onPlayQueued) } } else null,
                    onPlayVersion = onPlayVersion,
                ),
                onPerson = onPerson,
                onExtra = onExtra,
            )
        }
    }
}

/** What a title's Watched, Watchlist and ⋯ buttons do, and the last one that failed. */
data class TitleActions(
    val onWatched: () -> Unit = {},
    val onWatchlist: () -> Unit = {},
    val problem: ActionProblem? = null,
    val onProblemShown: () -> Unit = {},
    /** A show's Shuffle; null where it can't be shuffled. */
    val onShuffle: (() -> Unit)? = null,
    /** Play Version…: plays the title from a point with a chosen file. */
    val onPlayVersion: ((Item, Long, Long) -> Unit)? = null,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DetailsScreen(
    details: Details,
    onPlay: (Item, Long) -> Unit,
    onOpen: (String) -> Unit,
    actions: TitleActions = TitleActions(),
    initialPanel: DetailsPanel = DetailsPanel.None,
    onPerson: (Long) -> Unit = {},
    onExtra: (org.arcmediaserver.android.data.Extra) -> Unit = {},
) {
    val item = details.item
    val primary = remember { FocusRequester() }
    val more = remember { FocusRequester() }
    val episodeFocus = remember { FocusRequester() }
    val page = rememberLazyListState()
    var panel by remember { mutableStateOf(initialPanel) }
    // The Apple TV app's page: the artwork fills the screen and dims once the page scrolls.
    val scrolled by remember { androidx.compose.runtime.derivedStateOf { page.firstVisibleItemIndex > 0 || page.firstVisibleItemScrollOffset > 40 } }
    var moreAnchor by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    Box(Modifier.fillMaxSize().background(ArcColors.Background)) {
        HeroBackdrop(item, scrolled)
        val scope = rememberCoroutineScope()
        // TV lists normally scroll whatever has focus to a pivot a third of the way down, which
        // pushed the title off the top as soon as Play was focused. Here the page only scrolls
        // what's needed to show the focused thing, and the header always shows whole (as Plex).
        CompositionLocalProvider(LocalBringIntoViewSpec provides minimalBringIntoView()) {
            LazyColumn(Modifier.fillMaxSize(), state = page, contentPadding = PaddingValues(bottom = 40.dp)) {
                item {
                    Box(
                        Modifier.fillParentMaxHeight(0.88f).onFocusChanged { if (it.hasFocus) scope.launch { page.animateScrollToItem(0) } },
                        contentAlignment = Alignment.BottomStart,
                    ) {
                        Header(details, onPlay, onOpen, primary, actions, more, onMore = { at -> moreAnchor = at; panel = DetailsPanel.More })
                    }
                }
                if (item.isSeries) {
                    // The seasons show whole while they have focus, so episode names and
                    // details under the pictures aren't cut off at the bottom.
                    item {
                        Box(Modifier.onFocusChanged { if (it.hasFocus) scope.launch { page.animateScrollToItem(1) } }) {
                            Seasons(details, onPlay, episodeFocus)
                        }
                    }
                }
                // Under the title, as the web's page: the cast, the collections it's in, More
                // Like This, then extras.
                val cast = item.credits.filter { it.role == "actor" }
                if (cast.isNotEmpty()) {
                    item(key = "cast") {
                        Box(Modifier.padding(top = 28.dp)) {
                            Shelf(stringResource(R.string.details_cast), start = PageStart) {
                                items(cast, key = { it.personId }) { c -> PersonCard(c.name, c.image, c.character) { onPerson(c.personId) } }
                            }
                        }
                    }
                }
                details.related.forEach { row ->
                    item(key = row.key) {
                        Box(Modifier.padding(top = 20.dp)) {
                            Shelf(row.title ?: stringResource(R.string.details_more_like_this), start = PageStart) {
                                items(row.items, key = { it.id }) { t -> MediaCardView(cardFor(t), landscape = row.landscape, onClick = { onOpen(t.id) }) }
                            }
                        }
                    }
                }
                if (item.extras.isNotEmpty()) {
                    item(key = "extras") {
                        Box(Modifier.padding(top = 20.dp)) {
                            Shelf(stringResource(R.string.details_extras), start = PageStart) {
                                items(item.extras, key = { it.id }) { e -> ExtraCard(e) { onExtra(e) } }
                            }
                        }
                    }
                }
                item(key = "info") { InfoSection(item) }
            }
        }
        val closePanel = {
            panel = DetailsPanel.None
            more.requestFocus()
            Unit
        }
        when (panel) {
            DetailsPanel.None -> Unit
            DetailsPanel.More -> {
                val queue = org.arcmediaserver.android.ui.components.LocalCardMenu.current?.queue
                MoreMenu(
                    item, actions.onWatched, actions.onWatchlist, onMediaInfo = { panel = DetailsPanel.MediaInfo }, onClose = closePanel,
                    onQueue = queue?.let { q -> { next: Boolean -> q(item, next) } },
                    anchor = moreAnchor,
                    onPlayVersion = if (!item.isSeries && actions.onPlayVersion != null && versionsOf(item).size > 1) { { panel = DetailsPanel.Version } } else null,
                )
            }
            DetailsPanel.MediaInfo -> MediaInfoDialog(item, onClose = closePanel)
            DetailsPanel.Version -> VersionMenu(item, onClose = closePanel) { file -> panel = DetailsPanel.None; actions.onPlayVersion?.invoke(item, item.resumeMs, file) }
        }
    }
    LaunchedEffect(item.id) {
        if (initialPanel != DetailsPanel.None) return@LaunchedEffect
        // Opened from an episode's card: land on that episode in its show, as Plex does.
        if (details.focus != null) {
            page.scrollToItem(1)
            if (episodeFocus.requestWhenReady()) return@LaunchedEffect
        }
        primary.requestWhenReady()
    }
}

@Composable
private fun Header(
    details: Details,
    onPlay: (Item, Long) -> Unit,
    onOpen: (String) -> Unit,
    primary: FocusRequester,
    actions: TitleActions,
    more: FocusRequester,
    onMore: (androidx.compose.ui.geometry.Rect?) -> Unit,
) {
    val item = details.item
    // As the Apple TV app: the logo low on the artwork, then the Play button and round buttons on
    // one line with the description and details beside them (owner, 31 October).
    Column(Modifier.padding(start = PageStart, end = PageStart, bottom = 8.dp)) {
        org.arcmediaserver.android.ui.browse.TitleLogo(item, maxHeight = 100.dp, maxWidth = 380.dp, textStyle = MaterialTheme.typography.displayMedium)
        if (item.isEpisode) {
            Spacer(Modifier.height(8.dp))
            Text(listOfNotNull(episodeCode(item), item.title).joinToString(" · "), style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(18.dp))
        var focusedLabel by remember { mutableStateOf<String?>(null) }
        Row(verticalAlignment = Alignment.Top) {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    val round = Color.White.copy(alpha = 0.16f)
                    // A button's name shows below the row while it has the focus.
                    fun labelled(label: String, base: Modifier = Modifier) = base.onFocusChanged {
                        focusedLabel = if (it.isFocused) label else focusedLabel.takeIf { l -> l != label }
                    }
                    val target = if (item.isSeries) details.next else item
                    if (target != null) {
                        val resume = target.resumeMs
                        val code = if (item.isSeries) episodeCode(target) else null
                        val label = when {
                            resume > 0 && code != null -> stringResource(R.string.action_resume_episode, code)
                            resume > 0 -> stringResource(R.string.action_resume, clock(resume))
                            code != null -> stringResource(R.string.action_play_episode, code)
                            else -> stringResource(R.string.action_play)
                        }
                        PlayPill(label, Modifier.focusRequester(primary)) { onPlay(target, resume) }
                        if (resume > 0) {
                            val fromStart = stringResource(R.string.action_play_from_start)
                            RoundIconButton(ArcIcons.RotateCcw, fromStart, { onPlay(target, 0) }, size = 44.dp, containerColor = round, modifier = labelled(fromStart))
                        }
                    }
                    // Shuffle, after Play as on the web's show page.
                    if (item.isSeries && actions.onShuffle != null && details.seasons.any { s -> s.episodes.any { it.available } }) {
                        val shuffle = stringResource(R.string.action_shuffle)
                        RoundIconButton(ArcIcons.Shuffle, shuffle, { actions.onShuffle.invoke() }, size = 44.dp, containerColor = round, modifier = labelled(shuffle))
                    }
                    if (item.isEpisode && item.parentId != null) {
                        val show = stringResource(R.string.action_go_to_show)
                        RoundIconButton(ArcIcons.Tv, show, { onOpen(item.parentId) }, size = 44.dp, containerColor = round, modifier = labelled(show))
                    }
                    val watchedLabel = stringResource(if (item.watched) R.string.action_mark_unwatched else R.string.action_mark_watched)
                    RoundIconButton(
                        ArcIcons.CircleCheck, watchedLabel, actions.onWatched,
                        size = 44.dp, containerColor = round, iconTint = if (item.watched) ArcColors.Accent else null,
                        modifier = labelled(watchedLabel),
                    )
                    if (item.canWatchlist) {
                        val watchlistLabel = stringResource(if (item.onWatchlist) R.string.action_remove_watchlist else R.string.action_add_watchlist)
                        RoundIconButton(
                            if (item.onWatchlist) ArcIcons.BookmarkCheck else ArcIcons.Bookmark, watchlistLabel, actions.onWatchlist,
                            size = 44.dp, containerColor = round, iconTint = if (item.onWatchlist) ArcColors.Accent else null,
                            modifier = labelled(watchlistLabel),
                        )
                    }
                    val moreLabel = stringResource(R.string.action_more)
                    var moreBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
                    RoundIconButton(
                        ArcIcons.More, moreLabel, { onMore(moreBounds) }, size = 44.dp, containerColor = round,
                        modifier = labelled(moreLabel, Modifier.focusRequester(more).onGloballyPositioned { moreBounds = it.boundsInRoot() }),
                    )
                }
                // The focused round button's name, or what couldn't be saved.
                Box(Modifier.height(28.dp).padding(top = 8.dp)) {
                    val problem = actions.problem
                    if (problem != null) {
                        Text(
                            stringResource(if (problem == ActionProblem.Watched) R.string.action_watched_failed else R.string.action_watchlist_failed),
                            style = MaterialTheme.typography.labelLarge, color = ArcColors.Warning,
                        )
                        LaunchedEffect(problem) {
                            kotlinx.coroutines.delay(5_000)
                            actions.onProblemShown()
                        }
                    } else {
                        focusedLabel?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = ArcColors.Foreground.copy(alpha = 0.8f)) }
                    }
                }
            }
            Spacer(Modifier.width(24.dp))
            Column(Modifier.weight(1f).padding(top = 2.dp)) {
                item.overview?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = ArcColors.Foreground.copy(alpha = 0.85f), maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                val unwatched = item.unwatchedCount ?: 0
                val meta = listOfNotNull(
                    item.genres.firstOrNull(),
                    item.year?.toString()?.takeIf { !item.isEpisode },
                    item.runtimeMin?.takeIf { it > 0 && !item.isSeries }?.let { runtimeText(it) },
                    if (item.isSeries) pluralStringResource(R.plurals.episodes_left, unwatched, unwatched).takeIf { unwatched > 0 } else null,
                    item.rating?.takeIf { it > 0 }?.let { "★ %.1f".format(it) },
                ).filter { it.isNotBlank() }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (meta.isNotEmpty()) {
                        Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = ArcColors.Foreground.copy(alpha = 0.7f), maxLines = 1)
                        Spacer(Modifier.width(10.dp))
                    }
                    org.arcmediaserver.android.ui.components.FormatBadges(org.arcmediaserver.android.ui.components.formatLabels(item))
                }
            }
        }
    }
}

/** The Play button: a white pill, as the Apple TV app's. */
@Composable
private fun PlayPill(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = modifier.height(44.dp), contentPadding = PaddingValues(start = 18.dp, end = 22.dp),
        scale = ButtonDefaults.scale(focusedScale = 1.06f),
        colors = ButtonDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.9f),
            contentColor = Color.Black,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
    ) {
        Icon(ArcIcons.Play, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(label, style = MaterialTheme.typography.titleSmall)
    }
}

/** The full-screen artwork behind the page, shaded at the bottom and left, dimmed once scrolled. */
@Composable
internal fun HeroBackdrop(item: Item, dim: Boolean) {
    val url = org.arcmediaserver.android.ui.browse.backdropOf(item)
    val resolve = org.arcmediaserver.android.ui.components.LocalImageUrl.current
    val dimAlpha by androidx.compose.animation.core.animateFloatAsState(if (dim) 0.82f else 0f, label = "dim")
    Box(Modifier.fillMaxSize()) {
        if (url != null) {
            coil3.compose.AsyncImage(model = resolve(url), contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        val bg = ArcColors.Background
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 0.45f to Color.Transparent, 0.78f to bg.copy(alpha = 0.75f), 1f to bg.copy(alpha = 0.95f))))
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to bg.copy(alpha = 0.7f), 0.4f to bg.copy(alpha = 0.25f), 0.6f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(bg.copy(alpha = dimAlpha)))
    }
}

/** The Apple TV app's Information block at the foot of a title's page. */
@Composable
private fun InfoSection(item: Item) {
    fun names(role: String, n: Int = 4) = item.credits.filter { it.role == role }.take(n).joinToString(", ") { it.name }
    val released = item.releaseDate?.let { d ->
        runCatching {
            java.time.LocalDate.parse(d).format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.LONG))
        }.getOrNull()
    } ?: item.year?.toString()
    val rows = listOf(
        R.string.details_info_genre to item.genres.joinToString(", "),
        R.string.details_info_released to released.orEmpty(),
        R.string.details_info_runtime to (item.runtimeMin?.takeIf { it > 0 && !item.isSeries }?.let { runtimeText(it) } ?: ""),
        R.string.details_info_rated to item.contentRating.orEmpty(),
        R.string.details_info_studio to item.studio.orEmpty(),
        R.string.details_info_language to (org.arcmediaserver.android.playback.languageName(item.originalLanguage) ?: ""),
        R.string.details_info_starring to names("actor", 5),
        R.string.details_info_directors to names("director"),
        R.string.details_info_writers to names("writer"),
    ).filter { it.second.isNotBlank() }
    if (rows.isEmpty()) return
    Column(Modifier.padding(start = PageStart, end = PageStart, top = 28.dp)) {
        Text(stringResource(R.string.details_info), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        // Focusable so the page can scroll down to it; there's nothing to press.
        Column(Modifier.focusable()) {
            rows.chunked(3).forEach { line ->
                Row(Modifier.padding(bottom = 14.dp)) {
                    line.forEach { (key, value) ->
                        Column(Modifier.weight(1f).padding(end = 16.dp)) {
                            Text(stringResource(key), style = MaterialTheme.typography.labelMedium, color = ArcColors.Foreground.copy(alpha = 0.6f))
                            Spacer(Modifier.height(3.dp))
                            Text(value, style = MaterialTheme.typography.bodySmall, color = ArcColors.Foreground.copy(alpha = 0.9f), maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    repeat(3 - line.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** e.g. 1:02:05 or 12:34. */
fun clock(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Seasons(details: Details, onPlay: (Item, Long) -> Unit, episodeFocus: FocusRequester) {
    if (details.seasons.isEmpty()) {
        Text(stringResource(R.string.details_no_episodes), style = MaterialTheme.typography.bodyLarge, color = ArcColors.Muted, modifier = Modifier.padding(start = PageStart, top = 32.dp))
        return
    }
    var selected by rememberSaveable(details.item.id) { mutableIntStateOf(details.initialSeason.coerceIn(0, details.seasons.lastIndex)) }
    Column(Modifier.padding(top = 24.dp)) {
        PillTabs(details.seasons.map { seasonName(it) }, selected, onSelect = { selected = it }, modifier = Modifier.padding(start = PageStart - 8.dp))
        val season = details.seasons[selected]
        val listState = rememberLazyListState()
        LaunchedEffect(selected) {
            val idx = season.episodes.indexOfFirst { it.id == (details.focus ?: details.next)?.id }
            listState.scrollToItem(idx.coerceAtLeast(0))
        }
        CompositionLocalProvider(LocalBringIntoViewSpec provides minimalBringIntoView(PageStart)) {
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(start = PageStart, end = 48.dp, top = 18.dp, bottom = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                items(season.episodes, key = { it.id }) { ep ->
                    val mod = if (ep.id == details.focus?.id) Modifier.focusRequester(episodeFocus) else Modifier
                    EpisodeCard(ep, onClick = { onPlay(ep, ep.resumeMs) }, modifier = mod)
                }
            }
        }
    }
}

@Composable
fun seasonName(season: Season): String = season.name.takeIf { it.isNotBlank() }
    ?: if (season.seasonNumber == 0) stringResource(R.string.specials) else stringResource(R.string.season_number, season.seasonNumber)

/** An episode: its picture with progress and a tick, its number and name, and how long it is. */
@Composable
private fun EpisodeCard(ep: Item, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val card = cardFor(ep)
    Column(Modifier.width(ThumbWidth)) {
        FocusCard(onClick = onClick, onFocused = { focused = it }, modifier = modifier) {
            Artwork(card.thumb, label = ep.title, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f), contentScale = ContentScale.Crop)
            WatchBadge(ep)
            ProgressLine(card.progress)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            listOfNotNull(ep.episodeNumber?.let { "$it." }, ep.title).joinToString(" "),
            style = MaterialTheme.typography.titleSmall,
            color = if (focused) ArcColors.Foreground else ArcColors.Foreground.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val sub = listOfNotNull(
            ep.runtimeMin?.takeIf { it > 0 }?.let { runtimeText(it) },
            if (!ep.available) stringResource(R.string.details_missing) else null,
        ).joinToString(" · ")
        if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = ArcColors.Muted)
        // The focused episode's description has its room kept, so showing it doesn't push the
        // cast and everything under the episodes down.
        Box(Modifier.height(40.dp)) {
            if (focused && !ep.overview.isNullOrBlank()) {
                Text(ep.overview, style = MaterialTheme.typography.bodySmall, color = ArcColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** An extra: a wide card with its name, and what kind of extra it is and how long under it. */
@Composable
private fun ExtraCard(extra: org.arcmediaserver.android.data.Extra, onClick: () -> Unit) {
    Column(Modifier.width(ThumbWidth)) {
        FocusCard(onClick = onClick) {
            Artwork(null, label = extra.title, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            Icon(ArcIcons.Play, contentDescription = null, modifier = Modifier.align(Alignment.Center).size(32.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(extra.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val sub = listOfNotNull(extraKindName(extra.kind), (extra.durationMs / 60_000).toInt().takeIf { it > 0 }?.let { runtimeText(it) }).joinToString(" · ")
        Text(sub, style = MaterialTheme.typography.bodySmall, color = ArcColors.Muted, maxLines = 1)
    }
}

@Composable
private fun extraKindName(kind: String): String = stringResource(
    when (kind) {
        "trailer" -> R.string.extras_trailer
        "featurette" -> R.string.extras_featurette
        "behind_the_scenes" -> R.string.extras_behind_the_scenes
        "deleted_scene" -> R.string.extras_deleted_scene
        "interview" -> R.string.extras_interview
        "scene" -> R.string.extras_scene
        "short" -> R.string.extras_short
        else -> R.string.extras_other
    },
)

/**
 * Where a title page's text and rows start: Google TV's 48dp edge. The page has no sidebar, so
 * Home's 96dp (past the sidebar) left an empty strip (owner, 30 October).
 */
private val PageStart = 48.dp
