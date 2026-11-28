package org.arcmediaserver.android.ui.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.arcmediaserver.android.R
import org.arcmediaserver.android.data.Item
import org.arcmediaserver.android.data.PersonPage
import org.arcmediaserver.android.data.PersonRows
import org.arcmediaserver.android.data.cardFor
import org.arcmediaserver.android.ui.arcViewModel
import org.arcmediaserver.android.ui.browse.runtimeText
import org.arcmediaserver.android.ui.components.ArcIcons
import org.arcmediaserver.android.ui.components.Artwork
import org.arcmediaserver.android.ui.components.EpisodeArtwork
import org.arcmediaserver.android.ui.components.LoadState
import org.arcmediaserver.android.ui.components.ProgressLine
import org.arcmediaserver.android.ui.components.WatchBadge
import org.arcmediaserver.android.ui.components.episodeCode
import org.arcmediaserver.android.ui.components.episodeWithTitle
import org.arcmediaserver.android.ui.details.ActionProblem
import org.arcmediaserver.android.ui.details.Details
import org.arcmediaserver.android.ui.details.DetailsViewModel
import org.arcmediaserver.android.ui.details.clock
import org.arcmediaserver.android.ui.person.PersonViewModel
import org.arcmediaserver.android.ui.theme.ArcColors

@Composable
fun MobileDetailsRoute(
    itemId: String,
    episodeId: String?,
    onBack: () -> Unit,
    onPlay: (Item, Long) -> Unit,
    onPlayQueued: (Item, String) -> Unit,
    onOpen: (Item) -> Unit,
    onPerson: (Long) -> Unit,
) {
    val vm = arcViewModel { DetailsViewModel(it, itemId, episodeId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val canShuffle by vm.canShuffle.collectAsStateWithLifecycle()
    val problem by vm.problem.collectAsStateWithLifecycle()
    // Coming back from the player: show the new resume point and watched state.
    var resumed by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        if (resumed) vm.load(quiet = true)
        resumed = true
        onPauseOrDispose { }
    }
    val snackbar = remember { SnackbarHostState() }
    val problemText = problem?.let { problemText(it) }
    LaunchedEffect(problem) {
        if (problemText != null) {
            snackbar.showSnackbar(problemText)
            vm.problemShown()
        }
    }
    Scaffold(containerColor = ArcColors.Background, snackbarHost = { SnackbarHost(snackbar) }, contentWindowInsets = WindowInsets(0)) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                LoadState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                is LoadState.Failed -> Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars), contentAlignment = Alignment.Center) { MobileError(s.error, onRetry = { vm.load() }) }
                is LoadState.Ready -> MobileDetailsScreen(
                    s.value,
                    onPlay = onPlay,
                    onWatched = vm::toggleWatched,
                    onWatchlist = vm::toggleWatchlist,
                    onShuffle = if (canShuffle) { { vm.shuffle(onPlayQueued) } } else null,
                    onOpen = onOpen,
                    onPerson = onPerson,
                )
            }
            // Back over the artwork, under the status bar.
            FilledTonalIconButton(
                onClick = onBack,
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.Black.copy(alpha = 0.45f), contentColor = ArcColors.Foreground),
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(8.dp),
            ) { Icon(ArcIcons.ArrowLeft, contentDescription = stringResource(R.string.mobile_back)) }
        }
    }
}

@Composable
private fun problemText(problem: ActionProblem): String = stringResource(
    when (problem) {
        ActionProblem.Watched -> R.string.action_watched_failed
        ActionProblem.Watchlist -> R.string.action_watchlist_failed
        ActionProblem.Season -> R.string.action_season_watched_failed
        ActionProblem.Rating -> R.string.action_rating_failed
    },
)

/**
 * A title's page for touch, in Arc's order: the artwork, the name and its details line, Play (or
 * Resume), Watched / Watchlist / Shuffle, the description; for a show its seasons as chips and
 * the chosen season's episodes; then the cast and related rows. Opened from an episode's card,
 * it opens on that episode's season and scrolls to it, as the TV's does.
 */
