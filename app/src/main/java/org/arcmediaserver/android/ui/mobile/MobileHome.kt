package org.arcmediaserver.android.ui.mobile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.arcmediaserver.android.R
import org.arcmediaserver.android.data.Item
import org.arcmediaserver.android.data.Library
import org.arcmediaserver.android.data.MediaCard
import org.arcmediaserver.android.data.RowSource
import org.arcmediaserver.android.data.cardFor
import org.arcmediaserver.android.data.queueEntryId
import org.arcmediaserver.android.ui.arcViewModel
import org.arcmediaserver.android.ui.browse.BrowseUiState
import org.arcmediaserver.android.ui.browse.BrowseViewModel
import org.arcmediaserver.android.ui.browse.LibraryPage
import org.arcmediaserver.android.ui.browse.Section
import org.arcmediaserver.android.ui.browse.rowTitle
import org.arcmediaserver.android.ui.components.ArcIcons
import org.arcmediaserver.android.ui.components.LoadState
import org.arcmediaserver.android.ui.profiles.Avatar
import org.arcmediaserver.android.ui.search.SearchGroup
import org.arcmediaserver.android.ui.search.SearchUiState
import org.arcmediaserver.android.ui.search.SearchViewModel
import org.arcmediaserver.android.ui.theme.ArcColors

/** The phone's tabs, as Android's bottom navigation (a navigation rail on a tablet). */
enum class MobileTab { Home, Libraries, Search }

/**
 * Home, Libraries and Search under one bar, with the profile at the top right (Switch Profile,
 * Settings, Sign Out), as Google's own apps put the account. Each tab keeps its place while a
 * title's page or the player is on top, and Back from a library returns to the list.
 */
@Composable
fun MobileShell(
    onOpen: (Item) -> Unit,
    onPlay: (Item, Long, String?) -> Unit,
    onPerson: (Long) -> Unit,
    onAccount: () -> Unit,
) {
    val browse = arcViewModel { BrowseViewModel(it) }
    val search = arcViewModel { SearchViewModel(it) }
    val state by browse.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(MobileTab.Home) }
    // The library open in the Libraries tab (-1 the list, -2 the Watchlist).
    var library by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(tab, library) {
        browse.show(
            when {
                tab != MobileTab.Libraries -> Section.Home
                library == WATCHLIST -> Section.Watchlist
                library != null -> Section.Library(library!!)
                else -> Section.Home
            },
        )
    }
    BackHandler(enabled = tab == MobileTab.Libraries && library != null) { library = null }
    BackHandler(enabled = tab != MobileTab.Home && !(tab == MobileTab.Libraries && library != null)) { tab = MobileTab.Home }
    val openCard: (MediaCard) -> Unit = { card ->
        val entry = card.queueEntryId()
        if (entry != null) onPlay(card.item, card.item.resumeMs, entry) else onOpen(card.item)
    }
    val profile = (state.frame as? LoadState.Ready)?.value?.profile
    MobileShellFrame(
        tab = tab,
        onTab = { t -> if (tab == t && t == MobileTab.Libraries) library = null; tab = t },
        profile = profile,
        topBar = !(tab == MobileTab.Libraries && library != null),
        onAccount = onAccount,
    ) {
        when (tab) {
            MobileTab.Home -> MobileHomeContent(state, onCard = openCard, onRetry = browse::load, onRetryRow = browse::retryRow)
            MobileTab.Libraries -> when (val id = library) {
                null -> LibrariesList(state, onOpen = { library = it.id }, onWatchlist = { library = WATCHLIST }, onRetry = browse::load)
                WATCHLIST -> MobileWatchlist(state, onBack = { library = null }, onOpen = onOpen, onRetry = browse::loadWatchlist)
                else -> MobileLibrary(state, onBack = { library = null }, onOpen = onOpen, onNeed = browse::needTitles, onRetry = { browse.loadLibrary(id) })
            }
            MobileTab.Search -> {
                val s by search.state.collectAsStateWithLifecycle()
                MobileSearch(s, onType = search::type, onCard = openCard, onPerson = onPerson)
            }
        }
    }
}

/**
 * The tabs' frame: Arc's logo and the profile at the top, the tabs in a bottom navigation bar on
 * a phone or a navigation rail at the side from 600dp (a tablet, or a phone held sideways).
 */
