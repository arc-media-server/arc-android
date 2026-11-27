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
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import org.arcmediaserver.android.ui.components.revealOnFocus
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import coil3.request.crossfade
import coil3.request.transformations
import androidx.compose.runtime.MutableState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalInspectionMode
import org.arcmediaserver.android.ui.components.landsAtFoot
import org.arcmediaserver.android.ui.components.footScrollPage
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.focus.focusProperties
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.arcmediaserver.android.R
import org.arcmediaserver.android.ui.components.requestWhenReady
import org.arcmediaserver.android.data.Item
import org.arcmediaserver.android.data.Season
import org.arcmediaserver.android.data.orderNumbers
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
import org.arcmediaserver.android.ui.components.episodeWithTitle
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val serverName = remember { (context.applicationContext as? org.arcmediaserver.android.ArcApplication)?.container?.session?.serverName }
    val state by vm.state.collectAsStateWithLifecycle()
    val canShuffle by vm.canShuffle.collectAsStateWithLifecycle()
    // Theme Music: the film's or show's tune, quietly, once per visit (the profile can turn it off).
    val themeOn by remember { (context.applicationContext as? org.arcmediaserver.android.ArcApplication)?.container?.themeMusic ?: kotlinx.coroutines.flow.MutableStateFlow(false) }.collectAsStateWithLifecycle()
    ThemeMusicEffect(itemId, ThemeTune.urlFor((state as? org.arcmediaserver.android.ui.components.LoadState.Ready)?.value?.item, themeOn))
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
                    onSeasonWatched = vm::toggleSeasonWatched,
                    onRate = vm::rate,
                    onShuffle = if (canShuffle) { { vm.shuffle(onPlayQueued) } } else null,
                    onShuffleSeason = if (canShuffle) { { n -> vm.shuffle(onPlayQueued, n) } } else null,
                    onPlayVersion = onPlayVersion,
                ),
                onPerson = onPerson,
                onExtra = onExtra,
                serverName = serverName,
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
    /** Shuffle Season (a season's menu), by season number; null where it can't be shuffled. */
    val onShuffleSeason: ((Int) -> Unit)? = null,
    /** Play Version…: plays the title from a point with a chosen file. */
    val onPlayVersion: ((Item, Long, Long) -> Unit)? = null,
    /** A show's Mark Season as Watched / Unwatched, by season number. */
    val onSeasonWatched: (Int) -> Unit = {},
    /** The profile's own rating, 1–10 (0 clears it). */
    val onRate: (Int) -> Unit = {},
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
    /** The server's name, for the About section's Playback card. */
    serverName: String? = null,
) {
    val item = details.item
    val primary = remember { FocusRequester() }
    val more = remember { FocusRequester() }
    val episodeFocus = remember { FocusRequester() }
    val about = remember { FocusRequester() }
    val page = rememberLazyListState()
    var panel by remember { mutableStateOf(initialPanel) }
    // The Apple TV app's page: the artwork fills the screen and dims once the page scrolls.
    val scrolled by remember { androidx.compose.runtime.derivedStateOf { page.firstVisibleItemIndex > 0 || page.firstVisibleItemScrollOffset > 40 } }
    var moreAnchor by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    // A season's menu (OK on its tab): its number and where its tab is.
    var seasonMenu by remember { mutableStateOf<Pair<Int, androidx.compose.ui.geometry.Rect?>?>(null) }
    val seasonTab = remember { FocusRequester() }
    // The page colour is painted only while the backdrop doesn't fill the screen (loading, fading,
    // dimmed or none): under an opaque backdrop it's a whole-screen fill nobody sees.
    val covered = remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().drawBehind { if (!covered.value) drawRect(ArcColors.Background) }) {
        HeroBackdrop(item, scrolled, covered = covered)
        val scope = rememberCoroutineScope()
        // The header always shows whole (as Plex), and below it the focused row lands at the foot
        // of the screen, moving down or back up, with the rows above in view (as arc-kodi's title
        // page). Cards don't scroll the page themselves; each row asks (landsAtFoot).
        val foot = org.arcmediaserver.android.ui.components.rememberFootScroll(page, gap = 40.dp)
        CompositionLocalProvider(LocalBringIntoViewSpec provides org.arcmediaserver.android.ui.components.ScrollByShelf) {
            LazyColumn(Modifier.fillMaxSize().footScrollPage(foot), state = page, contentPadding = PaddingValues(bottom = 40.dp)) {
                item {
                    Box(
                        Modifier.height(androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp - if (item.isSeries) SeasonBarPeek else 0.dp).onFocusChanged { if (it.hasFocus) scope.launch { page.animateScrollToItem(0) } },
                        contentAlignment = Alignment.BottomStart,
                    ) {
                        Header(details, onPlay, onOpen, primary, actions, more, onMore = { at -> moreAnchor = at; panel = DetailsPanel.More }, about = about, onAbout = { panel = DetailsPanel.About })
                    }
                }
                if (item.isSeries) {
                    item {
                        Box(Modifier.landsAtFoot(foot)) {
                            Seasons(details, onPlay, episodeFocus, actions, seasonTab, onSeasonMenu = { s, at -> seasonMenu = s.seasonNumber to at })
                        }
                    }
                }
                // Under the title, as the web's page: the cast, the collections it's in, More
                // Like This, then extras.
                val cast = item.credits.filter { it.role == "actor" }
                if (cast.isNotEmpty()) {
                    item(key = "cast") {
                        Box(Modifier.landsAtFoot(foot).padding(top = 28.dp)) {
                            Shelf(stringResource(R.string.details_cast), start = PageStart) {
                                items(cast, key = { it.personId }) { c -> PersonCard(c.name, c.image, c.character) { onPerson(c.personId) } }
                            }
                        }
                    }
                }
                details.related.forEach { row ->
                    item(key = row.key) {
                        Box(Modifier.landsAtFoot(foot).padding(top = 20.dp)) {
                            Shelf(row.title ?: stringResource(R.string.details_more_like_this), start = PageStart) {
                                items(row.items, key = { it.id }) { t -> MediaCardView(cardFor(t), landscape = row.landscape, onClick = { onOpen(t.id) }) }
                            }
                        }
                    }
                }
                if (item.extras.isNotEmpty()) {
                    item(key = "extras") {
                        Box(Modifier.landsAtFoot(foot).padding(top = 20.dp)) {
                            Shelf(stringResource(R.string.details_extras), start = PageStart) {
                                items(item.extras, key = { it.id }) { e -> ExtraCard(e) { onExtra(e) } }
                            }
                        }
                    }
                }
                item(key = "info") { Box(Modifier.landsAtFoot(foot)) { AboutSection(item, serverName) } }
            }
        }
        val closePanel = {
            panel = DetailsPanel.None
            more.requestFocus()
            Unit
        }
        seasonMenu?.let { (number, at) ->
            val season = details.seasons.firstOrNull { it.seasonNumber == number }
            val close = {
                seasonMenu = null
                runCatching { seasonTab.requestFocus() }
                Unit
            }
            if (season == null) {
                LaunchedEffect(Unit) { seasonMenu = null } // the page reloaded without that season
            } else {
                val first = remember { FocusRequester() }
                org.arcmediaserver.android.ui.components.Popover(at, onClose = close, place = org.arcmediaserver.android.ui.components.PopoverPlace.Below, title = seasonName(season)) {
                    val watched = season.watched()
                    org.arcmediaserver.android.ui.components.PopoverItem(
                        stringResource(if (watched) R.string.action_season_unwatched else R.string.action_season_watched),
                        icon = ArcIcons.CircleCheck, focus = first,
                    ) {
                        actions.onSeasonWatched(number)
                        close()
                    }
                    // Shuffle Season, as the web's show page: when there's more than one episode
                    // on disk to shuffle and the server keeps play queues.
                    val shuffleSeason = actions.onShuffleSeason
                    if (shuffleSeason != null && shuffleCandidates(details.seasons, number).size > 1) {
                        org.arcmediaserver.android.ui.components.PopoverItem(stringResource(R.string.action_shuffle_season), icon = ArcIcons.Shuffle) {
                            seasonMenu = null
                            shuffleSeason(number)
                        }
                    }
                }
                org.arcmediaserver.android.ui.components.FocusOnOpen(first)
            }
        }
        when (panel) {
            DetailsPanel.None -> Unit
            DetailsPanel.More -> {
                val cardMenu = org.arcmediaserver.android.ui.components.LocalCardMenu.current
                val queue = cardMenu?.queue
                MoreMenu(
                    item, actions.onWatched, actions.onWatchlist, onMediaInfo = { panel = DetailsPanel.MediaInfo }, onClose = closePanel,
                    onQueue = queue?.let { q -> { next: Boolean -> q(item, next) } },
                    // The same list as a card's menu (the app's card menu host draws it); closing it
                    // returns to ⋯.
                    onAddToPlaylist = cardMenu?.let { m -> { panel = DetailsPanel.None; m.addToPlaylist(item) { runCatching { more.requestFocus() } } } },
                    anchor = moreAnchor,
                    onPlayVersion = if (!item.isSeries && actions.onPlayVersion != null && versionsOf(item).size > 1) { { panel = DetailsPanel.Version } } else null,
                    onRate = { panel = DetailsPanel.Rating },
                    onWatchTogether = cardMenu?.watchTogether?.let { w -> { w(if (item.isSeries) details.next ?: item else item) { runCatching { more.requestFocus() } } } },
                )
            }
            DetailsPanel.Rating -> RatingMenu(item, onClose = closePanel) { rating -> actions.onRate(rating); closePanel() }
            DetailsPanel.MediaInfo -> MediaInfoDialog(item, onClose = closePanel)
            DetailsPanel.Version -> VersionMenu(item, onClose = closePanel) { file -> panel = DetailsPanel.None; actions.onPlayVersion?.invoke(item, item.resumeMs, file) }
            DetailsPanel.About -> AboutPanel(item, onClose = { panel = DetailsPanel.None; about.requestFocus() })
        }
    }
    LaunchedEffect(item.id) {
        if (initialPanel != DetailsPanel.None) return@LaunchedEffect
        // Opened from an episode's card: land on that episode in its show, as Plex does.
        if (details.focus != null && episodeFocus.requestWhenReady()) return@LaunchedEffect
        primary.requestWhenReady()
    }
}

