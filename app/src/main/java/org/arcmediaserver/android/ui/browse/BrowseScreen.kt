package org.arcmediaserver.android.ui.browse

import androidx.compose.ui.layout.boundsInRoot

import androidx.compose.ui.layout.onGloballyPositioned

import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds

import androidx.compose.ui.draw.shadow

import androidx.compose.foundation.border

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.items
import org.arcmediaserver.android.ui.components.Shelf
import org.arcmediaserver.android.ui.components.PillTabs
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.runtime.mutableIntStateOf
import org.arcmediaserver.android.ui.components.minimalBringIntoView
import org.arcmediaserver.android.ui.components.rowsBringIntoView
import org.arcmediaserver.android.ui.components.ScrollByShelf
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import org.arcmediaserver.android.ui.components.LocalImageUrl
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import org.arcmediaserver.android.ui.together.roomId
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ModalNavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.NavigationDrawerItemDefaults
import androidx.tv.material3.NavigationDrawerScope
import androidx.tv.material3.Text
import androidx.tv.material3.rememberDrawerState
import org.arcmediaserver.android.R
import org.arcmediaserver.android.ui.components.requestWhenReady
import org.arcmediaserver.android.data.CardStyle
import org.arcmediaserver.android.data.HomeRowSpec
import org.arcmediaserver.android.data.Item
import org.arcmediaserver.android.data.Library
import org.arcmediaserver.android.data.MediaCard
import org.arcmediaserver.android.data.RowSource
import org.arcmediaserver.android.data.cardFor
import org.arcmediaserver.android.data.queueEntryId
import org.arcmediaserver.android.ui.arcViewModel
import org.arcmediaserver.android.ui.components.ArcIcons
import org.arcmediaserver.android.ui.components.Artwork
import org.arcmediaserver.android.ui.components.LoadState
import org.arcmediaserver.android.ui.components.Loaded
import org.arcmediaserver.android.ui.components.LoadingPane
import org.arcmediaserver.android.ui.components.MediaCardView
import org.arcmediaserver.android.ui.components.PosterWidth
import org.arcmediaserver.android.ui.components.Spinner
import org.arcmediaserver.android.ui.components.episodeWithTitle
import org.arcmediaserver.android.ui.components.text
import org.arcmediaserver.android.ui.profiles.Avatar
import org.arcmediaserver.android.ui.theme.ArcColors

@Composable
fun BrowseRoute(
    onOpen: (Item) -> Unit,
    onPerson: (org.arcmediaserver.android.data.Person) -> Unit,
    onSwitchProfile: () -> Unit,
    onSignedOut: () -> Unit,
    onOpenCollection: (org.arcmediaserver.android.data.CollectionRef) -> Unit = {},
    onOpenPlaylist: (org.arcmediaserver.android.data.PlaylistInfo) -> Unit = {},
    onPlayQueued: (Item, String) -> Unit = { _, _ -> },
    onSwitchServer: () -> Unit = onSignedOut,
    onPlay: (Item, Long) -> Unit = { _, _ -> },
) {
    val vm = arcViewModel { BrowseViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val container = (androidx.compose.ui.platform.LocalContext.current.applicationContext as org.arcmediaserver.android.ArcApplication).container
    // A Watch Together card chosen on Home: its Join (or Return) and Decline (or Cancel).
    var roomMenu by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { container.together.check() }
    // A genre opened from Search: Back returns to the results.
    androidx.activity.compose.BackHandler(enabled = (state.section as? Section.Library)?.genre != null) { vm.show(Section.Search) }
    // Coming back (from playing, or a title's page): Home's rows show what changed.
    var resumed by remember { mutableStateOf(false) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        if (resumed) vm.load()
        resumed = true
        onPauseOrDispose { }
    }
    BrowseScreen(
        state = state,
        onSection = vm::show,
        onOpen = onOpen,
        onRetry = vm::load,
        onRetryRow = vm::retryRow,
        onRetryLibrary = { (state.section as? Section.Library)?.let { vm.loadLibrary(it.id, it.genre) } },
        onNeedTitles = vm::needTitles,
        onLibraryQuery = vm::setLibraryQuery,
        onLibraryTab = vm::showLibraryTab,
        onOpenCollection = onOpenCollection,
        onRetryLibraryExtras = vm::retryLibraryExtras,
        onRetryWatchlist = vm::loadWatchlist,
        onOpenPlaylist = onOpenPlaylist,
        onRetryPlaylists = vm::loadPlaylists,
        onFolder = vm::openFolder,
        onFolderUp = vm::folderUp,
        onSwitchProfile = onSwitchProfile,
        onSignOut = { vm.signOut(onSignedOut) },
        searchContent = { org.arcmediaserver.android.ui.search.SearchRoute(onOpen, onPerson, onOpenCollection, onGenre = { g -> vm.show(Section.Library(g.libraryId, g.name)) }) },
        onPlayQueued = onPlayQueued,
        settingsContent = { org.arcmediaserver.android.ui.settings.SettingsRoute(onSignedOut = onSignedOut, onSwitchServer = onSwitchServer) },
        onPlay = onPlay,
        joinTogether = { close -> org.arcmediaserver.android.ui.together.JoinTogetherDialog(container, close) },
        onRoom = { roomMenu = it },
    )
    roomMenu?.let { id -> org.arcmediaserver.android.ui.together.RoomMenu(container, id, onClose = { roomMenu = null }) }
}

@Composable
fun BrowseScreen(
    state: BrowseUiState,
    onSection: (Section) -> Unit,
    onOpen: (Item) -> Unit,
    onRetry: () -> Unit,
    onRetryRow: (HomeRowSpec) -> Unit,
    onRetryLibrary: () -> Unit,
    onNeedTitles: (Int) -> Unit,
    onSwitchProfile: () -> Unit,
    onSignOut: () -> Unit,
    onLibraryTab: (LibraryTab) -> Unit = {},
    onOpenCollection: (org.arcmediaserver.android.data.CollectionRef) -> Unit = {},
    onRetryLibraryExtras: () -> Unit = {},
    onRetryWatchlist: () -> Unit = {},
    onOpenPlaylist: (org.arcmediaserver.android.data.PlaylistInfo) -> Unit = {},
    onRetryPlaylists: () -> Unit = {},
    onFolder: (path: String, focus: String?) -> Unit = { _, _ -> },
    onFolderUp: () -> Unit = {},
    onLibraryQuery: (LibraryQuery) -> Unit = {},
    drawerValue: DrawerValue = DrawerValue.Closed,
    searchContent: @Composable () -> Unit = {},
    onPlayQueued: (Item, String) -> Unit = { _, _ -> },
    settingsContent: @Composable () -> Unit = {},
    onPlay: (Item, Long) -> Unit = { _, _ -> },
    /** Join Watch Together (a code the host shared), from the profile at the top of the sidebar. */
    joinTogether: @Composable (onClose: () -> Unit) -> Unit = {},
    /** A Watch Together card on Home: the room's choices. */
    onRoom: (String) -> Unit = {},
) {
    var focusedCard by remember { mutableStateOf<MediaCard?>(null) }
    var homeScrolled by remember { mutableStateOf(false) }
    // With Watch Together, choosing the profile offers Switch Profile and Join Watch Together, as
    // the web's profile menu; without it, it switches profile at once.
    var profileMenu by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var joining by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(ArcColors.Background)) {
        // Home's whole-screen hero artwork sits behind the sidebar too.
        if (!HomePlainBackground && state.section == Section.Home && state.frame is LoadState.Ready) focusedCard?.item?.let { org.arcmediaserver.android.ui.details.HeroBackdrop(it, homeScrolled) }
        if (HomePlainBackground) androidx.compose.runtime.LaunchedEffect(Unit) { android.util.Log.i("Arc", "Diagnostic build: Home draws no backdrop") }
        Loaded(state.frame, onRetry) { frame ->
            val drawer = rememberDrawerState(drawerValue)
            ModalNavigationDrawer(
                drawerState = drawer,
                // The Apple TV app doesn't dim the page, but its glass blurs what's behind; Compose for TV's
                // drawer has no blur and draws the page over the panel's edge, so the shade stays.
                scrimBrush = Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Black.copy(alpha = 0.25f))),
                drawerContent = { value ->
                    Sidebar(
                        frame, state.section, value, onSection,
                        onProfile = { at -> if (state.together) profileMenu = at else onSwitchProfile() },
                        onSignOut = onSignOut,
                    )
                },
            ) {
                // The rail is 72dp wide but its icons end at 60dp: starting the content 48dp in
                // (plus each screen's own 48dp) leaves half the gap there was (owner, 30 October).
                Box(Modifier.fillMaxSize().padding(start = 48.dp)) {
                    Crossfade(state.section, label = "section") { section ->
                        when (section) {
                            Section.Home -> HomeContent(state.rows, onOpen, onRetryRow, onFocusedCard = { focusedCard = it }, onPlayQueued = onPlayQueued, onScrolled = { homeScrolled = it }, onRoom = onRoom)
                            Section.Search -> searchContent()
                            Section.Watchlist -> WatchlistContent(state.watchlist, onOpen, onRetryWatchlist)
                            Section.Playlists -> PlaylistsContent(state.playlists, onOpenPlaylist, onRetryPlaylists)
                            Section.Settings -> settingsContent()
                            is Section.Library -> LibraryContent(state.library, state.libraryTab, state.libraryExtras, onLibraryTab, onOpen, onOpenCollection, onRetryLibrary, onRetryLibraryExtras, onNeedTitles, onLibraryQuery, onFolder, onFolderUp)
                        }
                    }
                }
            }
        }
        profileMenu?.let { at ->
            ProfileMenu(at, onSwitchProfile = { profileMenu = null; onSwitchProfile() }, onJoin = { profileMenu = null; joining = true }, onClose = { profileMenu = null })
        }
        if (joining) joinTogether { joining = false }
    }
}

