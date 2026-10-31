package org.arcmediaserver.android.ui.player

import androidx.compose.foundation.layout.heightIn

import androidx.compose.animation.slideOutVertically

import androidx.compose.animation.slideInVertically

import androidx.compose.foundation.layout.PaddingValues

import androidx.compose.ui.draw.shadow

import io.github.peerless2012.ass.media.kt.withAssSupport
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import org.arcmediaserver.android.ArcApplication
import org.arcmediaserver.android.R
import org.arcmediaserver.android.data.AudioTrack
import org.arcmediaserver.android.data.Chapter
import org.arcmediaserver.android.data.Item
import org.arcmediaserver.android.data.Marker
import org.arcmediaserver.android.data.PlaybackPlan
import org.arcmediaserver.android.data.SubtitleTrack
import org.arcmediaserver.android.data.Version
import org.arcmediaserver.android.data.TrickplayInfo
import org.arcmediaserver.android.data.cardFor
import org.arcmediaserver.android.playback.TrickplayFrame
import org.arcmediaserver.android.playback.UpNext
import org.arcmediaserver.android.playback.sameLanguage
import org.arcmediaserver.android.playback.trickplayFrame
import org.arcmediaserver.android.ui.components.Artwork
import org.arcmediaserver.android.ui.components.LocalImageUrl
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.tv.material3.OutlinedButton
import coil3.compose.AsyncImage
import org.arcmediaserver.android.ui.components.ArcIcons
import org.arcmediaserver.android.ui.components.ErrorPane
import org.arcmediaserver.android.ui.components.Spinner
import org.arcmediaserver.android.ui.components.episodeCode
import org.arcmediaserver.android.ui.components.requestWhenReady
import org.arcmediaserver.android.ui.details.clock
import org.arcmediaserver.android.ui.theme.ArcColors
import java.util.Locale

/** The player's menus, as the web player's: each opens as a panel at the right. */
enum class PlayerMenu { None, Audio, Subtitles, Version, Quality, Chapters, Speed, Info }

/** Quality limits the player offers (kbit/s), as web/src/lib/playback.ts `qualities`. */
val qualities = listOf(20000, 12000, 8000, 4000, 3000, 2000, 1500, 720)
val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

/** e.g. "4 Mbps 720p" or "720 kbps 360p" (web qualityName). */
fun qualityName(kbps: Int): String {
    val height = when {
        kbps >= 8000 -> 1080
        kbps >= 2000 -> 720
        kbps >= 1500 -> 480
        else -> 360
    }
    val rate = if (kbps >= 1000) "${if (kbps % 1000 == 0) (kbps / 1000).toString() else (kbps / 1000.0).toString()} Mbps" else "$kbps kbps"
    return "$rate ${height}p"
}