/**
 * How much of a show's seasons shows under its header before the page scrolls: only the season
 * bar (its space above, the tabs and a margin), so the episodes wait below, as the Apple TV app
 * (owner, 20 November).
 */
private val SeasonBarPeek = 74.dp

@Composable
private fun Header(
    details: Details,
    onPlay: (Item, Long) -> Unit,
    onOpen: (String) -> Unit,
    primary: FocusRequester,
    actions: TitleActions,
    more: FocusRequester,
    onMore: (androidx.compose.ui.geometry.Rect?) -> Unit,
    about: FocusRequester = remember { FocusRequester() },
    onAbout: () -> Unit = {},
) {
    val item = details.item
    // The artwork fills the screen with everything low on it (owner, 16 November): the logo, the
    // age rating and genres, the description, the year and running time with the quality
    // capsules, then the Play button and round buttons; who's in it and who made it at the right.
    // A film's page keeps a margin at the bottom of the screen; a show's sits on its season bar.
    Row(Modifier.padding(start = PageStart, end = PageStart, bottom = if (item.isSeries) 8.dp else 40.dp), verticalAlignment = Alignment.Bottom) {
    Column(Modifier.weight(1f)) {
        org.arcmediaserver.android.ui.browse.TitleLogo(item, maxHeight = 120.dp, maxWidth = 460.dp, textStyle = MaterialTheme.typography.displayMedium)
        if (item.isEpisode) {
            Spacer(Modifier.height(8.dp))
            Text(episodeWithTitle(item), style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(16.dp))
        val rating = item.contentRating?.takeIf { it.isNotBlank() }
        val genres = item.genres.take(3)
        if (rating != null || genres.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                rating?.let { org.arcmediaserver.android.ui.components.AgeRating(it) }
                if (genres.isNotEmpty()) Text(genres.joinToString(" · "), style = MaterialTheme.typography.titleSmall, color = ArcColors.Foreground, maxLines = 1)
            }
        }
        Spacer(Modifier.height(2.dp))
        // The description: three lines, and when that cuts it short, MORE under it and the
        // whole thing focusable to open the full text, as the Apple TV app (owner, 1 November).
        var cut by remember { mutableStateOf(false) }
        androidx.tv.material3.Surface(
            onClick = onAbout,
            // Only focusable when there's more to read (a disabled Surface would dim the text).
            modifier = Modifier.widthIn(max = 600.dp).offset(x = (-10).dp).focusProperties { canFocus = cut }.focusRequester(about),
            shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
            scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
            colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent, contentColor = ArcColors.Foreground,
                focusedContainerColor = Color.White.copy(alpha = 0.14f), focusedContentColor = ArcColors.Foreground,
            ),
        ) {
          Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            org.arcmediaserver.android.ui.components.spoilerSafeOverview(item)?.let {
                Text(
                    it, style = MaterialTheme.typography.bodyMedium, color = ArcColors.Foreground.copy(alpha = 0.85f), maxLines = 3, overflow = TextOverflow.Ellipsis,
                    onTextLayout = { layout -> cut = layout.hasVisualOverflow },
                )
            }
            if (cut) Text(stringResource(R.string.details_more).uppercase(), style = MaterialTheme.typography.labelMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 1.sp), color = ArcColors.Foreground)
            val unwatched = item.unwatchedCount ?: 0
            val meta = listOfNotNull(
                item.year?.toString()?.takeIf { !item.isEpisode },
                item.runtimeMin?.takeIf { it > 0 && !item.isSeries }?.let { runtimeText(it) },
                if (item.isSeries) pluralStringResource(R.plurals.episodes_left, unwatched, unwatched).takeIf { unwatched > 0 } else null,
                item.rating?.takeIf { it > 0 }?.let { "★ %.1f".format(it) },
            ).filter { it.isNotBlank() }
            // The year and running time, then the quality capsules, on one line.
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (meta.isNotEmpty()) {
                    Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = ArcColors.Foreground.copy(alpha = 0.85f), maxLines = 1)
                }
                org.arcmediaserver.android.ui.components.TitleBadges(item, showRating = false)
            }
          }
        }
        Spacer(Modifier.height(6.dp))
        var focusedLabel by remember { mutableStateOf<String?>(null) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            val round = Color.White.copy(alpha = 0.16f)
            // A button's name shows below the row while it has the focus.
            fun labelled(label: String, base: Modifier = Modifier) = base.onFocusChanged {
                focusedLabel = if (it.isFocused) label else focusedLabel.takeIf { l -> l != label }
            }
            val target = if (item.isSeries) details.next else item
            if (target != null) {
                val resume = target.resumeMs
                val duration = target.durationMs
                val s = target.seasonNumber
                val e = target.episodeNumber
                // Outside the show's order (Arc's 0 and 0) an episode has no number to show.
                val numbered = target.isEpisode && target.orderNumbers != null
                val left = if (resume > 0 && duration > resume) runtimeText(((duration - resume) / 60_000).toInt().coerceAtLeast(1)) else null
                // What will play: "Resume S1 · E4", "Start watching · S1 · E1" or "Play S1 · E4";
                // part watched also shows a bar. A film part watched shows what's left.
                val label = when {
                    resume > 0 && numbered -> stringResource(R.string.action_resume_episode, episodeCode(s!!, e!!))
                    resume > 0 -> left ?: stringResource(R.string.action_resume, clock(resume))
                    item.isSeries && numbered && e == 1 && s != 0 -> stringResource(R.string.action_start_watching, episodeCode(s!!, e))
                    numbered -> stringResource(R.string.action_play_episode, episodeCode(s!!, e!!))
                    else -> stringResource(R.string.action_play)
                }
                val progress = if (resume > 0 && duration > 0) (resume.toFloat() / duration).coerceIn(0f, 1f) else null
                PlayPill(label, Modifier.focusRequester(primary), progress) { onPlay(target, resume) }
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
        // The focused round button's name, or what couldn't be saved, under the buttons. It takes
        // no room of its own (it's drawn in the space below), so a show's season bar sits right
        // under the buttons, as the LG/Samsung app.
        Box(
            Modifier.layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity))
                layout(placeable.width, 0) { placeable.place(0, 0) }
            }.padding(top = 8.dp),
        ) {
            // A season's problem shows beside its own button, in the seasons below.
            val problem = actions.problem?.takeIf { it != ActionProblem.Season }
            if (problem != null) {
                Text(
                    stringResource(
                        when (problem) {
                            ActionProblem.Watched -> R.string.action_watched_failed
                            ActionProblem.Rating -> R.string.action_rating_failed
                            else -> R.string.action_watchlist_failed
                        },
                    ),
                    style = MaterialTheme.typography.labelLarge, color = ArcColors.Warning, maxLines = 1,
                )
                LaunchedEffect(problem) {
                    kotlinx.coroutines.delay(5_000)
                    actions.onProblemShown()
                }
            } else {
                focusedLabel?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = ArcColors.Foreground.copy(alpha = 0.8f), maxLines = 1) }
            }
        }
    }
    HeaderCredits(item, Modifier.padding(start = 32.dp, bottom = 4.dp).widthIn(max = 420.dp))
    }
}

