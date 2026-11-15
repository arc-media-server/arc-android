package org.arcmediaserver.android.ui.player

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import org.arcmediaserver.android.data.orderNumbers
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import org.arcmediaserver.android.R
import org.arcmediaserver.android.data.Chapter
import org.arcmediaserver.android.data.Credit
import org.arcmediaserver.android.data.Item
import org.arcmediaserver.android.data.Marker
import org.arcmediaserver.android.data.PlaybackPlan
import org.arcmediaserver.android.data.cardFor
import org.arcmediaserver.android.playback.PlayerOsd
import org.arcmediaserver.android.playback.PlayerOsd.Tab
import org.arcmediaserver.android.playback.trickplayFrame
import org.arcmediaserver.android.ui.browse.runtimeText
import org.arcmediaserver.android.ui.components.ArcIcons
import org.arcmediaserver.android.ui.components.Artwork
import org.arcmediaserver.android.ui.components.FormatBadges
import org.arcmediaserver.android.ui.components.RoundIconButton
import org.arcmediaserver.android.ui.components.formatLabels
import org.arcmediaserver.android.ui.components.requestWhenReady
import org.arcmediaserver.android.ui.details.clock
import org.arcmediaserver.android.ui.theme.ArcColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The controls over the picture, in the Apple TV app's arrangement (arc-apple PlayerOSD):
 * - the show's name large at the bottom left, with "S2, E4 • the episode's name" above it (a
 *   film: its title alone), and three round buttons at the right of it: Playback, Audio and
 *   Subtitles, each opening its menu above it;
 * - the timeline, split at the chapters, with an upright marker, the time played under the marker
 *   and the time left at the right end. It has the focus when the controls show: Left and Right
 *   scrub (with the timeline picture above the marker), OK pauses;
 * - a tab row under it: Info, then Chapters, Cast and Episodes where the title has them. A
 *   focused tab's card takes the place of the title and timeline.
 *
 * Up from the timeline goes to the buttons, Down to the tabs, Down again into the card's tiles;
 * Up or Back from the tabs goes back to the timeline. Skip Intro / Skip Credits / Play Next show
 * at the right when the markers say so, and take the focus even while the controls are hidden.
 */