/** The skip offered now: an intro or recap while it plays, or the credits (web Player.tsx). */
fun skippable(markers: List<Marker>, positionMs: Long): Marker? =
    markers.firstOrNull { positionMs >= it.startMs && positionMs < it.endMs - 1_500 }

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerRoute(itemId: String, startMs: Long, entryId: String? = null, carryOn: Boolean = false, onClose: () -> Unit, onPlayNextEntry: (Item, String?) -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as ArcApplication).container
    val vm: PlayerViewModel = viewModel(factory = viewModelFactory { initializer { PlayerViewModel(container, context.applicationContext, itemId, startMs, entryId, carryOn) } })
    val state by vm.state.collectAsStateWithLifecycle()
    // The next title goes with its queue entry, when it's the queue's.
    val onPlayNext: (Item) -> Unit = { next -> onPlayNextEntry(next, state.nextEntryId) }
    // Up Next (web Player.tsx): a countdown when the credits start and again at the end; Play Now
    // goes straight on, Cancel lets the credits play (or, at the end, closes the player, as Plex).
    var upNextIn by remember { mutableStateOf<Int?>(null) }
    var creditsOffered by remember { mutableStateOf(false) }
    val atMarker = state.plan?.let { skippable(it.markers, state.positionMs) }
    val inCredits = atMarker?.kind == "credits"
    // Skip Intros Automatically (Settings): each intro and recap is skipped once, as the web player.
    val autoSkipped = remember { mutableSetOf<Long>() }
    LaunchedEffect(atMarker?.startMs, state.autoSkip) {
        val m = atMarker ?: return@LaunchedEffect
        if (state.autoSkip && (m.kind == "intro" || m.kind == "recap") && autoSkipped.add(m.startMs)) {
            org.arcmediaserver.android.data.ArcLog.Android.info("player: skipped the ${m.kind} automatically")
            vm.seekTo(m.endMs)
        }
    }
    LaunchedEffect(inCredits, state.next) {
        if (inCredits && UpNext.atCredits(state.next, state.autoPlay, creditsOffered)) {
            creditsOffered = true
            upNextIn = UpNext.COUNTDOWN_SECONDS
        }
    }
    LaunchedEffect(state.ended) {
        if (!state.ended) return@LaunchedEffect
        when (UpNext.atEnd(state.next, state.autoPlay)) {
            UpNext.AtEnd.Countdown -> if (upNextIn == null) upNextIn = UpNext.COUNTDOWN_SECONDS
            UpNext.AtEnd.Close -> onClose()
        }
    }
    LaunchedEffect(upNextIn) {
        val seconds = upNextIn ?: return@LaunchedEffect
        if (seconds <= 0) {
            state.next?.let(onPlayNext)
        } else {
            delay(1_000)
            upNextIn = seconds - 1
        }
    }
    val cancelUpNext = {
        upNextIn = null
        if (state.ended) onClose()
    }

    var controls by remember { mutableStateOf(true) }
    var menu by remember { mutableStateOf(PlayerMenu.None) }
    var lastInput by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var scrub by remember { mutableStateOf<Long?>(null) }
    val rootFocus = remember { FocusRequester() }

    LaunchedEffect(lastInput, state.playing, menu) {
        if (state.playing && menu == PlayerMenu.None) {
            delay(5_000)
            controls = false
        }
    }
    LaunchedEffect(scrub) {
        val target = scrub ?: return@LaunchedEffect
        delay(900)
        vm.seekTo(target)
        scrub = null
    }
    LaunchedEffect(controls) { if (!controls) rootFocus.requestWhenReady() }

    // Back cancels Up Next, closes a menu, then hides the controls, then leaves the player.
    BackHandler(enabled = upNextIn != null || menu != PlayerMenu.None || controls) {
        when {
            upNextIn != null -> cancelUpNext()
            menu != PlayerMenu.None -> menu = PlayerMenu.None
            else -> controls = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                lastInput = System.currentTimeMillis()
                when (e.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { vm.togglePlay(); controls = true; true }
                    KeyEvent.KEYCODE_MEDIA_PLAY -> { vm.player.play(); true }
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> { vm.player.pause(); controls = true; true }
                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { vm.seekBy(10_000); controls = true; true }
                    KeyEvent.KEYCODE_MEDIA_REWIND -> { vm.seekBy(-10_000); controls = true; true }
                    KeyEvent.KEYCODE_MEDIA_NEXT -> { state.next?.let(onPlayNext); state.next != null }
                    else -> false
                }
            }
            .focusRequester(rootFocus)
            .onKeyEvent { e ->
                // Controls hidden: OK pauses, left and right go back and forward 10 seconds (the web
                // player's J and L), up and down show the controls.
                if (controls || e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { vm.togglePlay(); controls = true; true }
                    KeyEvent.KEYCODE_DPAD_LEFT -> { vm.seekBy(-10_000); true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { vm.seekBy(10_000); true }
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> { controls = true; true }
                    else -> false
                }
            }
            .focusable(),
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    isFocusable = false
                    keepScreenOn = true
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    player = vm.player
                    // libass draws ASS/SSA subtitles over the player's own subtitle view.
                    subtitleView?.withAssSupport(vm.ass)
                }
            },
            // Text subtitles in the profile's style, not Android's caption settings (a black box).
            update = { view ->
                view.subtitleView?.apply {
                    setStyle(org.arcmediaserver.android.playback.captionStyle(state.subtitleStyle))
                    setFractionalTextSize(org.arcmediaserver.android.playback.subtitleTextFraction(state.subtitleStyle))
                }
            },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize(),
        )
        // Text subtitles the app loads itself, on their own view so the player never clears them.
        val cues by vm.cues.collectAsStateWithLifecycle()
        AndroidView(
            factory = { ctx -> androidx.media3.ui.SubtitleView(ctx) },
            update = { view ->
                view.setStyle(org.arcmediaserver.android.playback.captionStyle(state.subtitleStyle))
                view.setFractionalTextSize(org.arcmediaserver.android.playback.subtitleTextFraction(state.subtitleStyle))
                view.setCues(cues)
            },
            modifier = Modifier.fillMaxSize(),
        )
        when {
            state.stopped != null -> StoppedPane(state.stopped!!, onClose)
            state.error != null -> Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f))) {
                ErrorPane(state.error!!, onRetry = vm::retry, title = stringResource(R.string.player_failed))
            }
            else -> {
                if (state.loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Spinner(size = 56.dp) }
                PlayerControls(
                    state = state,
                    visible = controls,
                    scrubMs = scrub,
                    onPlayPause = vm::togglePlay,
                    onBack10 = { vm.seekBy(-10_000) },
                    onForward10 = { vm.seekBy(10_000) },
                    onScrub = { delta -> scrub = ((scrub ?: state.positionMs) + delta).coerceIn(0, state.durationMs) },
                    onSkip = { vm.seekTo(it.endMs) },
                    onNext = { state.next?.let(onPlayNext) },
                    onMenu = { menu = it },
                    onClose = onClose,
                    upNextShown = upNextIn != null,
                    menu = menu,
                )
                val plan = state.plan
                if (plan != null) {
                    val stats by vm.stats.collectAsStateWithLifecycle()
                    PlayerMenuPanel(
                        menu = menu,
                        state = state,
                        plan = plan,
                        stats = stats,
                        caps = vm.caps,
                        onAudio = { vm.chooseAudio(it); menu = PlayerMenu.None },
                        onSubtitle = { vm.chooseSubtitle(it); menu = PlayerMenu.None },
                        onVersion = { vm.chooseVersion(it); menu = PlayerMenu.None },
                        onQuality = { vm.chooseQuality(it); menu = PlayerMenu.None },
                        onChapter = { vm.seekTo(it); menu = PlayerMenu.None },
                        onSpeed = { vm.setSpeed(it); menu = PlayerMenu.None },
                    )
                }
                val next = state.next
                val seconds = upNextIn
                if (next != null && seconds != null) {
                    UpNextCard(next, seconds, onPlayNow = { onPlayNext(next) }, onCancel = cancelUpNext)
                }
            }
        }
    }
    // The player itself takes the keys only while the controls are hidden: on arrival the
    // controls show and Play has the focus (taking it here too left them unusable).
    LaunchedEffect(Unit) { if (!controls) rootFocus.requestWhenReady() }
}

