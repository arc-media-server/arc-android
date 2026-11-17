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
enum class PlayerMenu { None, Playback, Audio, Subtitles, SubtitleTiming, Version, Quality, Chapters, Speed, Info, Together }

/** "+0.5", "-1.5" or "0.0": a subtitle offset in seconds, signed, one decimal (the web's). */
fun timingSeconds(offsetMs: Long): String =
    (if (offsetMs > 0) "+" else "") + String.format(java.util.Locale.ROOT, "%.1f", offsetMs / 1000.0)

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
fun PlayerRoute(itemId: String, startMs: Long, entryId: String? = null, carryOn: Boolean = false, fileId: Long? = null, together: Boolean = false, onClose: () -> Unit, onPlayNextEntry: (Item, String?) -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as ArcApplication).container
    val vm: PlayerViewModel = viewModel(factory = viewModelFactory { initializer { PlayerViewModel(container, context.applicationContext, itemId, startMs, entryId, carryOn, fileId, together) } })
    val state by vm.state.collectAsStateWithLifecycle()
    // The next title goes with its queue entry, when it's the queue's.
    // In a Watch Together room, the host's next title moves the whole room on (ArcNavHost follows).
    val onPlayNext: (Item) -> Unit = { next -> if (state.room != null) vm.playNextInRoom(next) else onPlayNextEntry(next, state.nextEntryId) }
    val roomCode by container.together.code.collectAsStateWithLifecycle()
    // Invite People… from the lobby or the Watch Together menu (host).
    var invitingMore by remember { mutableStateOf(false) }
    // Watch Together's lobby shows until the room first plays (or it was under way on joining).
    var lobbyDone by remember(state.room?.id) { mutableStateOf(false) }
    state.room?.let { if (org.arcmediaserver.android.data.RoomSync.startedOnce(it)) lobbyDone = true }
    val inLobby = state.room?.available == true && !lobbyDone
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
    // A tab's card (Info, Chapters, Cast, Episodes) keeps the controls up while it's open.
    var cardOpen by remember { mutableStateOf(false) }

    LaunchedEffect(lastInput, state.playing, menu, cardOpen) {
        if (state.playing && menu == PlayerMenu.None && !cardOpen) {
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
                    KeyEvent.KEYCODE_MEDIA_PLAY -> { vm.play(); true }
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> { vm.pause(); controls = true; true }
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
                // Zoom Mode (Playback menu): fill the screen, cropping the edges, or fit the picture.
                view.resizeMode = if (state.zoom) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
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
                    visible = controls && !inLobby,
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
                    onChapter = { vm.seekTo(it) },
                    onRestart = { vm.seekTo(0) },
                    onPlayEpisode = { onPlayNextEntry(it, null) },
                    onCardOpen = { cardOpen = it },
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
                        // From Playback, Speed steps on in place; the menu stays open.
                        onSpeed = { vm.setSpeed(it); if (menu != PlayerMenu.Playback) menu = PlayerMenu.None },
                        onZoom = vm::toggleZoom,
                        onOpen = { menu = it },
                        onShiftSubtitles = vm::shiftSubtitles,
                        onLeaveRoom = { menu = PlayerMenu.None; onClose() },
                        onEndRoom = { menu = PlayerMenu.None; vm.endRoom() },
                        onNewCode = roomCode?.takeIf { it.roomId == state.room?.id }?.let { { vm.newRoomCode() } },
                        onInvite = { menu = PlayerMenu.None; invitingMore = true },
                        roomCode = roomCode?.takeIf { it.roomId == state.room?.id }?.code,
                    )
                }
                val next = state.next
                val seconds = upNextIn
                if (next != null && seconds != null) {
                    UpNextCard(next, seconds, onPlayNow = { onPlayNext(next) }, onCancel = cancelUpNext)
                }
                // Watch Together: the lobby, "Waiting for Sam…", and who paused, played or moved the room.
                if (state.room != null || state.roomEnded != null) {
                    org.arcmediaserver.android.ui.together.TogetherOverlays(
                        room = state.room,
                        lobby = inLobby,
                        ended = state.roomEnded,
                        code = roomCode,
                        serverNow = vm::serverNow,
                        onStart = vm::togglePlay,
                        onLeave = onClose,
                        onNewCode = vm::newRoomCode,
                        onInvite = { invitingMore = true },
                    )
                }
                if (invitingMore && state.room?.hosting == true) {
                    org.arcmediaserver.android.ui.together.InvitePicker(
                        container,
                        org.arcmediaserver.android.ui.together.InviteTarget.MoreTo(state.room?.itemId),
                        onUseCode = null,
                        onClose = { invitingMore = false },
                    )
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
            org.arcmediaserver.android.ui.components.EpisodeArtwork(next, card.thumb, label = next.title, modifier = Modifier.width(120.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(6.dp)))
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
    /** Playback's rows: Zoom Mode and Speed change in place; the others open their own list. */
    onZoom: () -> Unit = {},
    onOpen: (PlayerMenu) -> Unit = {},
    /** Subtitle timing: a nudge in ms (negative earlier), or null to reset. */
    onShiftSubtitles: (Long?) -> Unit = {},
    /** Watch Together's menu: leave, end for everyone (host), a new code (host). */
    onLeaveRoom: () -> Unit = {},
    onEndRoom: () -> Unit = {},
    onNewCode: (() -> Unit)? = null,
    /** Invite People… (host). */
    onInvite: (() -> Unit)? = null,
    /** The code to share, for the room this TV hosts. */
    roomCode: String? = null,
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
            PlayerMenu.Playback -> {
                // The Apple TV app's Playback menu: Zoom Mode, Playback Speed, Quality, Version, and
                // the playback information (stats for nerds).
                title = stringResource(R.string.player_playback)
                val speedName = if (state.speed == 1f) stringResource(R.string.player_normal) else "${state.speed.toString().removeSuffix(".0")}×"
                val qualityNow = when {
                    state.quality == -1 -> stringResource(R.string.player_automatic)
                    state.quality > 0 -> qualityName(state.quality)
                    else -> stringResource(R.string.player_original)
                }
                options = buildList {
                    // In a room, the room is first; speed is the room's (everyone plays at 1×).
                    state.room?.let { room ->
                        add(Option(stringResource(R.string.together_title), pluralStringResource(R.plurals.together_in_room, room.participants.size, room.participants.size), false) { onOpen(PlayerMenu.Together) })
                    }
                    add(Option(stringResource(R.string.player_zoom), stringResource(if (state.zoom) R.string.player_zoom_fill else R.string.player_normal), false, onZoom))
                    if (state.room == null) add(Option(stringResource(R.string.player_speed), speedName, false) { onSpeed(org.arcmediaserver.android.playback.PlayerOsd.nextSpeed(speeds, state.speed)) })
                    add(Option(stringResource(R.string.player_quality), qualityNow, false) { onOpen(PlayerMenu.Quality) })
                    if (plan.versions.size > 1) {
                        val current = plan.versions.firstOrNull { it.fileId == plan.fileId }?.let { versionName(it).first }
                        add(Option(stringResource(R.string.player_version), current, false) { onOpen(PlayerMenu.Version) })
                    }
                    add(Option(stringResource(R.string.player_info), null, false) { onOpen(PlayerMenu.Info) })
                }
            }
            PlayerMenu.Together -> {
                // Who's here and how they're doing, then Leave, and the host's End for everyone.
                title = stringResource(R.string.together_title)
                val room = state.room
                options = if (room == null) emptyList() else buildList {
                    room.participants.forEach { p ->
                        val name = if (p.id == room.selfId) stringResource(R.string.together_you, p.name) else p.name
                        val status = org.arcmediaserver.android.ui.together.statusOf(p)
                        add(Option(name, if (p.id == room.hostId) stringResource(R.string.together_host) + " · " + status else status, false) {})
                    }
                    if (room.hosting && roomCode != null) add(Option(stringResource(R.string.together_code), org.arcmediaserver.android.data.WatchCode.format(roomCode), false) {})
                    if (room.hosting && onInvite != null) add(Option(stringResource(R.string.together_invite_people), null, false, onInvite))
                    if (room.hosting && onNewCode != null) add(Option(stringResource(R.string.together_new_code), null, false, onNewCode))
                    add(Option(stringResource(R.string.together_leave), null, false, onLeaveRoom))
                    if (room.hosting) add(Option(stringResource(R.string.together_end_for_everyone), null, false, onEndRoom))
                }
            }
            PlayerMenu.Audio -> {
                title = stringResource(R.string.player_audio)
                options = plan.audioTracks.mapIndexed { i, t -> Option(audioName(t, i), audioDetail(t), t.index == plan.audioStream) { onAudio(t.index) } }
            }
            PlayerMenu.Subtitles -> {
                title = stringResource(R.string.player_subtitles)
                val offset = org.arcmediaserver.android.playback.SubtitleTiming.offsetFor(state.subtitleOffsets, plan.fileId, state.subtitle)
                options = listOf(Option(stringResource(R.string.player_subtitles_off), null, state.subtitle == null) { onSubtitle(null) }) +
                    plan.subtitleTracks.filter { !it.image }.mapIndexed { i, t -> Option(subtitleName(t, i), subtitleDetail(t), state.subtitle?.id == t.id) { onSubtitle(t) } } +
                    // Timing, as the web player's, for the text subtitles shown now.
                    if (org.arcmediaserver.android.playback.SubtitleTiming.adjustable(state.subtitle)) {
                        listOf(Option(stringResource(R.string.player_subtitle_timing), stringResource(R.string.player_timing_seconds, timingSeconds(offset)), false) { onOpen(PlayerMenu.SubtitleTiming) })
                    } else emptyList()
                if (state.subtitle != null && !org.arcmediaserver.android.playback.SubtitleTiming.adjustable(state.subtitle)) {
                    body = stringResource(R.string.player_timing_text_only)
                }
            }
            PlayerMenu.SubtitleTiming -> {
                // Nudges apply at once and the menu stays open, so the line can be matched by eye.
                title = stringResource(R.string.player_subtitle_timing)
                val offset = org.arcmediaserver.android.playback.SubtitleTiming.offsetFor(state.subtitleOffsets, plan.fileId, state.subtitle)
                val max = org.arcmediaserver.android.playback.SubtitleTiming.MAX_MS
                val step = org.arcmediaserver.android.playback.SubtitleTiming.STEP_MS
                body = when {
                    offset > 0 -> stringResource(R.string.player_timing_later, timingSeconds(offset))
                    offset < 0 -> stringResource(R.string.player_timing_earlier, timingSeconds(offset))
                    else -> stringResource(R.string.player_timing_zero)
                }
                // The choices stay put (the focus mustn't lose its row); at ±10 s a nudge does nothing.
                options = listOf(
                    Option(stringResource(R.string.player_timing_show_earlier), null, false) { if (offset > -max) onShiftSubtitles(-step) },
                    Option(stringResource(R.string.player_timing_show_later), null, false) { if (offset < max) onShiftSubtitles(step) },
                    Option(stringResource(R.string.player_timing_reset), null, false) { onShiftSubtitles(null) },
                )
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
    Box(Modifier.fillMaxSize().padding(end = 48.dp, bottom = 150.dp), contentAlignment = Alignment.BottomEnd) {
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