@Composable
fun PlayerControls(
    state: PlayerUiState,
    visible: Boolean,
    scrubMs: Long?,
    onPlayPause: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onBack10: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onForward10: () -> Unit,
    onScrub: (Long) -> Unit,
    onSkip: (Marker) -> Unit,
    onNext: () -> Unit,
    onMenu: (PlayerMenu) -> Unit,
    @Suppress("UNUSED_PARAMETER") onClose: () -> Unit,
    upNextShown: Boolean = false,
    /** The menu open over the controls; when it closes, its button has the focus again. */
    menu: PlayerMenu = PlayerMenu.None,
    onChapter: (Long) -> Unit = {},
    onRestart: () -> Unit = {},
    onPlayEpisode: (Item) -> Unit = {},
    /** Whether a tab's card is open (the player keeps the controls up while one is). */
    onCardOpen: (Boolean) -> Unit = {},
    /** The card open on arrival (screenshots). */
    initialTab: Tab? = null,
) {
    val plan = state.plan
    val timelineFocus = remember { FocusRequester() }
    val buttonFocus = remember { mutableMapOf<PlayerMenu, FocusRequester>() }
    fun buttonRequester(m: PlayerMenu) = buttonFocus.getOrPut(m) { FocusRequester() }
    var lastMenu by remember { mutableStateOf<PlayerMenu?>(null) }
    fun open(m: PlayerMenu) {
        lastMenu = m
        onMenu(m)
    }
    var focusedLabel by remember { mutableStateOf<String?>(null) }
    val tabs = PlayerOsd.tabs(plan?.chapters?.size ?: 0, state.cast.size, state.episodes.size)
    var tab by remember { mutableStateOf(initialTab) }
    val tabFocus = remember { mutableMapOf<Tab, FocusRequester>() }
    fun tabRequester(t: Tab) = tabFocus.getOrPut(t) { FocusRequester() }
    val cardEntry = remember { mutableMapOf<Tab, FocusRequester>() }
    fun entryRequester(t: Tab) = cardEntry.getOrPut(t) { FocusRequester() }
    var backToTimeline by remember { mutableStateOf(false) }
    fun closeCard() {
        tab = null
        backToTimeline = true
    }
    val skipFocus = remember { FocusRequester() }
    // While Up Next counts down, its card offers what Play Next would.
    val skip = plan?.let { skippable(it.markers, state.positionMs) }?.takeUnless { upNextShown && it.kind == "credits" }

    LaunchedEffect(tab) { onCardOpen(tab != null) }
    LaunchedEffect(visible) { if (!visible) tab = null }
    LaunchedEffect(backToTimeline, tab) {
        if (backToTimeline && tab == null) {
            backToTimeline = false
            timelineFocus.requestWhenReady()
        }
    }
    BackHandler(enabled = visible && tab != null && menu == PlayerMenu.None) { closeCard() }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().fillMaxHeight(if (tab != null) 0.7f else 0.5f)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.88f)))),
                )
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 48.dp, end = 48.dp, bottom = 22.dp)) {
                    if (tab == null) {
                        TitleRow(
                            state = state,
                            focusedLabel = focusedLabel,
                            button = { m, icon, label ->
                                OsdButton(
                                    icon, label, { open(m) },
                                    Modifier.focusRequester(buttonRequester(m)).focusProperties { down = timelineFocus },
                                    onLabel = { focusedLabel = it },
                                )
                            },
                        )
                        Spacer(Modifier.height(14.dp))
                        Timeline(
                            state = state,
                            scrubMs = scrubMs,
                            onScrub = onScrub,
                            onPlayPause = onPlayPause,
                            chapters = plan?.chapters.orEmpty(),
                            modifier = Modifier
                                .focusRequester(timelineFocus)
                                .focusProperties {
                                    up = buttonRequester(PlayerMenu.Subtitles)
                                    down = tabRequester(tabs.first())
                                },
                            onFocus = { focusedLabel = null },
                        )
                    }
                    Spacer(Modifier.height(if (tab == null) 6.dp else 0.dp))
                    TabRow(
                        tabs = tabs,
                        selected = tab,
                        requester = ::tabRequester,
                        entry = ::entryRequester,
                        onFocusTab = { tab = it },
                        onUp = ::closeCard,
                    )
                    val open = tab
                    if (open != null) {
                        Spacer(Modifier.height(14.dp))
                        val up = tabRequester(open)
                        when (open) {
                            Tab.Info -> InfoCard(state, plan, onRestart, entryRequester(open), up)
                            Tab.Chapters -> ChaptersCard(state, plan, onChapter, entryRequester(open), up)
                            Tab.Cast -> CastCard(state.cast, entryRequester(open), up)
                            Tab.Episodes -> EpisodesCard(state, onPlayEpisode, entryRequester(open), up)
                        }
                    }
                }
            }
        }
        // Skip Intro / Recap / Credits, or Play Next in the credits when there's a next episode.
        if (skip != null && tab == null) {
            val label = when {
                skip.kind == "intro" -> stringResource(R.string.player_skip_intro)
                skip.kind == "recap" -> stringResource(R.string.player_skip_recap)
                state.next != null -> stringResource(R.string.player_play_next)
                else -> stringResource(R.string.player_skip_credits)
            }
            Button(
                onClick = { if (skip.kind == "credits" && state.next != null) onNext() else onSkip(skip) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = if (visible) 200.dp else 48.dp).focusRequester(skipFocus),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                colors = ButtonDefaults.colors(containerColor = Color.White.copy(alpha = 0.2f), contentColor = Color.White, focusedContainerColor = Color.White, focusedContentColor = Color.Black),
            ) {
                Text(label)
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Icon(ArcIcons.SkipForward, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            }
            LaunchedEffect(skip.kind, skip.startMs) { if (!visible) skipFocus.requestWhenReady() }
        }
    }
    LaunchedEffect(visible) { if (visible && initialTab == null) timelineFocus.requestWhenReady() }
    LaunchedEffect(initialTab) { if (initialTab != null && visible) tabRequester(initialTab).requestWhenReady() }
    // A menu closed (a choice made, or Back): back to the button that opened it.
    LaunchedEffect(menu) {
        val from = lastMenu ?: return@LaunchedEffect
        if (menu != PlayerMenu.None) return@LaunchedEffect
        lastMenu = null
        if (visible) (buttonFocus[from] ?: timelineFocus).requestWhenReady()
    }
}