@Composable
private fun StoppedPane(message: String, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    Column(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(stringResource(R.string.player_stopped_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge, color = ArcColors.Muted)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onClose, modifier = Modifier.focusRequester(focus)) { Text(stringResource(R.string.player_close)) }
    }
    LaunchedEffect(Unit) { focus.requestWhenReady() }
}

/**
 * The controls over the picture, as the web player's (web/src/components/Player.tsx): the title
 * and Close at the top; at the bottom the timeline, then play/pause, next, back and forward
 * 10 seconds and the time on the left, and audio, subtitles, version, quality, chapters, speed and
 * playback info on the right. Skip Intro / Skip Credits / Play Next appear at the right when the
 * markers say so, and take the focus even while the controls are hidden.
 */
@Composable
fun PlayerControls(
    state: PlayerUiState,
    visible: Boolean,
    scrubMs: Long?,
    onPlayPause: () -> Unit,
    onBack10: () -> Unit,
    onForward10: () -> Unit,
    onScrub: (Long) -> Unit,
    onSkip: (Marker) -> Unit,
    onNext: () -> Unit,
    onMenu: (PlayerMenu) -> Unit,
    onClose: () -> Unit,
    upNextShown: Boolean = false,
    /** The menu open over the controls; when it closes, its button has the focus again. */
    menu: PlayerMenu = PlayerMenu.None,
) {
    val plan = state.plan
    val playFocus = remember { FocusRequester() }
    val menuFocus = remember { mutableMapOf<PlayerMenu, FocusRequester>() }
    var lastMenu by remember { mutableStateOf<PlayerMenu?>(null) }
    fun open(m: PlayerMenu) {
        lastMenu = m
        onMenu(m)
    }
    fun menuButton(m: PlayerMenu) = Modifier.focusRequester(menuFocus.getOrPut(m) { FocusRequester() })
    val skipFocus = remember { FocusRequester() }
    var focusedLabel by remember { mutableStateOf<String?>(null) }
    // While Up Next counts down, its card offers what Play Next would.
    val skip = plan?.let { skippable(it.markers, state.positionMs) }?.takeUnless { upNextShown && it.kind == "credits" }
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize()) {
                // As the Apple TV app's player (owner, 31 October): what's playing at the bottom
                // left, the buttons in a row of round glass buttons at the bottom right with the
                // focused one named above them, then a thin white bar with the time played and left.
                // Back closes the player.
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().fillMaxHeight(0.5f).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.85f)))))
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 48.dp, end = 48.dp, bottom = 24.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        val item = state.item
                        Column(Modifier.weight(1f).padding(end = 20.dp)) {
                            if (item != null) {
                                val sub = if (item.isEpisode) listOfNotNull(item.seriesTitle, episodeCode(item)).joinToString(" · ") else item.year?.toString()
                                if (!sub.isNullOrEmpty()) Text(sub, style = MaterialTheme.typography.titleSmall, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(item.title, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Box(Modifier.height(20.dp)) {
                                focusedLabel?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f), maxLines = 1) }
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val playLabel = stringResource(if (state.playing) R.string.player_pause else R.string.player_play)
                                ControlButton(if (state.playing) ArcIcons.Pause else ArcIcons.Play, playLabel, onPlayPause, Modifier.focusRequester(playFocus), onLabel = { focusedLabel = it })
                                state.next?.let { next ->
                                    ControlButton(ArcIcons.SkipForward, stringResource(R.string.player_next_title, next.title), onNext, onLabel = { focusedLabel = it })
                                }
                                ControlButton(ArcIcons.RotateCcw, stringResource(R.string.player_back_10), onBack10, onLabel = { focusedLabel = it })
                                ControlButton(ArcIcons.RotateCw, stringResource(R.string.player_forward_10), onForward10, onLabel = { focusedLabel = it })
                                Spacer(Modifier.width(8.dp))
                                if (plan != null) {
                                    if (plan.audioTracks.size > 1) ControlButton(ArcIcons.Languages, stringResource(R.string.player_audio), { open(PlayerMenu.Audio) }, menuButton(PlayerMenu.Audio), onLabel = { focusedLabel = it })
                                    if (plan.subtitleTracks.any { !it.image }) ControlButton(ArcIcons.Captions, stringResource(R.string.player_subtitles), { open(PlayerMenu.Subtitles) }, menuButton(PlayerMenu.Subtitles), onLabel = { focusedLabel = it })
                                    if (plan.versions.size > 1) ControlButton(ArcIcons.Film, stringResource(R.string.player_version), { open(PlayerMenu.Version) }, menuButton(PlayerMenu.Version), onLabel = { focusedLabel = it })
                                    ControlButton(ArcIcons.Sliders, stringResource(R.string.player_quality), { open(PlayerMenu.Quality) }, menuButton(PlayerMenu.Quality), onLabel = { focusedLabel = it })
                                    if (plan.chapters.size > 1) ControlButton(ArcIcons.ListOrdered, stringResource(R.string.player_chapters), { open(PlayerMenu.Chapters) }, menuButton(PlayerMenu.Chapters), onLabel = { focusedLabel = it })
                                }
                                ControlButton(ArcIcons.Gauge, stringResource(R.string.player_speed), { open(PlayerMenu.Speed) }, menuButton(PlayerMenu.Speed), onLabel = { focusedLabel = it })
                                if (plan != null) ControlButton(ArcIcons.Info, stringResource(R.string.player_info), { open(PlayerMenu.Info) }, menuButton(PlayerMenu.Info), onLabel = { focusedLabel = it })
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Timeline(state, scrubMs, onScrub, plan?.chapters.orEmpty())
                    Row(Modifier.fillMaxWidth()) {
                        val at = scrubMs ?: state.positionMs
                        Text(clock(at), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.8f))
                        Spacer(Modifier.weight(1f))
                        Text(if (state.durationMs > 0) "-" + clock((state.durationMs - at).coerceAtLeast(0)) else "–:––", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.8f))
                    }
                }
            }
        }
        // Skip Intro / Recap / Credits, or Play Next in the credits when there's a next episode.
        if (skip != null) {
            val label = when {
                skip.kind == "intro" -> stringResource(R.string.player_skip_intro)
                skip.kind == "recap" -> stringResource(R.string.player_skip_recap)
                state.next != null -> stringResource(R.string.player_play_next)
                else -> stringResource(R.string.player_skip_credits)
            }
            Button(
                onClick = { if (skip.kind == "credits" && state.next != null) onNext() else onSkip(skip) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = if (visible) 150.dp else 48.dp).focusRequester(skipFocus),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                colors = ButtonDefaults.colors(containerColor = ArcColors.AccentStrong, contentColor = Color.White, focusedContainerColor = Color.White, focusedContentColor = Color.Black),
            ) {
                Text(label)
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Icon(ArcIcons.SkipForward, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            }
            LaunchedEffect(skip.kind, skip.startMs) { if (!visible) skipFocus.requestWhenReady() }
        }
    }
    LaunchedEffect(visible) { if (visible) playFocus.requestWhenReady() }
    // A menu closed (a choice made, or Back): back to the button that opened it.
    LaunchedEffect(menu) {
        val from = lastMenu ?: return@LaunchedEffect
        if (menu != PlayerMenu.None) return@LaunchedEffect
        lastMenu = null
        if (visible) (menuFocus[from] ?: playFocus).requestWhenReady()
    }
}