@Composable
fun MobileDetailsScreen(
    details: Details,
    onPlay: (Item, Long) -> Unit,
    onWatched: () -> Unit,
    onWatchlist: () -> Unit,
    onShuffle: (() -> Unit)?,
    onOpen: (Item) -> Unit,
    onPerson: (Long) -> Unit,
) {
    val item = details.item
    val margin = pageMargin()
    val tabs = details.seasons.size + if (details.unplaced.isNotEmpty()) 1 else 0
    var season by rememberSaveable(item.id) { mutableIntStateOf(details.initialSeason.coerceIn(0, (tabs - 1).coerceAtLeast(0))) }
    val episodes = when {
        !item.isSeries -> emptyList()
        season < details.seasons.size -> details.seasons[season].episodes
        else -> details.unplaced
    }
    val list = rememberLazyListState()
    // Headers before the episodes: the top, then the season chips.
    val before = 2
    LaunchedEffect(item.id) {
        val land = details.focus ?: return@LaunchedEffect
        val at = episodes.indexOfFirst { it.id == land.id }
        if (at >= 0) list.scrollToItem(before + at, scrollOffset = -48)
    }
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    LazyColumn(state = list, contentPadding = PaddingValues(bottom = 32.dp), modifier = Modifier.fillMaxSize()) {
        item(key = "top") {
            Column {
                // A phone's artwork is the width of the screen; a tablet's stops at 360dp high so the
                // title and Play show without scrolling.
                Box(Modifier.fillMaxWidth().then(if (wide) Modifier.height(360.dp) else Modifier.aspectRatio(16f / 10f))) {
                    Artwork(item.images?.backdropFull ?: item.images?.backdrop, label = "", modifier = Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.35f), 0.25f to Color.Transparent, 0.65f to Color.Transparent, 1f to ArcColors.Background)))
                }
                Column(Modifier.padding(horizontal = margin).widthIn(max = 600.dp)) {
                    // The title's logo, as Arc's title pages show it (its name when there's none).
                    org.arcmediaserver.android.ui.browse.TitleLogo(item, maxHeight = 64.dp, maxWidth = 260.dp, textStyle = MaterialTheme.typography.headlineMedium)
                    val meta = listOfNotNull(
                        item.yearAndEdition,
                        item.runtimeMin?.takeIf { it > 0 && !item.isSeries }?.let { runtimeText(it) },
                        item.contentRating?.takeIf { it.isNotBlank() },
                        item.genres.take(2).joinToString(", ").ifBlank { null },
                    )
                    if (meta.isNotEmpty()) Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = ArcColors.Muted, modifier = Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(16.dp))
                    val target = if (item.isSeries) details.next else item.takeIf { it.playable }
                    if (target != null) {
                        val code = if (target.isEpisode) episodeCode(target) else null
                        val label = when {
                            code != null && target.resumeMs > 0 -> stringResource(R.string.action_resume_episode, code)
                            code != null -> stringResource(R.string.action_play_episode, code)
                            target.resumeMs > 0 -> stringResource(R.string.action_resume, clock(target.resumeMs))
                            else -> stringResource(R.string.action_play)
                        }
                        Button(onClick = { onPlay(target, target.resumeMs) }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                            Icon(ArcIcons.Play, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(label)
                        }
                        if (target.resumeMs > 0) {
                            TextButton(onClick = { onPlay(target, 0) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_play_from_start)) }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        ActionButton(
                            if (item.watched) ArcIcons.CircleCheck else ArcIcons.Check,
                            stringResource(if (item.watched) R.string.action_mark_unwatched else R.string.action_mark_watched),
                            onWatched,
                        )
                        if (item.canWatchlist) ActionButton(
                            if (item.onWatchlist) ArcIcons.BookmarkCheck else ArcIcons.Bookmark,
                            stringResource(if (item.onWatchlist) R.string.action_remove_watchlist else R.string.action_add_watchlist),
                            onWatchlist,
                        )
                        if (onShuffle != null && item.isSeries) ActionButton(ArcIcons.Shuffle, stringResource(R.string.action_shuffle), onShuffle)
                    }
                    item.overview?.takeIf { it.isNotBlank() }?.let { Overview(it) }
                }
            }
        }
        item(key = "seasons") {
            if (item.isSeries && tabs > 0) {
                LazyRow(contentPadding = PaddingValues(horizontal = margin), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) {
                    items(tabs) { i ->
                        val name = if (i < details.seasons.size) details.seasons[i].let { s -> s.name.ifBlank { stringResource(R.string.season_number, s.seasonNumber) } } else stringResource(R.string.details_not_in_order)
                        FilterChip(selected = season == i, onClick = { season = i }, label = { Text(name) })
                    }
                }
            }
        }
        if (item.isSeries && episodes.isEmpty()) {
            item(key = "none") { Text(stringResource(R.string.details_no_episodes), style = MaterialTheme.typography.bodyMedium, color = ArcColors.Muted, modifier = Modifier.padding(horizontal = margin, vertical = 8.dp)) }
        }
        items(episodes, key = { "ep-" + it.id }) { ep -> EpisodeRow(ep, onPlay = { onPlay(ep, ep.resumeMs) }) }
        val cast = item.credits.filter { it.role == "actor" || it.role.isEmpty() }.distinctBy { it.personId }
        if (cast.isNotEmpty()) item(key = "cast") {
            Box(Modifier.padding(top = 24.dp)) {
                PeopleRow(stringResource(R.string.details_cast), cast.map { Triple(it.name, it.image, it.character) }, onClick = { i -> onPerson(cast[i].personId) })
            }
        }
        items(details.related, key = { "rel-" + it.key }) { row ->
            Box(Modifier.padding(top = 24.dp)) {
                MobileShelf(row.title ?: stringResource(R.string.details_more_like_this), row.items.map { cardFor(it) }, landscape = row.landscape, onClick = { onOpen(it.item) })
            }
        }
    }
}