/** "S2, E4 • Name" for an episode, else null (a film shows its title alone). */
@Composable
internal fun episodeLine(item: Item): String? {
    if (!item.isEpisode) return null
    // Outside the show's order (Arc's 0 and 0): the name alone.
    val (s, e) = item.orderNumbers ?: return item.title
    return stringResource(R.string.player_episode_line, s, e, item.title)
}

@Composable
private fun TitleRow(state: PlayerUiState, focusedLabel: String?, button: @Composable (PlayerMenu, ImageVector, String) -> Unit) {
    Row(verticalAlignment = Alignment.Bottom) {
        val item = state.item
        Column(Modifier.weight(1f).padding(end = 24.dp)) {
            if (item != null) {
                episodeLine(item)?.let {
                    Text(it, style = MaterialTheme.typography.titleSmall, color = Color.White.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                }
                val name = if (item.isEpisode) item.seriesTitle ?: item.title else item.title
                Text(name, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Box(Modifier.height(20.dp)) {
                focusedLabel?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f), maxLines = 1) }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                button(PlayerMenu.Playback, ArcIcons.Gauge, stringResource(R.string.player_playback))
                button(PlayerMenu.Audio, ArcIcons.Volume, stringResource(R.string.player_audio))
                button(PlayerMenu.Subtitles, ArcIcons.Captions, stringResource(R.string.player_subtitles))
            }
        }
    }
}

/** A small round glass button; its name shows above the row while it has the focus. */
@Composable
private fun OsdButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, onLabel: (String?) -> Unit) =
    RoundIconButton(icon, label, onClick, modifier, size = 40.dp, containerColor = Color.White.copy(alpha = 0.16f), onLabel = onLabel)

/** The chapter at a point, by index. */
internal fun chapterAt(chapters: List<Chapter>, ms: Long): Int = chapters.indexOfLast { it.startMs <= ms }

/**
 * The timeline: segments split at the chapters, filled to the time shown, with an upright marker
 * and, under it, the play/pause sign and the time played; the time left sits at the right end.
 * Focused, Left and Right scrub in 10-second steps (the picture at that point shows above the
 * marker, with the chapter's name), and OK pauses or plays.
 */