/** A round glass button; its label shows above the row while it has the focus. */
@Composable
private fun ControlButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, onLabel: (String?) -> Unit) =
    org.arcmediaserver.android.ui.components.RoundIconButton(icon, label, onClick, modifier, size = 34.dp, containerColor = Color.White.copy(alpha = 0.14f), onLabel = onLabel)

/** The chapter at a point, by index. */
private fun chapterAt(chapters: List<Chapter>, ms: Long): Int = chapters.indexOfLast { it.startMs <= ms }

/** The timeline: focused, left and right move along it in 10-second steps; chapters are marked. */
@Composable
private fun Timeline(state: PlayerUiState, scrubMs: Long?, onScrub: (Long) -> Unit, chapters: List<Chapter>) {
    var focused by remember { mutableStateOf(false) }
    val duration = state.durationMs.coerceAtLeast(1)
    val shown = scrubMs ?: state.positionMs
    Box(
        Modifier
            .fillMaxWidth()
            .height(24.dp)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> { onScrub(-10_000); true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { onScrub(10_000); true }
                    else -> false
                }
            }
            .focusable(),
        contentAlignment = Alignment.CenterStart,
    ) {
        val h = if (focused) 8.dp else 4.dp
        Box(Modifier.fillMaxWidth().height(h).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.25f)))
        Box(Modifier.fillMaxWidth((state.bufferedMs.toFloat() / duration).coerceIn(0f, 1f)).height(h).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.4f)))
        Box(Modifier.fillMaxWidth((shown.toFloat() / duration).coerceIn(0f, 1f)).height(h).clip(RoundedCornerShape(4.dp)).background(Color.White))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            chapters.drop(1).forEach { c ->
                Box(Modifier.offset(x = maxWidth * (c.startMs.toFloat() / duration).coerceIn(0f, 1f)).width(2.dp).height(h).background(Color.Black.copy(alpha = 0.6f)))
            }
            // Scrubbing happens on the focused timeline, so a scrub point also shows its knob.
            if (focused || scrubMs != null) {
                val x = maxWidth * (shown.toFloat() / duration).coerceIn(0f, 1f)
                Box(Modifier.offset(x = x - 7.dp).size(14.dp).shadow(4.dp, CircleShape).clip(CircleShape).background(Color.White))
                // While scrubbing: the picture at that point, above the timeline (web Player.tsx Thumb).
                val thumbs = state.trickplay
                val frame = if (scrubMs != null && thumbs != null) trickplayFrame(thumbs.info, thumbs.sheets, scrubMs) else null
                if (frame != null && thumbs != null) {
                    val w = ThumbPreviewWidth
                    val h = w * (thumbs.info.height.toFloat() / thumbs.info.width)
                    val left = (x - w / 2).coerceIn(0.dp, (maxWidth - w).coerceAtLeast(0.dp))
                    TrickplayThumb(frame, thumbs.info, w, Modifier.offset(x = left, y = -(h + 18.dp)))
                }
            }
        }
    }
}