/** Who's in it and who made it, low on the right of a title's artwork. */
@Composable
private fun HeaderCredits(item: Item, modifier: Modifier = Modifier) {
    fun names(role: String) = item.credits.filter { it.role == role }.take(3).map { it.name }
    val lines = listOf(
        stringResource(R.string.details_info_starring) to names("actor"),
        stringResource(R.string.details_info_directors) to names("director"),
    ).filter { it.second.isNotEmpty() }
    if (lines.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        lines.forEach { (label, people) ->
            Text(
                androidx.compose.ui.text.buildAnnotatedString {
                    withStyle(androidx.compose.ui.text.SpanStyle(color = ArcColors.Foreground.copy(alpha = 0.7f))) { append(label) }
                    append(" ")
                    withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)) { append(people.joinToString(", ")) }
                },
                style = MaterialTheme.typography.bodyMedium, color = ArcColors.Foreground, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A title's whole description, opened from MORE above its buttons. */
@Composable
private fun AboutPanel(item: Item, onClose: () -> Unit) {
    val first = remember { FocusRequester() }
    org.arcmediaserver.android.ui.components.Popover(null, onClose = onClose, place = org.arcmediaserver.android.ui.components.PopoverPlace.Center, title = item.title, width = 560.dp) {
        androidx.tv.material3.Surface(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth().focusRequester(first),
            shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(RoundedCornerShape(11.dp)),
            scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1f),
            colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent, contentColor = ArcColors.Foreground, focusedContentColor = ArcColors.Foreground),
        ) {
            Text(org.arcmediaserver.android.ui.components.spoilerSafeOverview(item).orEmpty(), style = MaterialTheme.typography.bodyMedium, color = ArcColors.Foreground.copy(alpha = 0.85f), modifier = Modifier.padding(horizontal = 11.dp, vertical = 4.dp))
        }
    }
    org.arcmediaserver.android.ui.components.FocusOnOpen(first)
}