/**
 * Diagnostic build only, not for merging: Home draws no backdrop, fades or dim layer, over the
 * plain page. The hero text and logo, shelves, scrolling and focus are unchanged, so a Chromecast
 * frame-timing run against build 153 shows how much of Home's cost is the backdrop.
 */
private const val HomePlainBackground = true

/** The profile's menu at the top of the sidebar, when Watch Together is on: as the web's profile menu. */
@Composable
private fun ProfileMenu(at: androidx.compose.ui.geometry.Rect, onSwitchProfile: () -> Unit, onJoin: () -> Unit, onClose: () -> Unit) {
    val first = remember { FocusRequester() }
    org.arcmediaserver.android.ui.components.Popover(anchor = at, onClose = onClose, width = 260.dp) {
        org.arcmediaserver.android.ui.components.PopoverItem(stringResource(R.string.nav_switch_profile), null, focus = first, onClick = onSwitchProfile)
        org.arcmediaserver.android.ui.components.PopoverItem(stringResource(R.string.together_join_menu), ArcIcons.Users, onClick = onJoin)
    }
    LaunchedEffect(Unit) { first.requestWhenReady() }
}

/**
 * The sidebar, as the Apple TV app's (owner, 31 October): closed, a column of small icons; open,
 * a floating rounded panel over the page with the profile and the time at the top, then Search,
 * Home and the libraries, and Settings at the foot. Entries are compact (34dp) so ten or so
 * libraries fit without scrolling.
 */
@Composable
private fun NavigationDrawerScope.Sidebar(
    frame: BrowseFrame,
    section: Section,
    value: DrawerValue,
    onSection: (Section) -> Unit,
    /** The profile was chosen; where its entry is, for a menu beside it. */
    onProfile: (androidx.compose.ui.geometry.Rect?) -> Unit,
    onSignOut: () -> Unit,
) {
    val open = value == DrawerValue.Open
    val panel = RoundedCornerShape(24.dp)
    // As the Apple TV app: the panel grows out, the icons ride out with its edge and the names
    // slide in beside them (owner, 31 October).
    val width by androidx.compose.animation.core.animateDpAsState(if (open) 220.dp else 52.dp, SidebarEasing, label = "width")
    val shown by androidx.compose.animation.core.animateFloatAsState(if (open) 1f else 0f, SidebarEasingF, label = "panel")
    val logoInset by androidx.compose.animation.core.animateDpAsState(if (open) 15.dp else 10.dp, SidebarEasing, label = "logo-inset")
    val logoWidth by androidx.compose.animation.core.animateDpAsState(if (open) 136.dp else 20.dp, SidebarEasing, label = "logo-width")
    val logoHeight by androidx.compose.animation.core.animateDpAsState(if (open) 36.dp else 20.dp, SidebarEasing, label = "logo-height")
    Box(Modifier.fillMaxHeight().padding(12.dp)) {
        Column(
            Modifier
                .fillMaxHeight()
                .width(width)
                .shadow(30.dp * shown, panel)
                .background(SidebarColor.copy(alpha = SidebarColor.alpha * shown), panel)
                .border(1.dp, Color.White.copy(alpha = 0.08f * shown), panel)
                .clip(panel)
                .padding(horizontal = 6.dp, vertical = 10.dp)
                .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // Match the entries' animated leading inset; expand the mark into Arc's full logo.
            Box(Modifier.height(44.dp).padding(start = logoInset), contentAlignment = Alignment.CenterStart) {
                Icon(
                    androidx.compose.ui.res.painterResource(if (open) R.drawable.arc_logo else R.drawable.arc_mark),
                    contentDescription = stringResource(R.string.app_name),
                    tint = Color.White,
                    modifier = Modifier.width(logoWidth).height(logoHeight),
                )
            }
            Spacer(Modifier.height(4.dp))
            val profile = frame.profile
            if (profile != null && frame.canSwitchProfile) {
                // The profile and the time, at the top as the Apple TV app's; choosing it switches
                // profile (or, with Watch Together, offers that and Join Watch Together).
                val bounds = remember { arrayOfNulls<androidx.compose.ui.geometry.Rect>(1) }
                SidebarEntry(
                    leading = { Avatar(profile, 22.dp) },
                    label = profile.name,
                    extra = { SidebarClock() },
                    selected = false,
                    onClick = { onProfile(bounds[0]) },
                    modifier = Modifier.onGloballyPositioned { bounds[0] = it.boundsInRoot() },
                )
            } else {
                // A friend's TV has no profile picker, so keeps Sign Out here.
                SidebarItem(ArcIcons.SignOut, stringResource(R.string.nav_sign_out), false, onSignOut)
            }
            Spacer(Modifier.height(12.dp))
            SidebarItem(ArcIcons.Search, stringResource(R.string.nav_search), section == Section.Search) { onSection(Section.Search) }
            SidebarItem(ArcIcons.Home, stringResource(R.string.nav_home), section == Section.Home) { onSection(Section.Home) }
            // The Watchlist sits among the libraries where the profile put it (as Plex pins it).
            val watchlist: @Composable () -> Unit = {
                SidebarItem(ArcIcons.Bookmark, stringResource(R.string.row_watchlist), section == Section.Watchlist) { onSection(Section.Watchlist) }
            }
            frame.libraries.forEachIndexed { i, lib ->
                if (i == frame.watchlistAt) watchlist()
                SidebarItem(libraryIcon(lib), lib.name, (section as? Section.Library)?.id == lib.id) { onSection(Section.Library(lib.id)) }
            }
            if (frame.watchlistAt >= frame.libraries.size) watchlist()
            // Playlists after the libraries, as Plex and the web list them.
            SidebarItem(ArcIcons.ListVideo, stringResource(R.string.nav_playlists), section == Section.Playlists) { onSection(Section.Playlists) }
            Spacer(Modifier.weight(1f))
            SidebarItem(ArcIcons.Settings, stringResource(R.string.nav_settings), section == Section.Settings) { onSection(Section.Settings) }
        }
    }
}