private val ThumbPreviewWidth = 240.dp

/** One timeline thumbnail: the sheet drawn at its scale, moved so only this tile shows. */
@Composable
fun TrickplayThumb(frame: TrickplayFrame, info: TrickplayInfo, width: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val scale = width.value / info.width
    Box(
        modifier
            .wrapContentSize(Alignment.TopStart, unbounded = true)
            .requiredSize(width, (info.height * scale).dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black)
            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(8.dp)),
    ) {
        AsyncImage(
            model = LocalImageUrl.current(frame.sheet),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .offset(x = (-frame.x * scale).dp, y = (-frame.y * scale).dp)
                .requiredSize((info.columns * info.width * scale).dp, (info.rows * info.height * scale).dp),
        )
    }
}

/**
 * The web's Up Next card (Player.tsx UpNext), at the bottom right: the countdown, the next
 * episode's picture, title and number, and Play Now (focused) and Cancel.
 */
@Composable
fun UpNextCard(next: Item, seconds: Int, onPlayNow: () -> Unit, onCancel: () -> Unit) {
    val play = remember { FocusRequester() }
    // A compact card (owner, 31 October: the first was too big), as the TV web app's.
    Box(Modifier.fillMaxSize().padding(end = 48.dp, bottom = 150.dp), contentAlignment = Alignment.BottomEnd) {
        Row(
            Modifier.width(360.dp).shadow(24.dp, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp)).background(Color(0xE6141416)).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val card = cardFor(next)
            Artwork(card.thumb, label = next.title, modifier = Modifier.width(120.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(6.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(pluralStringResource(R.plurals.player_up_next_in, seconds, seconds), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.55f))
                Text(next.title, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = listOfNotNull(next.seriesTitle, episodeCode(next)).joinToString(" · ")
                if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f), maxLines = 1)
                Spacer(Modifier.height(7.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    val small = PaddingValues(horizontal = 13.dp, vertical = 0.dp)
                    Button(
                        onClick = onPlayNow, modifier = Modifier.height(28.dp).focusRequester(play), contentPadding = small,
                        colors = ButtonDefaults.colors(containerColor = Color.White.copy(alpha = 0.9f), contentColor = Color.Black, focusedContainerColor = Color.White, focusedContentColor = Color.Black),
                    ) {
                        Icon(ArcIcons.Play, contentDescription = null, modifier = Modifier.size(11.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(stringResource(R.string.player_play_now), style = MaterialTheme.typography.labelMedium)
                    }
                    Button(
                        onClick = onCancel, modifier = Modifier.height(28.dp), contentPadding = small,
                        colors = ButtonDefaults.colors(containerColor = Color.White.copy(alpha = 0.14f), contentColor = Color.White, focusedContainerColor = Color.White, focusedContentColor = Color.Black),
                    ) { Text(stringResource(R.string.action_cancel), style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
    }
    LaunchedEffect(Unit) { play.requestWhenReady() }
}

private fun languageName(code: String?): String? = org.arcmediaserver.android.playback.languageName(code)

@Composable
private fun audioName(t: AudioTrack, i: Int): String = languageName(t.language) ?: stringResource(R.string.player_track, i + 1)

@Composable
private fun audioDetail(t: AudioTrack): String = listOfNotNull(
    org.arcmediaserver.android.ui.components.codecName(t.codec),
    when (t.channels) {
        0 -> null
        1 -> stringResource(R.string.player_mono)
        2 -> stringResource(R.string.player_stereo)
        6 -> "5.1"
        8 -> "7.1"
        else -> stringResource(R.string.player_channels, t.channels)
    },
    t.title,
).joinToString(" · ")

@Composable
private fun subtitleName(t: SubtitleTrack, i: Int): String = languageName(t.language) ?: t.title ?: stringResource(R.string.player_track, i + 1)

@Composable
private fun subtitleDetail(t: SubtitleTrack): String? = listOfNotNull(
    if (t.forced) stringResource(R.string.player_forced) else null,
    if (t.hearingImpaired) "SDH" else null,
    t.title?.takeIf { title -> !sameLanguage(title, t.language) && !title.equals("forced", true) && !title.equals("sdh", true) },
).joinToString(" · ").ifBlank { null }

@Composable
private fun versionName(v: Version): Pair<String, String> {
    val res = when {
        v.height >= 2000 -> "4K"
        v.height > 0 -> "${v.height}p"
        else -> ""
    }
    val rest = listOfNotNull(v.hdr, org.arcmediaserver.android.ui.components.codecName(v.videoCodec), if (v.size > 0) "%.1f GB".format(v.size / 1e9) else null)
    return if (!v.edition.isNullOrBlank()) v.edition to (listOf(res) + rest).filter { it.isNotBlank() }.joinToString(" · ")
    else res.ifBlank { stringResource(R.string.player_version) } to rest.joinToString(" · ")
}

private data class Option(val name: String, val detail: String?, val selected: Boolean, val onClick: () -> Unit)

/** Each menu's choices, from the same rules the web player's menus follow. */
@Composable
internal fun PlayerMenuPanel(
    menu: PlayerMenu,
    state: PlayerUiState,
    plan: PlaybackPlan,
    stats: org.arcmediaserver.android.playback.PlaybackStats = org.arcmediaserver.android.playback.PlaybackStats(),
    caps: org.arcmediaserver.android.data.PlayerCaps? = null,
    onAudio: (Int) -> Unit,
    onSubtitle: (SubtitleTrack?) -> Unit,
    onVersion: (Long) -> Unit,
    onQuality: (Int) -> Unit,
    onChapter: (Long) -> Unit,
    onSpeed: (Float) -> Unit,
) {
    // While it slides away the panel keeps showing the menu that was open: switching to another
    // (the Info layout) made it take the focus back from the controls, leaving nothing focused.
    var shown by remember { mutableStateOf(menu) }
    if (menu != PlayerMenu.None) shown = menu
    AnimatedVisibility(menu != PlayerMenu.None, enter = slideInVertically { it / 8 } + fadeIn(), exit = slideOutVertically { it / 8 } + fadeOut()) {
        val title: String
        var body: String? = null
        val options: List<Option>
        when (shown) {
            PlayerMenu.Audio -> {
                title = stringResource(R.string.player_audio)
                options = plan.audioTracks.mapIndexed { i, t -> Option(audioName(t, i), audioDetail(t), t.index == plan.audioStream) { onAudio(t.index) } }
            }
            PlayerMenu.Subtitles -> {
                title = stringResource(R.string.player_subtitles)
                options = listOf(Option(stringResource(R.string.player_subtitles_off), null, state.subtitle == null) { onSubtitle(null) }) +
                    plan.subtitleTracks.filter { !it.image }.mapIndexed { i, t -> Option(subtitleName(t, i), subtitleDetail(t), state.subtitle?.id == t.id) { onSubtitle(t) } }
            }
            PlayerMenu.Version -> {
                title = stringResource(R.string.player_version)
                options = plan.versions.map { v -> versionName(v).let { (n, d) -> Option(n, d.ifBlank { null }, v.fileId == plan.fileId) { onVersion(v.fileId) } } }
            }
            PlayerMenu.Quality -> {
                title = stringResource(R.string.player_quality)
                val cap = plan.serverLimitKbps
                val choices = listOf(-1, 0) + qualities.filter { (plan.sourceKbps == 0 || it < plan.sourceKbps) && (cap == 0 || it < cap) }
                options = choices.map { q ->
                    val name = when {
                        q == -1 -> stringResource(R.string.player_automatic)
                        q > 0 -> qualityName(q)
                        cap > 0 -> stringResource(R.string.player_maximum_capped, qualityName(cap))
                        else -> stringResource(R.string.player_original)
                    }
                    Option(name, null, q == state.quality) { onQuality(q) }
                }
                if (cap > 0) body = stringResource(R.string.player_away_limit, qualityName(cap))
            }
            PlayerMenu.Chapters -> {
                title = stringResource(R.string.player_chapters)
                val at = chapterAt(plan.chapters, state.positionMs)
                options = plan.chapters.mapIndexed { i, c ->
                    Option(c.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.player_chapter_number, i + 1), clock(c.startMs), i == at) { onChapter(c.startMs) }
                }
            }
            PlayerMenu.Speed -> {
                title = stringResource(R.string.player_speed)
                options = speeds.map { s -> Option(if (s == 1f) stringResource(R.string.player_normal) else "${s.toString().removeSuffix(".0")}×", null, s == state.speed) { onSpeed(s) } }
            }
            PlayerMenu.Info, PlayerMenu.None -> {
                val (t, d) = methodLabel(plan)
                title = t
                body = d
                options = emptyList()
            }
        }
        if (shown == PlayerMenu.Info) StatsPanel(title, body, statsRows(state, plan, stats, caps)) else OptionPanel(title, options, body)
    }
}

/** How the file is being sent (web/src/lib/playback.ts methodLabel), for Playback info. */
@Composable
fun methodLabel(p: PlaybackPlan): Pair<String, String> {
    val reasons = p.reasons.joinToString(", ")
    return when (p.method) {
        "direct" -> stringResource(R.string.method_direct_title) to stringResource(R.string.method_direct)
        "remux" -> stringResource(R.string.method_remux_title) to stringResource(R.string.method_remux, reasons)
        else -> stringResource(R.string.method_converting_title) to when {
            !p.encoder.isNullOrBlank() && p.toneMapped -> stringResource(R.string.method_hardware_hdr, reasons, p.encoder)
            !p.encoder.isNullOrBlank() -> stringResource(R.string.method_hardware, reasons, p.encoder)
            p.toneMapped -> stringResource(R.string.method_software_hdr, reasons)
            else -> stringResource(R.string.method_software, reasons)
        }
    }
}

@Composable
private fun OptionPanel(title: String, options: List<Option>, body: String?) {
    val focus = remember { FocusRequester() }
    // The Apple TV app's floating list, over the controls' buttons (owner, 31 October).
    MenuFloat(380.dp) {
        PanelHeading(title, body)
        if (options.isEmpty()) {
            // Playback info has nothing to choose: a focusable close target keeps D-pad focus here.
            Box(Modifier.focusRequester(focus).focusable())
        } else {
            LazyColumn(Modifier.heightIn(max = 300.dp)) {
                items(options) { o ->
                    val first = o.selected || o === options.first() && options.none { it.selected }
                    org.arcmediaserver.android.ui.components.PopoverItem(
                        o.name, if (o.selected) ArcIcons.Check else null, o.detail, focus = if (first) focus else null, onClick = o.onClick,
                    )
                }
            }
        }
    }
    LaunchedEffect(title) { focus.requestWhenReady() }
}

/** A player menu's floating panel, at the bottom right above the controls' buttons. */
@Composable
private fun MenuFloat(width: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().padding(end = 48.dp, bottom = 120.dp), contentAlignment = Alignment.BottomEnd) {
        Column(
            modifier.width(width)
                .shadow(30.dp, RoundedCornerShape(18.dp))
                .background(org.arcmediaserver.android.ui.components.PopoverColor, RoundedCornerShape(18.dp))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                .padding(7.dp),
            content = content,
        )
    }
}

@Composable
private fun PanelHeading(title: String, body: String?) {
    Text(title, style = MaterialTheme.typography.labelLarge, color = ArcColors.Muted, modifier = Modifier.padding(start = 11.dp, end = 11.dp, top = 5.dp, bottom = 6.dp))
    body?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = ArcColors.Muted, modifier = Modifier.padding(start = 11.dp, end = 11.dp, bottom = 6.dp)) }
}

@Composable
internal fun statsRows(
    state: PlayerUiState,
    plan: PlaybackPlan,
    stats: org.arcmediaserver.android.playback.PlaybackStats,
    caps: org.arcmediaserver.android.data.PlayerCaps?,
): List<Pair<String, String>> = buildList {
    val videoFormat = when (stats.videoMime) {
        null -> null
        androidx.media3.common.MimeTypes.VIDEO_DOLBY_VISION -> "Dolby Vision"
        androidx.media3.common.MimeTypes.VIDEO_H265 -> "HEVC"
        androidx.media3.common.MimeTypes.VIDEO_H264 -> "H.264"
        androidx.media3.common.MimeTypes.VIDEO_AV1 -> "AV1"
        androidx.media3.common.MimeTypes.VIDEO_VP9 -> "VP9"
        else -> stats.videoMime.substringAfter('/')
    }
    val video = listOfNotNull(
        listOfNotNull(videoFormat, stats.videoCodecs?.let { "($it)" }).joinToString(" ").ifBlank { null },
        if (stats.width > 0) "${stats.width}×${stats.height}" else null,
        if (stats.frameRate > 0) String.format(java.util.Locale.ROOT, "%.3f fps", stats.frameRate).replace(".000", "") else null,
    ).joinToString(" · ")
    if (video.isNotBlank()) add(stringResource(R.string.stats_video) to video)
    add(stringResource(R.string.stats_dynamic_range) to stats.dynamicRange)
    stats.videoDecoder?.let { add(stringResource(R.string.stats_video_decoder) to it) }
    val source = plan.audioTracks.firstOrNull { it.index == plan.audioStream }
    val sourceAudio = source?.let { listOfNotNull(org.arcmediaserver.android.ui.components.codecName(it.codec), org.arcmediaserver.android.ui.components.channelsName(it.channels)).joinToString(" ") }
    val sent = plan.audioOutput?.takeIf { it.converted }?.let {
        listOfNotNull(org.arcmediaserver.android.ui.components.codecName(it.codec), org.arcmediaserver.android.ui.components.channelsName(it.channels)).joinToString(" ")
    }
    if (sourceAudio != null) add(stringResource(R.string.stats_audio) to (if (sent != null) "$sourceAudio → $sent" else sourceAudio))
    val output = stats.audioDecoder ?: if (stats.audioMime != null) stringResource(R.string.stats_passthrough) else null
    output?.let { add(stringResource(R.string.stats_audio_output) to it) }
    if (plan.sourceKbps > 0) add(stringResource(R.string.stats_bitrate) to String.format(java.util.Locale.ROOT, "%.1f Mbps", plan.sourceKbps / 1000.0))
    // How fast it arrives, to set against the bitrate: slower than the file means waiting.
    if (stats.downloadKbps > 0) add(stringResource(R.string.stats_download) to String.format(java.util.Locale.ROOT, "%.1f Mbps", stats.downloadKbps / 1000.0))
    if (stats.waits > 0) add(stringResource(R.string.stats_waits) to pluralStringResource(R.plurals.stats_waits_value, stats.waits, stats.waits, stats.longestWaitMs / 1000.0))
    add(stringResource(R.string.stats_buffer) to String.format(java.util.Locale.ROOT, "%.1f s", (state.bufferedMs - state.positionMs).coerceAtLeast(0) / 1000.0))
    add(stringResource(R.string.stats_dropped) to stats.droppedFrames.toString())
    if (caps != null) {
        val tv = listOfNotNull(
            caps.hdrFormats?.joinToString(", ") ?: stringResource(R.string.stats_hdr_unknown),
            caps.dolbyVisionProfiles?.let { p -> "DV " + p.joinToString("/") { "P$it" } },
            caps.audioPassthrough.takeIf { it.isNotEmpty() }?.let { a -> stringResource(R.string.stats_passthrough_list, a.joinToString(", ")) },
        ).joinToString(" · ")
        add(stringResource(R.string.stats_this_tv) to tv)
    }
}

@Composable
internal fun StatsPanel(title: String, body: String?, rows: List<Pair<String, String>>) {
    val focus = remember { FocusRequester() }
    MenuFloat(440.dp, Modifier.focusRequester(focus).focusable()) {
        PanelHeading(title, body)
        Spacer(Modifier.height(4.dp))
        rows.forEach { (label, value) ->
            Row(Modifier.padding(horizontal = 11.dp, vertical = 3.dp)) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = ArcColors.Muted, modifier = Modifier.width(120.dp))
                Text(value, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(6.dp))
    }
    LaunchedEffect(Unit) { focus.requestWhenReady() }
}