@Composable
private fun ActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = ArcColors.Muted, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.width(88.dp))
    }
}

/** The description, four lines until tapped (the whole of it then, as Plex's "more"). */
@Composable
private fun Overview(text: String) {
    var open by rememberSaveable { mutableStateOf(false) }
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = if (open) Int.MAX_VALUE else 4,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 12.dp).clickable { open = !open },
    )
}

/** An episode: its still with the watched badge and progress, "S1 · E4 · Pilot", the run time, and its description. */
@Composable
private fun EpisodeRow(ep: Item, onPlay: () -> Unit) {
    val margin = pageMargin()
    val card = cardFor(ep)
    Row(
        Modifier.fillMaxWidth().clickable(enabled = ep.available, onClick = onPlay).padding(horizontal = margin, vertical = 8.dp).alpha(if (ep.available) 1f else 0.5f),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.width(144.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(6.dp))) {
            EpisodeArtwork(ep, card.thumb, label = ep.title, modifier = Modifier.fillMaxSize())
            WatchBadge(ep)
            ProgressLine(card.progress)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(episodeWithTitle(ep), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val sub = if (!ep.available) stringResource(R.string.details_missing) else ep.runtimeMin?.takeIf { it > 0 }?.let { runtimeText(it) }
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = ArcColors.Muted)
            // An unwatched episode's description waits while the profile hides spoilers.
            val hide = org.arcmediaserver.android.ui.components.LocalHideSpoilers.current && !ep.watched
            ep.overview?.takeIf { it.isNotBlank() && !hide }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = ArcColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

@Composable
fun MobilePersonRoute(personId: Long, onBack: () -> Unit, onOpen: (Item) -> Unit) {
    val vm = arcViewModel { PersonViewModel(it, personId) }
    val state by vm.state.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        when (val s = state) {
            LoadState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is LoadState.Failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { MobileError(s.error, onRetry = vm::load) }
            is LoadState.Ready -> MobilePersonScreen(s.value, onOpen)
        }
        IconButton(onClick = onBack, modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(8.dp)) {
            Icon(ArcIcons.ArrowLeft, contentDescription = stringResource(R.string.mobile_back))
        }
    }
}

/** A person: their photo, what they're known for, and their films, shows and crew work here. */
@Composable
fun MobilePersonScreen(page: PersonPage, onOpen: (Item) -> Unit) {
    val rows = remember(page) { PersonRows.rows(page) }
    LazyColumn(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars), contentPadding = PaddingValues(top = 56.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item(key = "top") {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Artwork(page.person.image, label = org.arcmediaserver.android.ui.person.initials(page.person.name), modifier = Modifier.size(128.dp).clip(CircleShape))
                Spacer(Modifier.height(12.dp))
                Text(page.person.name, style = MaterialTheme.typography.headlineSmall)
                Text(org.arcmediaserver.android.ui.person.summary(page), style = MaterialTheme.typography.bodyMedium, color = ArcColors.Muted)
            }
        }
        items(rows, key = { it.kind.name }) { row ->
            MobileShelf(org.arcmediaserver.android.ui.person.rowTitle(row.kind), row.credits.map { cardFor(it.item) }, landscape = false, onClick = { onOpen(it.item) })
        }
    }
}