/**
 * The Play button: a glass pill like the round buttons beside it, white only while it has the focus
 * (the Apple TV app's). Part watched, a bar of how far through sits before the label, in its colour.
 */
@Composable
private fun PlayPill(label: String, modifier: Modifier = Modifier, progress: Float? = null, onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = modifier.height(44.dp), contentPadding = PaddingValues(start = 18.dp, end = 22.dp),
        scale = ButtonDefaults.scale(focusedScale = 1.06f),
        colors = ButtonDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.16f),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
    ) {
        Icon(ArcIcons.Play, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        if (progress != null) {
            val tint = androidx.tv.material3.LocalContentColor.current
            Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(tint.copy(alpha = 0.3f))) {
                Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(tint))
            }
            Spacer(Modifier.width(10.dp))
        }
        Text(label, style = MaterialTheme.typography.titleSmall)
    }
}

/**
 * The full-screen artwork behind the page, shaded at the bottom and left, dimmed once scrolled.
 *
 * Each picture comes with its shading already drawn into it ([BackdropShade], once, off the main
 * thread), so it's one opaque picture: drawing it is a single pass, with no saved layer to keep
 * or repaint. A new picture fades in over the last by its alpha, and dimming fades the whole
 * backdrop: both draw over the solid page, so the result matches painting the page colour over it.
 *
 * While it fills the screen opaquely ([whole]: no fade of the caller's own running; not dimmed;
 * its picture loaded and fully in), it says so through [covered] and [LocalBackdropCover], so
 * nothing paints the page colour beneath it.
 */