@Composable
private fun Timeline(
    state: PlayerUiState,
    scrubMs: Long?,
    onScrub: (Long) -> Unit,
    onPlayPause: () -> Unit,
    chapters: List<Chapter>,
    modifier: Modifier = Modifier,
    onFocus: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val duration = state.durationMs
    val shown = scrubMs ?: state.positionMs
    val at = PlayerOsd.fraction(shown, duration)
    val buffered = PlayerOsd.fraction(state.bufferedMs, duration)
    val segments = remember(chapters, duration) { PlayerOsd.segments(chapters, duration) }
    Column(
        modifier
            .fillMaxWidth()
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> { onScrub(-10_000); true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { onScrub(10_000); true }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { onPlayPause(); true }
                    else -> false
                }
            }
            .focusable(),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(26.dp), contentAlignment = Alignment.CenterStart) {
            val h = if (focused) 10.dp else 7.dp
            val gap = 3.dp
            segments.forEachIndexed { i, s ->
                val left = maxWidth * s.start
                val width = (maxWidth * (s.end - s.start) - if (i < segments.lastIndex) gap else 0.dp).coerceAtLeast(1.dp)
                Box(Modifier.offset(x = left).width(width).height(h).clip(RoundedCornerShape(h / 2)).background(Color.White.copy(alpha = 0.22f))) {
                    Box(Modifier.fillMaxWidth(PlayerOsd.filled(s, buffered)).fillMaxHeight().background(Color.White.copy(alpha = 0.22f)))
                    Box(Modifier.fillMaxWidth(PlayerOsd.filled(s, at)).fillMaxHeight().background(Color.White))
                }
            }
            val x = maxWidth * at
            Box(Modifier.offset(x = x - 2.dp).width(4.dp).height(if (focused) 26.dp else 20.dp).shadow(3.dp, RoundedCornerShape(2.dp)).clip(RoundedCornerShape(2.dp)).background(Color.White))
            // While scrubbing: the picture at that point above the marker, with its chapter's name.
            val thumbs = state.trickplay
            if (scrubMs != null) {
                val frame = thumbs?.let { trickplayFrame(it.info, it.sheets, scrubMs) }
                val w = ThumbPreviewWidth
                val chapter = chapters.getOrNull(chapterAt(chapters, scrubMs))?.title?.takeIf { it.isNotBlank() }
                if (frame != null && thumbs != null) {
                    // Placed by its top edge: the thumbnail measures as nothing (it draws unbounded).
                    val th = w * (thumbs.info.height.toFloat() / thumbs.info.width)
                    val label = if (chapter != null) 24.dp else 0.dp
                    val left = (x - w / 2).coerceIn(0.dp, (maxWidth - w).coerceAtLeast(0.dp))
                    Box(Modifier.align(Alignment.TopStart).offset(x = left, y = -(th + label + 10.dp))) {
                        TrickplayThumb(frame, thumbs.info, w)
                    }
                    if (chapter != null) {
                        Text(
                            chapter, style = MaterialTheme.typography.labelMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.TopStart).offset(x = left, y = -(label + 6.dp)).width(w),
                        )
                    }
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val x = maxWidth * at
            val labelWidth = 120.dp
            val left = (x - 10.dp).coerceIn(0.dp, (maxWidth - labelWidth - 90.dp).coerceAtLeast(0.dp))
            Row(Modifier.offset(x = left).width(labelWidth), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (state.playing) ArcIcons.Play else ArcIcons.Pause, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(11.dp))
                Spacer(Modifier.width(6.dp))
                Text(clock(shown), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.9f))
            }
            Text(
                if (duration > 0) "-" + clock((duration - shown).coerceAtLeast(0)) else "–:––",
                style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

private val ThumbPreviewWidth = 240.dp

@Composable
private fun tabName(t: Tab): String = when (t) {
    Tab.Info -> stringResource(R.string.player_tab_info)
    Tab.Chapters -> stringResource(R.string.player_chapters)
    Tab.Cast -> stringResource(R.string.details_cast)
    Tab.Episodes -> stringResource(R.string.search_episodes)
}

/** The tab row: focusing a tab opens its card; Up goes back to the timeline. */
@Composable
private fun TabRow(
    tabs: List<Tab>,
    selected: Tab?,
    requester: (Tab) -> FocusRequester,
    entry: (Tab) -> FocusRequester,
    onFocusTab: (Tab) -> Unit,
    onUp: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        tabs.forEach { t ->
            Surface(
                onClick = { onFocusTab(t) },
                modifier = Modifier
                    .focusRequester(requester(t))
                    .focusProperties { if (selected == t) down = entry(t) }
                    .onFocusChanged { if (it.isFocused) onFocusTab(t) }
                    .onKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP) { onUp(); true } else false
                    },
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (selected == t) Color.White.copy(alpha = 0.18f) else Color.Transparent,
                    contentColor = if (selected == null || selected == t) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.6f),
                    focusedContainerColor = Color.White,
                    focusedContentColor = Color.Black,
                ),
            ) {
                Text(tabName(t), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp))
            }
        }
    }
}

private val Glass = Color(0xB3202024)
private val CardShape = RoundedCornerShape(16.dp)

/** A focusable glass tile in a card's row; focused, it lifts and gains a white edge. */
@Composable
private fun Tile(onClick: () -> Unit, modifier: Modifier = Modifier, width: Dp, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.width(width),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(12.dp))),
        colors = ClickableSurfaceDefaults.colors(containerColor = Glass, contentColor = Color.White, focusedContainerColor = Color(0xE6303034), focusedContentColor = Color.White),
    ) { content() }
}

/**
 * Info: the picture, the name and overview, then "S2 · E4 · 46m · 15 Nov 2002" with the quality
 * badges and how the stream reaches the TV in small text; a round Restart button at the top right.
 */
@Composable
private fun InfoCard(state: PlayerUiState, plan: PlaybackPlan?, onRestart: () -> Unit, entry: FocusRequester, up: FocusRequester) {
    val item = state.item ?: return
    Row(Modifier.fillMaxWidth().clip(CardShape).background(Glass).padding(14.dp)) {
        Artwork(cardFor(item).thumb, label = item.title, modifier = Modifier.width(250.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)))
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            item.overview?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f), maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val parts = buildList {
                    if (item.isEpisode) item.orderNumbers?.let { (s, e) -> add(stringResource(R.string.episode_code, s, e)) }
                    item.runtimeMin?.takeIf { it > 0 }?.let { add(runtimeText(it)) }
                    infoDate(item.releaseDate)?.let(::add)
                }
                if (parts.isNotEmpty()) {
                    Text(parts.joinToString(" · "), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.75f), maxLines = 1)
                    Spacer(Modifier.width(10.dp))
                }
                FormatBadges(formatLabels(item))
                Spacer(Modifier.weight(1f))
                if (plan != null) Text(methodLabel(plan).first, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f), maxLines = 1)
            }
        }
        Spacer(Modifier.width(12.dp))
        RoundIconButton(
            ArcIcons.RotateCcw, stringResource(R.string.player_restart), onRestart,
            Modifier.focusRequester(entry).focusProperties { this.up = up },
            size = 36.dp, containerColor = Color.White.copy(alpha = 0.16f),
        )
    }
}