@Composable
fun MobileShellFrame(
    tab: MobileTab,
    onTab: (MobileTab) -> Unit,
    profile: org.arcmediaserver.android.data.Profile?,
    topBar: Boolean,
    onAccount: () -> Unit,
    content: @Composable () -> Unit,
) {
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    Row(Modifier.fillMaxSize()) {
        if (wide) {
            NavigationRail(containerColor = ArcColors.Background, header = { Spacer(Modifier.height(8.dp)) }) {
                MobileTab.entries.forEach { t ->
                    NavigationRailItem(selected = tab == t, onClick = { onTab(t) }, icon = { Icon(t.icon(), null) }, label = { Text(t.label()) })
                }
            }
        }
        Scaffold(
            modifier = Modifier.weight(1f),
            containerColor = ArcColors.Background,
            topBar = { if (topBar) ShellTopBar(profile, onAccount) },
            bottomBar = {
                if (!wide) NavigationBar(containerColor = ArcColors.Surface) {
                    MobileTab.entries.forEach { t ->
                        NavigationBarItem(selected = tab == t, onClick = { onTab(t) }, icon = { Icon(t.icon(), null) }, label = { Text(t.label()) })
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) { content() }
        }
    }
}

private const val WATCHLIST = -2L

@Composable
private fun MobileTab.label(): String = stringResource(
    when (this) {
        MobileTab.Home -> R.string.nav_home
        MobileTab.Libraries -> R.string.mobile_tab_libraries
        MobileTab.Search -> R.string.nav_search
    },
)

private fun MobileTab.icon(): ImageVector = when (this) {
    MobileTab.Home -> ArcIcons.Home
    MobileTab.Libraries -> ArcIcons.Clapperboard
    MobileTab.Search -> ArcIcons.Search
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShellTopBar(profile: org.arcmediaserver.android.data.Profile?, onAccount: () -> Unit) {
    TopAppBar(
        title = { Image(painterResource(R.drawable.arc_logo), contentDescription = stringResource(R.string.app_name), modifier = Modifier.height(24.dp).aspectRatio(136.02f / 36.2f)) },
        actions = {
            IconButton(onClick = onAccount) {
                if (profile != null) Avatar(profile, 32.dp) else Icon(ArcIcons.Settings, contentDescription = stringResource(R.string.nav_settings))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = ArcColors.Background),
    )
}

/**
 * Home's rows, as the profile arranged them in Arc (the same rows as the TV): Continue Watching
 * first with wide cards, then the rest in each row's own style. Rows still loading or empty are
 * left out; Watch Together's row waits for the phone's Watch Together.
 */
@Composable
fun MobileHomeContent(state: BrowseUiState, onCard: (MediaCard) -> Unit, onRetry: () -> Unit, onRetryRow: (org.arcmediaserver.android.data.HomeRowSpec) -> Unit) {
    when (val f = state.frame) {
        LoadState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is LoadState.Failed -> MobileError(f.error, onRetry)
        is LoadState.Ready -> {
            val rows = state.rows.filter { it.spec.source != RowSource.WatchTogether }
            val shown = rows.filter { (it.cards as? LoadState.Ready)?.value?.isNotEmpty() == true || it.cards is LoadState.Failed }
            if (shown.isEmpty() && rows.none { it.cards is LoadState.Loading }) {
                Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(stringResource(R.string.home_empty_title), style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.home_empty_body), style = MaterialTheme.typography.bodyMedium, color = ArcColors.Muted, textAlign = TextAlign.Center)
                }
                return
            }
            LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.fillMaxSize()) {
                items(shown, key = { it.spec.id }) { row ->
                    when (val c = row.cards) {
                        is LoadState.Ready -> MobileShelf(rowTitle(row.spec), c.value, landscape = row.spec.style != org.arcmediaserver.android.data.CardStyle.Poster, onClick = onCard)
                        is LoadState.Failed -> Column(Modifier.padding(horizontal = pageMargin())) {
                            Text(rowTitle(row.spec), style = MaterialTheme.typography.titleMedium)
                            androidx.compose.material3.TextButton(onClick = { onRetryRow(row.spec) }) { Text(stringResource(R.string.action_retry)) }
                        }
                        LoadState.Loading -> Unit
                    }
                }
            }
        }
    }
}

/** The profile's libraries in Arc's order, and the Watchlist. */
@Composable
internal fun LibrariesList(state: BrowseUiState, onOpen: (Library) -> Unit, onWatchlist: () -> Unit, onRetry: () -> Unit) {
    when (val f = state.frame) {
        LoadState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is LoadState.Failed -> MobileError(f.error, onRetry)
        is LoadState.Ready -> LazyColumn(Modifier.fillMaxSize()) {
            items(f.value.libraries, key = { it.id }) { lib ->
                ListItem(
                    headlineContent = { Text(lib.name) },
                    leadingContent = { Icon(libraryIcon(lib), contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = ArcColors.Background),
                    modifier = Modifier.clickable { onOpen(lib) },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.row_watchlist)) },
                    leadingContent = { Icon(ArcIcons.Bookmark, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = ArcColors.Background),
                    modifier = Modifier.clickable(onClick = onWatchlist),
                )
            }
        }
    }
}

private fun libraryIcon(lib: Library): ImageVector = when (lib.kind) {
    "movies" -> ArcIcons.Film
    "shows" -> ArcIcons.Tv
    else -> ArcIcons.Folder
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title, maxLines = 1) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(ArcIcons.ArrowLeft, contentDescription = stringResource(R.string.mobile_back)) } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = ArcColors.Background),
        windowInsets = WindowInsets(0),
    )
}