@Composable
internal fun HeroBackdrop(item: Item, dim: Boolean, whole: Boolean = true, covered: MutableState<Boolean>? = null) {
    val url = org.arcmediaserver.android.ui.browse.backdropOf(item)?.let(org.arcmediaserver.android.ui.components.LocalImageUrl.current)
    val dimAlpha = androidx.compose.animation.core.animateFloatAsState(if (dim) BackdropDim else 0f, label = "dim")
    // A plain list changed only here and when a fade ends (which bumps [changed]), so composing it
    // never writes back to state it has just read.
    val layers = remember { ArrayList<BackdropLayer>() }
    var changed by remember { mutableIntStateOf(0) }
    changed.let { } // read here, so the end of a fade recomposes
    val inspecting = LocalInspectionMode.current
    if (layers.lastOrNull()?.url != url) {
        // A picture still loading or fading in is replaced; the last one fully shown stays beneath.
        val shown = layers.lastOrNull { it.alpha.value >= 1f }
        layers.clear()
        if (shown != null) layers += shown
        if (url != null) layers += BackdropLayer(url, androidx.compose.animation.core.Animatable(if (shown == null && inspecting) 1f else 0f))
    }
    val bottom = layers.firstOrNull()
    // Read while drawing and fading, so composition hears only when it flips.
    val covering by remember(bottom, whole) {
        androidx.compose.runtime.derivedStateOf { whole && bottom != null && bottom.loaded && bottom.alpha.value >= 1f && dimAlpha.value == 0f }
    }
    val cover = org.arcmediaserver.android.ui.components.LocalBackdropCover.current
    val isCovering = covering
    if (covered != null) LaunchedEffect(isCovering) { covered.value = isCovering }
    if (cover != null && isCovering) {
        androidx.compose.runtime.DisposableEffect(cover) {
            cover.count++
            onDispose { cover.count-- }
        }
    }
    // One picture: its alpha and the dimming multiply straight onto it. Two (a fade): the pair is
    // dimmed together, which needs a layer, so the lower one doesn't show through the upper.
    val single = layers.size <= 1
    Box(
        Modifier.fillMaxSize().graphicsLayer {
            alpha = 1f - dimAlpha.value
            compositingStrategy = if (single) androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha else androidx.compose.ui.graphics.CompositingStrategy.Auto
        },
    ) {
        for (layer in layers.toList()) {
            androidx.compose.runtime.key(layer.url) {
                BackdropPicture(layer, onShown = {
                    // Fully shown: the pictures beneath it are hidden, so they go.
                    val i = layers.indexOf(layer)
                    if (i > 0) {
                        repeat(i) { layers.removeAt(0) }
                        changed++
                    }
                })
            }
        }
    }
}