/** The open sidebar's colour: a near-opaque grey panel, as the Apple TV app's. */
private val SidebarColor = Color(0xF724242C)
private val SidebarCurve = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0.9f, 0.3f, 1f)
private val SidebarEasing = androidx.compose.animation.core.tween<Dp>(320, easing = SidebarCurve)
private val SidebarEasingF = androidx.compose.animation.core.tween<Float>(320, easing = SidebarCurve)

/** The time, beside the profile's name, updated each minute. */
@Composable
private fun SidebarClock() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000 - now % 60_000)
            now = System.currentTimeMillis()
        }
    }
    Text(android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(now)), style = MaterialTheme.typography.labelMedium)
}

@Composable
private fun NavigationDrawerScope.SidebarItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) =
    SidebarEntry({ Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) }, label, null, selected, onClick)

/**
 * One sidebar entry, 34dp tall: the icon alone while the sidebar is closed, the label beside it
 * (and [extra], dimmed, on the right) once it opens. Drawn here because Compose for TV's drawer
 * item is 56dp.
 */
@Composable
private fun NavigationDrawerScope.SidebarEntry(
    leading: @Composable () -> Unit,
    label: String,
    extra: (@Composable () -> Unit)?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.tv.material3.Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(34.dp),
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color.White.copy(alpha = 0.12f) else Color.Transparent,
            contentColor = if (selected) ArcColors.Foreground else ArcColors.Muted,
            focusedContainerColor = ArcColors.Foreground,
            focusedContentColor = ArcColors.Background,
        ),
    ) {
        val open = hasFocus
        val inset by androidx.compose.animation.core.animateDpAsState(if (open) 15.dp else 10.dp, SidebarEasing, label = "inset")
        val slide by androidx.compose.animation.core.animateDpAsState(if (open) 0.dp else (-20).dp, SidebarEasing, label = "slide")
        val fade by androidx.compose.animation.core.animateFloatAsState(
            if (open) 1f else 0f,
            if (open) androidx.compose.animation.core.tween(220, delayMillis = 60) else androidx.compose.animation.core.tween(120),
            label = "fade",
        )
        Row(Modifier.fillMaxSize().padding(start = inset, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { leading() }
            if (open || fade > 0f) {
                Row(
                    Modifier.weight(1f).graphicsLayer { alpha = fade; translationX = slide.toPx() },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.width(11.dp))
                    Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip, modifier = Modifier.weight(1f))
                    if (extra != null) {
                        Spacer(Modifier.width(6.dp))
                        androidx.compose.runtime.CompositionLocalProvider(
                            androidx.tv.material3.LocalContentColor provides androidx.tv.material3.LocalContentColor.current.copy(alpha = 0.6f),
                        ) { extra() }
                    }
                }
            }
        }
    }
}

fun libraryIcon(lib: Library): ImageVector = when (lib.kind) {
    "shows" -> ArcIcons.Tv
    "videos" -> ArcIcons.Clapperboard
    else -> ArcIcons.Film
}

@Composable
fun rowTitle(spec: HomeRowSpec): String = when (val s = spec.source) {
    is RowSource.Continue -> stringResource(R.string.row_continue)
    RowSource.NextUp -> stringResource(R.string.row_next_up)
    RowSource.Watchlist -> stringResource(R.string.row_watchlist)
    is RowSource.Recent -> stringResource(R.string.row_recent, s.library.name)
    is RowSource.Custom -> s.title
    is RowSource.Seasonal -> stringResource(
        when (s.pick) {
            org.arcmediaserver.android.data.SeasonalPick.Romance -> R.string.row_seasonal_romance
            org.arcmediaserver.android.data.SeasonalPick.Horror -> R.string.row_seasonal_horror
            org.arcmediaserver.android.data.SeasonalPick.Christmas -> R.string.row_seasonal_christmas
        },
    )
    is RowSource.Collection -> s.title.ifBlank { stringResource(R.string.row_collection) }
    is RowSource.Playlist -> s.title.ifBlank { stringResource(R.string.row_playlist) }
    RowSource.Queue -> stringResource(R.string.row_queue)
    RowSource.WatchTogether -> stringResource(R.string.together_row_title)
}