/**
 * A library's titles in a grid that fills the width with posters (wide cards for home videos),
 * loaded a page at a time as it scrolls, in the order Arc's library uses.
 */
@Composable
fun MobileLibrary(state: BrowseUiState, onBack: () -> Unit, onOpen: (Item) -> Unit, onNeed: (Int) -> Unit, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        val page = (state.library as? LoadState.Ready)?.value
        PageTopBar(page?.library?.name.orEmpty(), onBack)
        when (val l = state.library) {
            LoadState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is LoadState.Failed -> MobileError(l.error, onRetry)
            is LoadState.Ready -> LibraryGrid(l.value, onOpen, onNeed)
        }
    }
}

@Composable
private fun LibraryGrid(page: LibraryPage, onOpen: (Item) -> Unit, onNeed: (Int) -> Unit) {
    if (page.total == 0) {
        Text(stringResource(R.string.library_empty), style = MaterialTheme.typography.bodyLarge, color = ArcColors.Muted, modifier = Modifier.padding(pageMargin()))
        return
    }
    val landscape = page.library.kind == "videos"
    val margin = pageMargin()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (landscape) 200.dp else 104.dp),
        contentPadding = PaddingValues(start = margin, end = margin, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(page.total, key = { it }) { i ->
            val item = page.items[i]
            if (item == null) {
                LaunchedEffect(i) { onNeed(i) }
                Box(Modifier.fillMaxWidth().aspectRatio(if (landscape) 16f / 9f else 2f / 3f).background(ArcColors.Surface, RoundedCornerShape(8.dp)))
            } else {
                MobileCard(cardFor(item), landscape, onClick = { onOpen(item) }, width = androidx.compose.ui.unit.Dp.Unspecified, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** The profile's Watchlist, newest first, as a poster grid. */
@Composable
private fun MobileWatchlist(state: BrowseUiState, onBack: () -> Unit, onOpen: (Item) -> Unit, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PageTopBar(stringResource(R.string.row_watchlist), onBack)
        when (val w = state.watchlist) {
            LoadState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is LoadState.Failed -> MobileError(w.error, onRetry)
            is LoadState.Ready -> if (w.value.isEmpty()) {
                Text(stringResource(R.string.watchlist_empty), style = MaterialTheme.typography.bodyLarge, color = ArcColors.Muted, modifier = Modifier.padding(pageMargin()))
            } else {
                val margin = pageMargin()
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(104.dp),
                    contentPadding = PaddingValues(start = margin, end = margin, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(w.value.size, key = { w.value[it].id }) { i ->
                        val item = w.value[i]
                        MobileCard(cardFor(item), landscape = false, onClick = { onOpen(item) }, width = androidx.compose.ui.unit.Dp.Unspecified, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

/** Search as you type: Top Results, Episodes and People, as the TV's search groups them. */
@Composable
fun MobileSearch(state: SearchUiState, onType: (String) -> Unit, onCard: (MediaCard) -> Unit, onPerson: (Long) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.text,
            onValueChange = onType,
            singleLine = true,
            leadingIcon = { Icon(ArcIcons.Search, contentDescription = null) },
            trailingIcon = if (state.text.isNotEmpty()) { { IconButton(onClick = { onType("") }) { Icon(ArcIcons.Close, contentDescription = stringResource(R.string.mobile_clear_search)) } } } else null,
            placeholder = { Text(stringResource(R.string.search_placeholder)) },
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth().padding(horizontal = pageMargin(), vertical = 8.dp),
        )
        when {
            state.text.isBlank() -> Text(stringResource(R.string.search_hint), style = MaterialTheme.typography.bodyMedium, color = ArcColors.Muted, modifier = Modifier.padding(pageMargin()))
            state.error != null -> Text(stringResource(R.string.search_failed), style = MaterialTheme.typography.bodyMedium, color = ArcColors.Error, modifier = Modifier.padding(pageMargin()))
            state.sections.isEmpty() && state.searching -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.sections.isEmpty() && state.query.isNotEmpty() -> Text(stringResource(R.string.search_empty, state.query), style = MaterialTheme.typography.bodyMedium, color = ArcColors.Muted, modifier = Modifier.padding(pageMargin()))
            else -> LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                items(state.sections.filter { it.group == SearchGroup.Top || it.group == SearchGroup.Episodes || it.group == SearchGroup.People }, key = { it.group.name }) { section ->
                    when (section.group) {
                        SearchGroup.People -> PeopleRow(
                            stringResource(R.string.search_people),
                            section.people.map { Triple(it.name, it.image, null) },
                            onClick = { i -> onPerson(section.people[i].id) },
                        )
                        SearchGroup.Episodes -> MobileShelf(stringResource(R.string.search_episodes), section.cards, landscape = true, onClick = onCard)
                        else -> MobileShelf(stringResource(R.string.search_top), section.cards, landscape = false, onClick = onCard)
                    }
                }
            }
        }
    }
}