/** One backdrop picture, whether it has loaded, and how far it has faded in. */
private class BackdropLayer(val url: String, val alpha: androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D>) {
    var loaded by mutableStateOf(false)
}

@Composable
private fun BackdropPicture(layer: BackdropLayer, onShown: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(layer.loaded) {
        if (!layer.loaded) return@LaunchedEffect
        layer.alpha.animateTo(1f, androidx.compose.animation.core.tween(BackdropFadeMs))
        onShown()
    }
    coil3.compose.AsyncImage(
        // No crossfade of its own: the picture's alpha fades instead.
        model = remember(layer.url) {
            coil3.request.ImageRequest.Builder(context).data(layer.url).crossfade(false)
                .transformations(org.arcmediaserver.android.ui.components.BackdropShade(ArcColors.Background)).build()
        },
        contentDescription = null,
        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        onSuccess = { layer.loaded = true },
        modifier = Modifier.fillMaxSize().graphicsLayer {
            alpha = layer.alpha.value
            compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
        },
    )
}

private const val BackdropFadeMs = 250

/** How far the artwork dims once the page scrolls past the hero (the page colour's share). */
internal const val BackdropDim = 0.82f

/**
 * The About section at the foot of a title's page, as the Apple TV app's: an About card across the
 * top (the title in bold, then the overview), then Details, Crew and Playback side by side.
 */