/**
 * Home, as the Apple TV app's: the hero describes the focused title (its logo or name, details
 * and overview over its artwork) with the profile's rows beneath, Continue Watching first, just
 * under it. Focus starts on the first card, and moving along a row changes the hero. The hero
 * has no buttons of its own: Select on a card opens its title (a play queue card plays on), and
 * holding Select gives its actions. Further down, the hero scrolls away over a dimmed copy of the
 * artwork. Empty rows are left out.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeContent(
    rows: List<HomeRowState>,
    onOpen: (Item) -> Unit,
    onRetryRow: (HomeRowSpec) -> Unit,
    onFocusedCard: (MediaCard?) -> Unit,
    onPlayQueued: (Item, String) -> Unit = { _, _ -> },
    /** Whether the page has scrolled past the hero (the backdrop dims). */
    onScrolled: (Boolean) -> Unit = {},
    /** A Watch Together card: the room's choices. */
    onRoom: (String) -> Unit = {},
) {
    // Titles taken out of Continue Watching leave that row at once, without reloading it.
    val menu = org.arcmediaserver.android.ui.components.LocalCardMenu.current
    @Suppress("NAME_SHADOWING")
    val rows = rows.map { r ->
        val cards = r.cards
        if (menu != null && r.spec.source is RowSource.Continue && cards is LoadState.Ready) r.copy(cards = LoadState.Ready(cards.value.filterNot { menu.offContinue(it.item.id) })) else r
    }
    val visible = rows.filter { r -> !(r.cards is LoadState.Ready && r.cards.value.isEmpty()) }
    if (rows.isNotEmpty() && rows.all { it.cards is LoadState.Ready && it.cards.value.isEmpty() }) {
        EmptyHome()
        return
    }
    if (rows.isEmpty()) {
        LoadingPane()
        return
    }
    // Only the first shelf drives the hero. Keep its last backdrop behind the lower shelves
    // so that moving focus across the dimmed page doesn't load and draw full-screen images.
    val firstReady = visible.firstOrNull { it.cards is LoadState.Ready }
    val heroCards = (firstReady?.cards as? LoadState.Ready)?.value.orEmpty()
    var heroKey by remember(firstReady?.spec?.id) { mutableStateOf<String?>(null) }
    val hero = heroCards.firstOrNull { it.key == heroKey } ?: heroCards.firstOrNull()
    val list = rememberLazyListState()
    val scrolled by remember { androidx.compose.runtime.derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 40 } }
    LaunchedEffect(hero?.key) { onFocusedCard(hero) }
    LaunchedEffect(scrolled) { onScrolled(scrolled) }
    val firstCard = remember { FocusRequester() }
    // The row last focused, kept while away (a title's page, the player), to come back to.
    var lastRow by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    val rowFocus = remember { mutableMapOf<String, FocusRequester>() }
    val shelfPosition by rememberUpdatedState<(String) -> Int> { id ->
        // At the first shelf the hero stays visible. Other shelves go to the top of the list.
        val index = visible.indexOfFirst { it.spec.id == id }
        if (index < 0 || visible[index] === firstReady) 0 else index + if (hero != null) 1 else 0
    }
    // Focus alone can move the scroll position without recomposing all the shelf definitions.
    LaunchedEffect(list) {
        snapshotFlow { lastRow }.collectLatest { id ->
            if (id != null) list.animateScrollToItem(shelfPosition(id))
        }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides ScrollByShelf) {
        LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 36.dp)) {
            if (hero != null) {
                item(key = "hero") {
                    Box(Modifier.fillParentMaxHeight(HeroHeight).fillMaxWidth(), contentAlignment = Alignment.BottomStart) {
                        HomeHero(hero)
                    }
                }
            }
            items(visible, key = { it.spec.id }) { row ->
                val isFirst = row === firstReady
                Box(
                    Modifier.padding(top = 4.dp).onFocusChanged {
                        if (it.hasFocus) lastRow = row.spec.id
                    },
                ) {
                    HomeRow(
                        row,
                        // A queue card plays on from its entry; every other card opens its title.
                        onOpen = { card ->
                            val room = card.roomId()
                            when {
                                room != null -> onRoom(room)
                                card.queueEntryId() != null -> onPlayQueued(card.item, card.queueEntryId()!!)
                                else -> onOpen(card.item)
                            }
                        },
                        onFocused = { if (isFirst) heroKey = it.key },
                        onRetry = { onRetryRow(row.spec) },
                        firstCard = if (isFirst) firstCard else null,
                        rowFocus = rowFocus.getOrPut(row.spec.id) { FocusRequester() },
                    )
                }
            }
        }
    }
    // Arriving, focus goes to the first card (Continue Watching's first); coming back, to the row
    // last used. Never left in the sidebar (which would open it).
    var focusedOnce by remember { mutableStateOf(false) }
    val readyIds = visible.filter { it.cards is LoadState.Ready }.map { it.spec.id }
    LaunchedEffect(readyIds.isNotEmpty()) {
        if (focusedOnce || readyIds.isEmpty()) return@LaunchedEffect
        val back = lastRow?.takeIf { it in readyIds }
        focusedOnce = if (back != null) {
            list.scrollToItem(shelfPosition(back))
            rowFocus.getOrPut(back) { FocusRequester() }.requestWhenReady()
        } else {
            firstCard.requestWhenReady()
        }
    }
}

/** The hero's share of the screen: the first row's cards show in full just under it. */
private const val HeroHeight = 0.6f

/** The focused title's details, bottom left over its artwork (the page draws the artwork). */
@Composable
private fun HomeHero(card: MediaCard) {
    val item = card.item
    Box(Modifier.fillMaxSize()) {
        Crossfade(card.key, animationSpec = tween(300), label = "hero", modifier = Modifier.align(Alignment.BottomStart)) { _ ->
            Column(Modifier.padding(start = 48.dp, end = 48.dp, bottom = 12.dp).widthIn(max = 560.dp)) {
                TitleLogo(item, maxHeight = 90.dp, maxWidth = 380.dp, textStyle = MaterialTheme.typography.displayMedium)
                Spacer(Modifier.height(10.dp))
                val meta = listOfNotNull(
                    if (item.isEpisode) episodeWithTitle(item) else null,
                    item.year?.toString()?.takeIf { !item.isEpisode },
                    item.runtimeMin?.takeIf { it > 0 }?.let { runtimeText(it) },
                    item.genres.take(2).joinToString(", ").takeIf { it.isNotBlank() },
                ).filter { it.isNotBlank() }
                // The age rating is among the labels after this line.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (meta.isNotEmpty()) {
                        Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = ArcColors.Foreground.copy(alpha = 0.75f), maxLines = 1)
                        Spacer(Modifier.width(10.dp))
                    }
                    org.arcmediaserver.android.ui.components.FormatBadges(org.arcmediaserver.android.ui.components.formatLabels(item))
                }
                org.arcmediaserver.android.ui.components.spoilerSafeOverview(item)?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = ArcColors.Foreground.copy(alpha = 0.85f), maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun EmptyHome() {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.home_empty_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.home_empty_body), style = MaterialTheme.typography.bodyLarge, color = ArcColors.Muted)
    }
}