/** "15 Nov 2002" from "2002-11-15", or null. */
internal fun infoDate(date: String?): String? = runCatching {
    LocalDate.parse(date!!.take(10)).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
}.getOrNull()

private val CardRowPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp)

/** Chapters: a tile for each, with its picture (from the timeline thumbnails), name and time. */
@Composable
private fun ChaptersCard(state: PlayerUiState, plan: PlaybackPlan?, onChapter: (Long) -> Unit, entry: FocusRequester, up: FocusRequester) {
    val chapters = plan?.chapters.orEmpty()
    val current = chapterAt(chapters, state.positionMs).coerceAtLeast(0)
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (current - 1).coerceAtLeast(0))
    LazyRow(state = list, horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = CardRowPadding) {
        itemsIndexed(chapters) { i, c ->
            val focus = Modifier.focusProperties { this.up = up }.let { if (i == current) it.focusRequester(entry) else it }
            Tile({ onChapter(c.startMs) }, focus, width = 220.dp) {
                Column {
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black.copy(alpha = 0.4f))) {
                        val thumbs = state.trickplay
                        val frame = thumbs?.let { trickplayFrame(it.info, it.sheets, c.startMs) }
                        if (frame != null && thumbs != null) TrickplayThumb(frame, thumbs.info, 220.dp)
                        if (i == current) {
                            Icon(ArcIcons.Play, contentDescription = null, tint = Color.White, modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).size(14.dp))
                        }
                    }
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Text(c.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.player_chapter_number, i + 1), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(clock(c.startMs), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }
}

/** Cast: a tile per actor with a round photo, the name and the part. */
@Composable
private fun CastCard(cast: List<Credit>, entry: FocusRequester, up: FocusRequester) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = CardRowPadding) {
        itemsIndexed(cast) { i, c ->
            val focus = Modifier.focusProperties { this.up = up }.let { if (i == 0) it.focusRequester(entry) else it }
            Tile({}, focus, width = 160.dp) {
                Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Artwork(c.image, label = initialsOf(c.name), modifier = Modifier.size(84.dp).clip(CircleShape))
                    Spacer(Modifier.height(8.dp))
                    Text(c.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    Text(c.character.orEmpty(), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

private fun initialsOf(name: String) = name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }

/** Episodes: the season's episodes as pictures with "S1 · E8" and the name; OK plays one. */
@Composable
private fun EpisodesCard(state: PlayerUiState, onPlayEpisode: (Item) -> Unit, entry: FocusRequester, up: FocusRequester) {
    val current = state.episodes.indexOfFirst { it.id == state.item?.id }.coerceAtLeast(0)
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (current - 1).coerceAtLeast(0))
    LazyRow(state = list, horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = CardRowPadding) {
        itemsIndexed(state.episodes, key = { _, e -> e.id }) { i, ep ->
            val focus = Modifier.focusProperties { this.up = up }.let { if (i == current) it.focusRequester(entry) else it }
            val playing = ep.id == state.item?.id
            Tile({ if (!playing) onPlayEpisode(ep) }, focus, width = 250.dp) {
                Column {
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                        Artwork(cardFor(ep).thumb, label = ep.title, modifier = Modifier.fillMaxSize())
                        val s = ep.seasonNumber
                        val e = ep.episodeNumber
                        // Outside the show's order an episode has no number in it.
                        val numbered = ep.orderNumbers != null && !state.episodesUnplaced
                        if (playing || numbered) Row(
                            Modifier.align(Alignment.BottomStart).padding(8.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (playing) {
                                Icon(ArcIcons.Play, contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp))
                                Spacer(Modifier.width(5.dp))
                            }
                            if (numbered) Text(stringResource(R.string.episode_code, s!!, e!!), style = MaterialTheme.typography.labelMedium, color = Color.White)
                        }
                    }
                    Text(ep.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp))
                }
            }
        }
    }
}