@Composable
private fun AboutSection(item: Item, serverName: String?) {
    fun names(role: String, n: Int = 4) = item.credits.filter { it.role == role }.take(n).joinToString(", ") { it.name }
    val released = item.releaseDate?.let { d ->
        runCatching {
            java.time.LocalDate.parse(d.take(10)).format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.LONG))
        }.getOrNull()
    } ?: item.year?.toString()
    val details = listOf(
        stringResource(R.string.details_info_released) to released.orEmpty(),
        stringResource(R.string.details_info_runtime) to (item.runtimeMin?.takeIf { it > 0 && !item.isSeries }?.let { runtimeText(it) } ?: ""),
        stringResource(R.string.details_info_rated) to item.contentRating.orEmpty(),
        stringResource(R.string.details_info_genre) to item.genres.joinToString(", "),
        stringResource(R.string.details_info_language) to (org.arcmediaserver.android.playback.languageName(item.originalLanguage) ?: ""),
    ).filter { it.second.isNotBlank() }
    val crew = listOf(
        stringResource(R.string.details_info_directors) to names("director"),
        stringResource(R.string.details_info_writers) to names("writer"),
        stringResource(R.string.details_info_studio) to item.studio.orEmpty(),
    ).filter { it.second.isNotBlank() }
    val file = item.files.firstOrNull { !it.missing }
    val playback = listOf(
        stringResource(R.string.settings_server) to serverName.orEmpty(),
        stringResource(R.string.player_quality) to org.arcmediaserver.android.playback.Quality.summary(file).orEmpty(),
        stringResource(R.string.details_bitrate) to (file?.bitrate?.takeIf { it > 0 }?.let { "%.1f Mbps".format(it / 1e6) } ?: ""),
        stringResource(R.string.details_size) to (file?.size?.takeIf { it > 0 }?.let { "%.1f GB".format(it / 1e9) } ?: ""),
        stringResource(R.string.details_edition) to file?.edition.orEmpty(),
        stringResource(R.string.details_file) to (file?.path?.substringAfterLast('/')?.substringAfterLast('\\') ?: ""),
    ).filter { it.second.isNotBlank() }
    val overview = org.arcmediaserver.android.ui.components.spoilerSafeOverview(item)
    if (overview == null && details.isEmpty() && crew.isEmpty() && playback.isEmpty()) return
    Column(Modifier.padding(start = PageStart, end = PageStart, top = 28.dp)) {
        Text(stringResource(R.string.details_about), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        // Focusable so the page can scroll down to it; there's nothing to press.
        Column(Modifier.focusable()) {
            if (overview != null) {
                InfoCard(Modifier.fillMaxWidth()) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
                    Spacer(Modifier.height(6.dp))
                    Text(overview, style = MaterialTheme.typography.bodyMedium, color = ArcColors.Foreground.copy(alpha = 0.85f), maxLines = 5, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(14.dp))
            }
            Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                val cards = listOf(R.string.details_card_details to details, R.string.details_card_crew to crew, R.string.details_card_playback to playback).filter { it.second.isNotEmpty() }
                cards.forEach { (title, rows) ->
                    InfoCard(Modifier.weight(1f).fillMaxHeight()) {
                        Text(stringResource(title), style = MaterialTheme.typography.titleSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
                        Spacer(Modifier.height(10.dp))
                        rows.forEach { (label, value) ->
                            Text(label, style = MaterialTheme.typography.labelMedium, color = ArcColors.Foreground.copy(alpha = 0.6f))
                            Text(value, style = MaterialTheme.typography.bodySmall, color = ArcColors.Foreground.copy(alpha = 0.9f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
                repeat(3 - cards.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun InfoCard(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.08f)).padding(16.dp), content = content)
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
private fun Seasons(
    details: Details,
    onPlay: (Item, Long) -> Unit,
    episodeFocus: FocusRequester,
    actions: TitleActions = TitleActions(),
    seasonTab: FocusRequester = remember { FocusRequester() },
    onSeasonMenu: (Season, androidx.compose.ui.geometry.Rect?) -> Unit = { _, _ -> },
) {
    // The seasons, then Not in This Order (episodes on disk the show's order leaves out) as the
    // last tab, as the web's show page: nothing goes missing.
    val outside = details.unplaced.isNotEmpty()
    val tabs = details.seasons.size + if (outside) 1 else 0
    if (tabs == 0) {
        Text(stringResource(R.string.details_no_episodes), style = MaterialTheme.typography.bodyLarge, color = ArcColors.Muted, modifier = Modifier.padding(start = PageStart, top = 32.dp))
        return
    }
    // Show Missing Episodes can add or drop seasons, so the tab starts again (on the season kept).
    var selected by rememberSaveable(details.item.id, details.missing) { mutableIntStateOf(details.initialSeason.coerceIn(0, tabs - 1)) }
    // Reveal the same block whether focus enters through a season tab or a linked episode.
    // Wait a frame so the focusable child's smaller request starts first.
    val block = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    Column(
        Modifier.bringIntoViewRequester(block).onFocusChanged {
            if (it.hasFocus) scope.launch { androidx.compose.runtime.withFrameNanos { }; block.bringIntoView() }
        }.focusGroup().padding(top = 24.dp),
    ) {
        val labels = details.seasons.map { seasonName(it) } + if (outside) listOf(stringResource(R.string.details_not_in_order)) else emptyList()
        val unplacedTab = selected >= details.seasons.size
        val episodes = if (unplacedTab) details.unplaced else details.seasons[selected].episodes
        // Moving along the tabs switches season, so a button after them could only be reached by
        // passing the last one. OK on a season's tab opens its menu instead (Mark Season as
        // Watched / Unwatched), as Plex's season menu. Not in This Order isn't a season, so it
        // has no menu.
        var tabBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PillTabs(
                labels, selected, onSelect = { selected = it },
                // Coming into the tabs (Down from Play, Up from the episodes) lands on the season
                // shown, not whichever tab is nearest: landing on a tab switches season.
                modifier = Modifier.weight(1f, fill = false).focusRestorer(seasonTab),
                start = PageStart - 8.dp,
                end = 48.dp,
                onClick = { i -> details.seasons.getOrNull(i)?.let { onSeasonMenu(it, tabBounds) } },
                selectedTab = Modifier.focusRequester(seasonTab).onGloballyPositioned { tabBounds = it.boundsInRoot() },
            )
            if (actions.problem == ActionProblem.Season) {
                Spacer(Modifier.width(16.dp))
                Text(stringResource(R.string.action_season_watched_failed), style = MaterialTheme.typography.labelLarge, color = ArcColors.Warning)
                LaunchedEffect(actions.problem) {
                    kotlinx.coroutines.delay(5_000)
                    actions.onProblemShown()
                }
            }
        }
        if (unplacedTab) {
            Text(
                if (details.orderName.isNotBlank()) stringResource(R.string.details_not_in_order_note, details.orderName)
                else stringResource(R.string.details_not_in_order_note_unnamed),
                style = MaterialTheme.typography.bodySmall, color = ArcColors.Muted,
                modifier = Modifier.padding(start = PageStart, top = 14.dp).widthIn(max = 640.dp),
            )
        }
        val listState = rememberLazyListState()
        LaunchedEffect(selected) {
            val idx = episodes.indexOfFirst { it.id == (details.focus ?: details.next)?.id }
            listState.scrollToItem(idx.coerceAtLeast(0))
        }
        // Entering the episodes lands on the one last used here, else the one to watch next, else
        // the first on screen (RowFocus), not whichever is nearest the card left above or below.
        val rowFocus = org.arcmediaserver.android.ui.components.rememberRowFocus(listState)
        CompositionLocalProvider(LocalBringIntoViewSpec provides minimalBringIntoView(PageStart)) {
            LazyRow(
                state = listState,
                modifier = rowFocus.modifier(preferred = { (details.focus ?: details.next)?.id }),
                contentPadding = PaddingValues(start = PageStart, end = 48.dp, top = 18.dp, bottom = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                items(episodes, key = { it.id }) { ep ->
                    val mod = if (ep.id == details.focus?.id) Modifier.focusRequester(episodeFocus) else Modifier
                    rowFocus.Card(ep.id) { EpisodeCard(ep, onClick = { onPlay(ep, ep.resumeMs) }, modifier = mod, numbered = !unplacedTab) }
                }
            }
        }
    }
}

@Composable
fun seasonName(season: Season): String = season.name.takeIf { it.isNotBlank() }
    ?: if (season.seasonNumber == 0) stringResource(R.string.specials) else stringResource(R.string.season_number, season.seasonNumber)

/**
 * An episode as the Apple TV app shows one: its picture with progress and a tick, then its number,
 * its name, a few lines of its description, and its length (or what's left of it).
 */
@Composable
private fun EpisodeCard(ep: Item, onClick: () -> Unit, modifier: Modifier = Modifier, numbered: Boolean = true) {
    var focused by remember { mutableStateOf(false) }
    val card = cardFor(ep)
    Column(Modifier.width(EpisodeWidth).revealOnFocus()) {
        // An episode with no file (Show Missing Episodes) is dimmed and doesn't play; it can still
        // be focused, to read about it.
        FocusCard(onClick = { if (ep.available) onClick() }, onFocused = { focused = it }, modifier = modifier) {
            org.arcmediaserver.android.ui.components.EpisodeArtwork(
                ep, card.thumb, label = ep.title,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).alpha(if (ep.available) 1f else 0.4f),
            )
            WatchBadge(ep)
            ProgressLine(card.progress)
        }
        Spacer(Modifier.height(10.dp))
        // Outside the show's order an episode has no number in it.
        // The number as every Arc app writes it: "S1 · E3", or "Special 3" for season 0.
        ep.orderNumbers?.takeIf { numbered }?.let { (s, e) ->
            Text(
                episodeCode(s, e), maxLines = 1,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, letterSpacing = 0.5.sp), color = ArcColors.Muted,
            )
        }
        Text(
            ep.title,
            style = MaterialTheme.typography.titleSmall,
            color = if (focused) ArcColors.Foreground else ArcColors.Foreground.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        org.arcmediaserver.android.ui.components.spoilerSafeOverview(ep)?.let { overview ->
            Text(
                overview, style = MaterialTheme.typography.bodySmall, color = ArcColors.Foreground.copy(alpha = 0.65f), maxLines = 3, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(vertical = 3.dp),
            )
        }
        val left = ep.resumeMs.takeIf { it > 0 }?.let { pos -> ep.runtimeMin?.let { maxOf(1, (it - pos / 60_000).toInt()) } }
        val sub = listOfNotNull(
            if (left != null) stringResource(R.string.time_left, runtimeText(left)) else ep.runtimeMin?.takeIf { it > 0 }?.let { runtimeText(it) },
            if (!ep.available) stringResource(R.string.details_missing) else null,
        ).joinToString(" · ")
        if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = ArcColors.Muted)
    }
}

/** Episode cards are a little wider than other thumbnails, to fit three lines of description. */
private val EpisodeWidth = 200.dp

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