/** Whose palette matches [backdropOf]: the show's for an episode shown with its show's backdrop. */
fun paletteIdOf(item: Item): String = if (item.isEpisode && item.images?.seriesBackdrop != null && item.parentId != null) item.parentId else item.id

/** The backdrop for a title: its own, or its show's for an episode. */
fun backdropOf(item: Item): String? = item.images?.let { if (item.isEpisode) it.seriesBackdrop ?: it.backdropFull ?: it.backdrop else it.backdropFull ?: it.backdrop }

/** The clear logo for a title: its own, or its show's for an episode. */
fun logoOf(item: Item): String? = item.images?.let { if (item.isEpisode) it.seriesLogo ?: it.logo else it.logo }

/**
 * The whole-screen backdrop behind Home and a title's page, as Plex's TV app: the art sits in the
 * top right, whole (16:9, 70% of the width), over an Ultra Blur wash of its own colours (from
 * Arc; the plain page without them), and its left and bottom edges fade into it, so the picture
 * stays clear and the text beside and under it sits on its colours.
 */
@Composable
fun ImmersiveBackdrop(item: Item?) {
    val resolve = LocalImageUrl.current
    val url = item?.let(::backdropOf)
    Box(Modifier.fillMaxSize()) {
        UltraBlur(item?.let(::paletteIdOf))
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .fillMaxWidth(0.7f)
                .aspectRatio(16f / 9f)
                // The art's edges fade to see-through, so the wash shows through them.
                .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(Brush.horizontalGradient(0f to Color.Transparent, 0.45f to Color.Black.copy(alpha = 0.75f), 0.7f to Color.Black), blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
                    drawRect(Brush.verticalGradient(0.5f to Color.Black, 0.85f to Color.Black.copy(alpha = 0.3f), 1f to Color.Transparent), blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
                },
        ) {
            Crossfade(url, animationSpec = tween(600), label = "backdrop") { u ->
                if (u != null) {
                    AsyncImage(model = resolve(u), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

/** A title's clear logo, or its name in large type when it has none (or it doesn't load). */
@Composable
fun TitleLogo(item: Item, maxHeight: Dp = 96.dp, maxWidth: Dp = 420.dp, textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.displaySmall) {
    val title = if (item.isEpisode) item.seriesTitle ?: item.title else item.title
    val logo = logoOf(item)
    var failed by remember(logo) { mutableStateOf(false) }
    if (logo != null && !failed) {
        AsyncImage(
            model = LocalImageUrl.current(logo),
            contentDescription = title,
            contentScale = ContentScale.Fit,
            alignment = Alignment.BottomStart,
            onError = { failed = true },
            modifier = Modifier.height(maxHeight).widthIn(max = maxWidth),
        )
    } else {
        Text(title, style = textStyle, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = maxWidth + 80.dp))
    }
}

/** What the focused title is: logo, a line of details and a short summary, top left. */
@Composable
private fun Overview(card: MediaCard) {
    val item = card.item
    Crossfade(card.key, animationSpec = tween(300), label = "overview") { _ ->
        TitleSummary(
            item,
            listOfNotNull(
                if (item.isEpisode) episodeWithTitle(item) else null,
                item.year?.toString()?.takeIf { !item.isEpisode },
                item.contentRating,
                item.runtimeMin?.takeIf { it > 0 }?.let { runtimeText(it) },
                item.genres.take(2).joinToString(", ").takeIf { it.isNotBlank() },
            ),
            Modifier.padding(start = 48.dp, top = 36.dp),
        )
    }
}

/**
 * A title's logo (or name), one line of details and three lines of its description: the same on
 * Home and on the title's page, at the same place, so moving between them doesn't jump.
 */
@Composable
fun TitleSummary(item: Item, meta: List<String>, modifier: Modifier = Modifier, labels: Boolean = false) {
    Column(modifier.width(480.dp)) {
        TitleLogo(item, maxHeight = 72.dp, maxWidth = 360.dp)
        Spacer(Modifier.height(14.dp))
        val line = meta.filter { it.isNotBlank() }
        if (line.isNotEmpty()) Text(line.joinToString("  ·  "), style = MaterialTheme.typography.titleSmall, color = ArcColors.Foreground.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        // On a title's page only (as the web): its labels, the age rating, picture and sound.
        val shown = if (labels) org.arcmediaserver.android.ui.components.formatLabels(item) else emptyList()
        if (shown.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            org.arcmediaserver.android.ui.components.FormatBadges(shown)
        }
        Spacer(Modifier.height(8.dp))
        org.arcmediaserver.android.ui.components.spoilerSafeOverview(item)?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = ArcColors.Foreground.copy(alpha = 0.8f), maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun runtimeText(minutes: Int): String =
    if (minutes >= 60) stringResource(R.string.hours_minutes, minutes / 60, minutes % 60) else stringResource(R.string.minutes, minutes)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeRow(row: HomeRowState, onOpen: (MediaCard) -> Unit, onFocused: (MediaCard) -> Unit, onRetry: () -> Unit, firstCard: FocusRequester?, rowFocus: FocusRequester) {
    Column {
        Text(rowTitle(row.spec), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 48.dp, bottom = 4.dp))
        when (val cards = row.cards) {
            LoadState.Loading -> Box(Modifier.padding(start = 48.dp).height(120.dp), contentAlignment = Alignment.CenterStart) { Spinner(size = 28.dp) }
            is LoadState.Failed -> Row(Modifier.padding(start = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.row_failed) + " " + cards.error.text(), style = MaterialTheme.typography.bodyMedium, color = ArcColors.Muted, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(16.dp))
                androidx.tv.material3.OutlinedButton(onClick = onRetry, modifier = Modifier.focusRequester(rowFocus)) { Text(stringResource(R.string.action_retry)) }
            }
            // Vertical padding leaves room for the focused card to grow without being clipped.
            is LoadState.Ready -> CompositionLocalProvider(
                LocalBringIntoViewSpec provides minimalBringIntoView(ShelfPadding - ShelfInset, after = ShelfPadding),
                org.arcmediaserver.android.ui.components.LocalInContinueRow provides (row.spec.source is RowSource.Continue),
            ) { LazyRow(
                // Focusing the row returns to the card last focused in it (or its first).
                modifier = Modifier.padding(start = ShelfInset).clipToBounds().focusRequester(rowFocus).focusRestorer(),
                contentPadding = PaddingValues(start = ShelfPadding - ShelfInset, end = ShelfPadding, top = 14.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(cards.value, key = { it.key }) { card ->
                    val mod = Modifier.onFocusChanged { if (it.hasFocus) onFocused(card) }
                    val style = row.spec.style
                    MediaCardView(
                        card,
                        landscape = style == CardStyle.Thumb,
                        backdrop = style == CardStyle.Backdrop,
                        seasonPoster = row.spec.seasonPosters,
                        width = when (style) { CardStyle.Thumb -> HomeThumbWidth; CardStyle.Backdrop -> HomeBackdropWidth; CardStyle.Poster -> HomePosterWidth },
                        onClick = { onOpen(card) },
                        modifier = if (firstCard != null && card === cards.value.first()) mod.focusRequester(firstCard) else mod,
                    )
                }
            } }
        }
    }
}

/**
 * A library: its name and tabs, as Plex's TV app and the web (Recommended, its titles, Collections),
 * then the chosen tab. Moving along the tabs switches what's shown. The titles have the web's sort
 * and filters, and an A–Z bar down the right while they're sorted by title.
 */
@Composable
fun LibraryContent(
    state: LoadState<LibraryPage>,
    tab: LibraryTab,
    extras: LibraryExtras,
    onTab: (LibraryTab) -> Unit,
    onOpen: (Item) -> Unit,
    onOpenCollection: (org.arcmediaserver.android.data.CollectionRef) -> Unit,
    onRetry: () -> Unit,
    onRetryExtras: () -> Unit,
    onNeed: (Int) -> Unit,
    onQuery: (LibraryQuery) -> Unit = {},
    onFolder: (path: String, focus: String?) -> Unit = { _, _ -> },
    onFolderUp: () -> Unit = {},
) {
    Loaded(state, onRetry) { page ->
        val tabs = libraryTabs(page.library, extras)
        val shown = tab.takeIf { it in tabs } ?: LibraryTab.Titles
        val first = remember { FocusRequester() }
        var panel by remember { mutableStateOf<LibraryPanel?>(null) }
        // Where Sort and Filters are, so their lists open under them.
        var sortAt by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
        var filterAt by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
        val sortButton = remember { FocusRequester() }
        val filterButton = remember { FocusRequester() }
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                // A fixed height, so the name and tabs stay put whether or not the tab has Sort
                // and Filters buttons (they're taller than the tabs).
                Row(Modifier.padding(start = 48.dp, top = 28.dp, bottom = 4.dp, end = 48.dp).height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(page.library.name, style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.width(24.dp))
                    if (tabs.size > 1) {
                        // Recommended, Library, Collections: Plex's names for a library's tabs.
                        val labels = tabs.map { stringResource(when (it) { LibraryTab.Recommended -> R.string.library_tab_recommended; LibraryTab.Titles -> R.string.library_tab_library; LibraryTab.Folders -> R.string.library_tab_folders; LibraryTab.Collections -> R.string.library_tab_collections }) }
                        PillTabs(labels, tabs.indexOf(shown), onSelect = { onTab(tabs[it]) })
                    }
                    if (shown == LibraryTab.Titles) {
                        Spacer(Modifier.weight(1f))
                        Text(pluralStringResource(R.plurals.library_count, page.total, page.total), style = MaterialTheme.typography.titleMedium, color = ArcColors.Muted)
                        Spacer(Modifier.width(16.dp))
                        HeaderButton(sortLabel(page.query.sort), Modifier.focusRequester(sortButton).onGloballyPositioned { sortAt = it.boundsInRoot() }) { panel = LibraryPanel.Sort }
                        Spacer(Modifier.width(10.dp))
                        val n = page.query.filterCount
                        HeaderButton(if (n > 0) pluralStringResource(R.plurals.library_filters_on, n, n) else stringResource(R.string.library_filters), Modifier.focusRequester(filterButton).onGloballyPositioned { filterAt = it.boundsInRoot() }) { panel = LibraryPanel.Filters }
                    }
                }
                when (shown) {
                    LibraryTab.Titles -> if (page.total == 0) {
                        Text(stringResource(if (page.query.filterCount > 0) R.string.library_nothing_matches else R.string.library_empty), style = MaterialTheme.typography.bodyLarge, color = ArcColors.Muted, modifier = Modifier.padding(start = 48.dp, top = 24.dp))
                    } else {
                        TitlesWithLetters(page, first, onOpen, onNeed)
                    }
                    LibraryTab.Recommended -> Loaded(extras.recommended, onRetryExtras) { rows -> RecommendedView(rows, page.library, first, onOpen) }
                    LibraryTab.Folders -> FoldersView(
                        page.library, extras.folderPath, extras.folders, extras.folderFocus, first,
                        onOpenFolder = { onFolder(it, "") }, onUp = onFolderUp, onOpen = onOpen, onRetry = { onFolder(extras.folderPath, extras.folderFocus) },
                    )
                    LibraryTab.Collections -> Loaded(extras.collections, onRetryExtras) { collections -> CollectionGrid(collections, first, onOpenCollection) }
                }
            }
            when (panel) {
                LibraryPanel.Sort -> SortPanel(page.query, onChoose = onQuery, onClose = { panel = null; runCatching { sortButton.requestFocus() } }, anchor = sortAt)
                LibraryPanel.Filters -> FilterPanel(page.query, extras.facets, onChoose = onQuery, onClose = { panel = null; runCatching { filterButton.requestFocus() } }, anchor = filterAt)
                null -> Unit
            }
        }
        LaunchedEffect(page.library.id, page.query) { first.requestWhenReady() }
    }
}

/** A small rounded button in a screen's header (a library's Sort and Filters). */
@Composable
internal fun HeaderButton(label: String, modifier: Modifier = Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit) {
    // A glass pill, white when focused, as the Apple TV app's.
    androidx.tv.material3.Button(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
        scale = androidx.tv.material3.ButtonDefaults.scale(focusedScale = 1.04f),
        colors = androidx.tv.material3.ButtonDefaults.colors(
            containerColor = ArcColors.Pill, contentColor = ArcColors.Foreground,
            focusedContainerColor = Color.White, focusedContentColor = Color.Black,
        ),
    ) {
        if (icon != null) {
            androidx.tv.material3.Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * The library's titles with the A–Z bar beside them (title sort only, as the web's LetterRail):
 * choosing a letter moves to its first title, loading that part of the list if needed. Letters
 * with no titles are left out.
 */
@Composable
private fun TitlesWithLetters(page: LibraryPage, first: FocusRequester, onOpen: (Item) -> Unit, onNeed: (Int) -> Unit) {
    val grid = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val scope = rememberCoroutineScope()
    var target by remember(page.query) { mutableStateOf<Int?>(null) }
    val offsets = remember(page.letters, page.query.reverse) { letterOffsets(page.letters, page.query.reverse) }
    Box(Modifier.fillMaxSize()) {
        TitleGrid(page.total, { page.items[it] }, landscape = page.library.kind == "videos", first, onOpen, onNeed, grid, target)
        if (page.query.sort == "title" && offsets.size > 1) {
            // The grid already leaves a 48dp gutter. Keep the rail there so it can't resize posters.
            LetterBar(offsets.keys.toList(), Modifier.align(Alignment.TopEnd)) { letter ->
                val at = offsets.getValue(letter)
                onNeed(at)
                target = at
                scope.launch { grid.scrollToItem(at) }
            }
        }
    }
}

/** Where each letter's first title is in the list, in the list's order (web letterOffsets). */
fun letterOffsets(letters: List<org.arcmediaserver.android.data.LetterCount>, reverse: Boolean): LinkedHashMap<String, Int> {
    val out = LinkedHashMap<String, Int>()
    var at = 0
    for (l in if (reverse) letters.asReversed() else letters) {
        val key = l.letter.uppercase().takeIf { it.length == 1 && it[0] in 'A'..'Z' } ?: "#"
        if (key !in out) out[key] = at
        at += l.count
    }
    return out
}

@Composable
private fun LetterBar(letters: List<String>, modifier: Modifier, onJump: (String) -> Unit) {
    androidx.compose.foundation.lazy.LazyColumn(
        modifier.width(36.dp).fillMaxHeight().padding(end = 12.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(letters, key = { it }) { letter ->
            androidx.tv.material3.Surface(
                onClick = { onJump(letter) },
                modifier = Modifier.size(24.dp, 17.dp),
                shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = ArcColors.Muted,
                    focusedContainerColor = ArcColors.Foreground,
                    focusedContentColor = ArcColors.Background,
                ),
                scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1.15f),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(letter, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/**
 * Titles in a grid: six posters across and two whole rows on screen, as Plex (owner, 30
 * October); home videos four wide. Rows scroll only as far as needed, so captions stay in view
 * (no TV pivot), with a small margin so the second row isn't pushed up for no reason. Shared by a
 * library's titles and a collection's page.
 */
@Composable
fun TitleGrid(items: List<Item>, landscape: Boolean, first: FocusRequester, onOpen: (Item) -> Unit) =
    TitleGrid(items.size, { items.getOrNull(it) }, landscape, first, onOpen)

/**
 * The grid over [count] positions, of which [itemAt] knows those loaded: a position not loaded
 * yet shows an empty card (the same card, so the focus stays when its title arrives) and asks for
 * its page through [onNeed]. [target] is a position to move the focus to (a letter's first title).
 */
@Composable
fun TitleGrid(
    count: Int,
    itemAt: (Int) -> Item?,
    landscape: Boolean,
    first: FocusRequester,
    onOpen: (Item) -> Unit,
    onNeed: (Int) -> Unit = {},
    state: androidx.compose.foundation.lazy.grid.LazyGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState(),
    target: Int? = null,
) = CardGrid(count, landscape, key = { it }, state = state) { i, mod ->
    val item = itemAt(i)
    if (item == null) LaunchedEffect(i) { onNeed(i) }
    val card = item?.let { cardFor(it) } ?: placeholderCard(i)
    val self = if (i == target) remember(target) { FocusRequester() } else null
    if (self != null) LaunchedEffect(target) { self.requestWhenReady() }
    MediaCardView(
        card,
        landscape = landscape,
        onClick = { if (item != null) onOpen(item) },
        modifier = mod
            .then(if (i == 0) Modifier.focusRequester(first) else Modifier)
            .then(if (self != null) Modifier.focusRequester(self) else Modifier)
            // Load ahead of the focus, so titles are there by the time the grid reaches them.
            .onFocusChanged { if (it.hasFocus) onNeed(i + 30) },
    )
}

/** An empty card for a position whose title hasn't loaded yet. */
private fun placeholderCard(i: Int) = MediaCard(key = "slot:$i", item = Item(id = "", kind = "placeholder", title = ""), poster = null, thumb = null, progress = null)

private enum class LibraryPanel { Sort, Filters }

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CardGrid(
    count: Int,
    landscape: Boolean,
    key: (Int) -> Any,
    state: androidx.compose.foundation.lazy.grid.LazyGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState(),
    card: @Composable (Int, Modifier) -> Unit,
) {
    // Whole rows at a time, captions included (the title and year under each card, then the gap).
    CompositionLocalProvider(LocalBringIntoViewSpec provides rowsBringIntoView(top = 8.dp, below = 58.dp)) {
        LazyVerticalGrid(
            state = state,
            columns = GridCells.Fixed(if (landscape) 4 else 6),
            contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 8.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(count, key = key) { i -> card(i, Modifier.fillMaxWidth()) }
        }
    }
}

/** A library's collections, as posters; each opens its own page. */
@Composable
private fun CollectionGrid(collections: List<org.arcmediaserver.android.data.CollectionRef>, first: FocusRequester, onOpen: (org.arcmediaserver.android.data.CollectionRef) -> Unit) {
    // Folder collections (home videos) are wide, as their videos are.
    val landscape = collections.isNotEmpty() && collections.all { it.kind == "folder" }
    CardGrid(collections.size, landscape, key = { collections[it].id }) { i, mod ->
        val c = collections[i]
        val card = MediaCard(key = c.id, item = Item(id = c.id, kind = "collection", title = c.title, images = c.images), poster = c.images?.primary, thumb = c.images?.backdrop ?: c.images?.primary, progress = null)
        MediaCardView(card, landscape = landscape, onClick = { onOpen(c) }, modifier = if (i == 0) mod.focusRequester(first) else mod)
    }
}

/**
 * A library's Recommended view: the server's rows in its order (web Library.tsx), each titled in
 * the app's language from its type. Rows carrying on and episodes use thumbnails unless the server
 * owner chose a style.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecommendedView(rows: List<org.arcmediaserver.android.data.RecommendedRow>, library: Library, first: FocusRequester, onOpen: (Item) -> Unit) {
    if (rows.isEmpty()) {
        Text(stringResource(R.string.library_recommended_empty), style = MaterialTheme.typography.bodyLarge, color = ArcColors.Muted, modifier = Modifier.padding(start = 48.dp, top = 24.dp))
        return
    }
    val list = rememberLazyListState()
    var focusedShelf by remember { mutableStateOf<Int?>(null) }
    val rowCount by rememberUpdatedState(rows.size)
    LaunchedEffect(list) {
        snapshotFlow { focusedShelf }.collectLatest { index ->
            if (index != null && index < rowCount) list.animateScrollToItem(index)
        }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides ScrollByShelf) {
        LazyColumn(state = list, contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            itemsIndexed(rows, key = { i, r -> "$i:${r.type}:${r.name}:${r.genre}" }) { index, row ->
                val episodes = row.items.any { it.isEpisode }
                val style = row.style.ifEmpty { if (row.type == "continue" || episodes) "thumb" else "poster" }
                Shelf(
                    recommendedTitle(row, library),
                    modifier = Modifier.onFocusChanged { if (it.hasFocus) focusedShelf = index },
                    leadingInset = ShelfInset,
                ) {
                    itemsIndexed(row.items, key = { _, it -> it.id }) { i, item ->
                        MediaCardView(
                            cardFor(item),
                            landscape = style == "thumb",
                            backdrop = style == "arc",
                            // The same sizes as Home's rows, so a Backdrops row isn't poster-narrow.
                            width = when (style) { "thumb" -> HomeThumbWidth; "arc" -> HomeBackdropWidth; else -> HomePosterWidth },
                            onClick = { onOpen(item) },
                            modifier = if (index == 0 && i == 0) Modifier.focusRequester(first) else Modifier,
                        )
                    }
                }
            }
        }
    }
}

/** A Recommended row's title in the app's language, from what the server says it is (web recommendedTitle). */
@Composable
private fun recommendedTitle(row: org.arcmediaserver.android.data.RecommendedRow, library: Library): String {
    val shows = library.kind == "shows"
    val name = row.name.orEmpty()
    return when (row.type) {
        "continue" -> stringResource(R.string.rec_continue)
        "recently_released" -> stringResource(R.string.rec_recently_released)
        "recently_added" -> stringResource(R.string.rec_recently_added, row.name ?: library.name)
        "start_watching" -> stringResource(R.string.rec_start_watching)
        "because_you_watched" -> stringResource(R.string.rec_because_you_watched, name)
        "top_rated" -> stringResource(if (shows) R.string.rec_top_rated_shows else R.string.rec_top_rated_movies)
        "recently_watched" -> stringResource(if (shows) R.string.rec_recently_watched_episodes else R.string.rec_recently_watched)
        "rediscover" -> stringResource(R.string.rec_rediscover)
        "more_in_genre" -> stringResource(R.string.rec_more_in_genre, name)
        "top_in_genre" -> stringResource(if (shows) R.string.rec_top_shows_in_genre else R.string.rec_top_movies_in_genre, name)
        "top_by_person" -> stringResource(
            when {
                row.role == "director" && shows -> R.string.rec_top_shows_by
                row.role == "director" -> R.string.rec_top_movies_by
                shows -> R.string.rec_top_shows_with
                else -> R.string.rec_top_movies_with
            },
            name,
        )
        "top_unwatched" -> stringResource(R.string.rec_top_unwatched)
        else -> row.title
    }
}

// Keep the art lined up at 48dp while clipping the row just past the closed sidebar.
private val ShelfPadding = 48.dp
private val ShelfInset = 36.dp

/** Home's single row of cards: about seven posters across, with room for the heading and focus. */
private val HomePosterWidth = 132.dp
private val HomeThumbWidth = 236.dp
private val HomeBackdropWidth = 200.dp
// Heading, card padding, the poster and its two-line caption.
private val HomeBandHeight = 32.dp + 28.dp + HomePosterWidth * 1.5f + 50.dp

/**
 * The Watchlist from the sidebar: its name, then pills at the right of it as the Apple TV app's
 * (All, Movies or TV Shows; Date Added, Title or Release Date), then the titles in the library's grid.
 */
@Composable
fun WatchlistContent(state: LoadState<List<Item>>, onOpen: (Item) -> Unit, onRetry: () -> Unit) {
    Loaded(state, onRetry) { items ->
        val first = remember { FocusRequester() }
        var show by remember { mutableStateOf(org.arcmediaserver.android.data.WatchlistView.Show.All) }
        var sort by remember { mutableStateOf(org.arcmediaserver.android.data.WatchlistView.Sort.DateAdded) }
        var open by remember { mutableStateOf<String?>(null) }
        var showAt by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
        var sortAt by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
        val showButton = remember { FocusRequester() }
        val sortButton = remember { FocusRequester() }
        val shown = remember(items, show, sort) { org.arcmediaserver.android.data.WatchlistView.shown(items, show, sort) }
        val showNames = mapOf(
            org.arcmediaserver.android.data.WatchlistView.Show.All to stringResource(R.string.watchlist_show_all),
            org.arcmediaserver.android.data.WatchlistView.Show.Movies to stringResource(R.string.watchlist_show_movies),
            org.arcmediaserver.android.data.WatchlistView.Show.Shows to stringResource(R.string.watchlist_show_shows),
        )
        val sortNames = mapOf(
            org.arcmediaserver.android.data.WatchlistView.Sort.DateAdded to stringResource(R.string.watchlist_sort_added),
            org.arcmediaserver.android.data.WatchlistView.Sort.Title to stringResource(R.string.watchlist_sort_title),
            org.arcmediaserver.android.data.WatchlistView.Sort.ReleaseDate to stringResource(R.string.watchlist_sort_released),
        )
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(start = 48.dp, end = 48.dp, top = 28.dp, bottom = 4.dp).height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.row_watchlist), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.weight(1f))
                if (items.isNotEmpty()) {
                    HeaderButton(showNames.getValue(show), Modifier.focusRequester(showButton).onGloballyPositioned { showAt = it.boundsInRoot() }) { open = "show" }
                    Spacer(Modifier.width(10.dp))
                    HeaderButton(sortNames.getValue(sort), Modifier.focusRequester(sortButton).onGloballyPositioned { sortAt = it.boundsInRoot() }) { open = "sort" }
                }
            }
            if (items.isEmpty()) {
                Text(stringResource(R.string.watchlist_empty), style = MaterialTheme.typography.bodyLarge, color = ArcColors.Muted, modifier = Modifier.padding(start = 48.dp, top = 24.dp))
            } else {
                key(show, sort) { TitleGrid(shown, landscape = false, first, onOpen) }
            }
        }
        fun close(button: FocusRequester) {
            open = null
            button.requestFocus()
        }
        when (open) {
            "show" -> org.arcmediaserver.android.ui.components.Popover(showAt, onClose = { close(showButton) }, place = org.arcmediaserver.android.ui.components.PopoverPlace.Below, width = 260.dp) {
                org.arcmediaserver.android.ui.components.PopoverList {
                    showNames.forEach { (v, name) ->
                        val focus = remember { FocusRequester() }
                        org.arcmediaserver.android.ui.components.PopoverItem(name, if (v == show) ArcIcons.Check else null, focus = if (v == show) focus else null) { show = v; close(showButton) }
                        if (v == show) LaunchedEffect(Unit) { focus.requestWhenReady() }
                    }
                }
            }
            "sort" -> org.arcmediaserver.android.ui.components.Popover(sortAt, onClose = { close(sortButton) }, place = org.arcmediaserver.android.ui.components.PopoverPlace.Below, width = 260.dp) {
                org.arcmediaserver.android.ui.components.PopoverList {
                    sortNames.forEach { (v, name) ->
                        val focus = remember { FocusRequester() }
                        org.arcmediaserver.android.ui.components.PopoverItem(name, if (v == sort) ArcIcons.Check else null, focus = if (v == sort) focus else null) { sort = v; close(sortButton) }
                        if (v == sort) LaunchedEffect(Unit) { focus.requestWhenReady() }
                    }
                }
            }
        }
        LaunchedEffect(items.isEmpty()) { if (items.isNotEmpty()) first.requestWhenReady() }
    }
}
