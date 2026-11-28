package org.arcmediaserver.android.ui.mobile

import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.delay
import org.arcmediaserver.android.ArcApplication
import org.arcmediaserver.android.R
import org.arcmediaserver.android.data.ArcLog
import org.arcmediaserver.android.data.Item
import org.arcmediaserver.android.playback.UpNext
import org.arcmediaserver.android.ui.components.ArcIcons
import org.arcmediaserver.android.ui.components.episodeWithTitle
import org.arcmediaserver.android.ui.components.text
import org.arcmediaserver.android.ui.details.clock
import org.arcmediaserver.android.ui.player.PlayerUiState
import org.arcmediaserver.android.ui.player.PlayerViewModel
import org.arcmediaserver.android.ui.player.VideoSurface
import org.arcmediaserver.android.ui.player.audioDetail
import org.arcmediaserver.android.ui.player.audioName
import org.arcmediaserver.android.ui.player.findActivity
import org.arcmediaserver.android.ui.player.skippable
import org.arcmediaserver.android.ui.player.subtitleDetail
import org.arcmediaserver.android.ui.player.subtitleName
import org.arcmediaserver.android.ui.theme.ArcColors

/**
 * The phone's player: the TV's player underneath (the same view model, stream decisions, resume
 * reporting and subtitles), with touch controls as Android's own players have them. It turns the
 * phone sideways and hides the system bars while it's open; tap shows or hides the controls,
 * double-tap either side goes back or forward 10 seconds, and leaving the app pauses.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun MobilePlayerRoute(
    itemId: String,
    startMs: Long,
    entryId: String?,
    carryOn: Boolean,
    fileId: Long?,
    onClose: () -> Unit,
    onPlayNextEntry: (Item, String?) -> Unit,
) {
    val context = LocalContext.current
    val container = (context.applicationContext as ArcApplication).container
    val vm: PlayerViewModel = viewModel(factory = viewModelFactory { initializer { PlayerViewModel(container, context.applicationContext, itemId, startMs, entryId, carryOn, fileId, together = false) } })
    val state by vm.state.collectAsStateWithLifecycle()
    FullScreenLandscape()
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.pause() }

    var controls by remember { mutableStateOf(true) }
    var lastTouch by remember { mutableLongStateOf(0L) }
    var sheet by remember { mutableStateOf(false) }
    // Where the slider's thumb is while it's being dragged; the video jumps there on release.
    var dragMs by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(lastTouch, state.playing, sheet, dragMs) {
        if (state.playing && !sheet && dragMs == null) {
            delay(4_000)
            controls = false
        }
    }

    // Up Next at the end, as the TV's: a countdown to the next episode (Auto-Play on), else close.
    var upNextIn by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(state.ended) {
        if (!state.ended) return@LaunchedEffect
        when (UpNext.atEnd(state.next, state.autoPlay)) {
            UpNext.AtEnd.Countdown -> if (upNextIn == null) upNextIn = UpNext.COUNTDOWN_SECONDS
            UpNext.AtEnd.Close -> onClose()
        }
    }
    LaunchedEffect(upNextIn) {
        val seconds = upNextIn ?: return@LaunchedEffect
        if (seconds <= 0) state.next?.let { onPlayNextEntry(it, state.nextEntryId) } else {
            delay(1_000)
            upNextIn = seconds - 1
        }
    }
    BackHandler(enabled = upNextIn != null) { upNextIn = null; onClose() }

    Box(
        Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) {
            detectTapGestures(
                onTap = { controls = !controls; lastTouch = System.currentTimeMillis() },
                onDoubleTap = { at ->
                    val back = at.x < size.width / 2f
                    vm.seekBy(if (back) -10_000 else 10_000)
                    lastTouch = System.currentTimeMillis()
                },
            )
        },
    ) {
        VideoSurface(vm, state)
        when {
            state.stopped != null -> CenterMessage(stringResource(R.string.player_stopped_title), state.stopped!!, stringResource(R.string.player_close), onClose)
            state.error != null -> CenterMessage(stringResource(R.string.player_failed), state.error!!.text(), stringResource(R.string.action_retry), vm::retry, onClose)
            else -> {
                if (state.loading) CircularProgressIndicator(Modifier.align(Alignment.Center).size(48.dp), color = Color.White)
                AnimatedVisibility(controls, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
                    Controls(
                        state,
                        dragMs = dragMs,
                        onDrag = { dragMs = it; lastTouch = System.currentTimeMillis() },
                        onDragEnd = { dragMs?.let(vm::seekTo); dragMs = null },
                        onTogglePlay = { vm.togglePlay(); lastTouch = System.currentTimeMillis() },
                        onSeekBy = { vm.seekBy(it); lastTouch = System.currentTimeMillis() },
                        onTracks = { sheet = true },
                        onClose = onClose,
                    )
                }
                // Skip Intro / Recap / Credits shows whether or not the controls do, as the TV's.
                val marker = state.plan?.let { skippable(it.markers, state.positionMs) }
                if (marker != null) {
                    OutlinedButton(
                        onClick = { ArcLog.Android.info("player: skipped the ${marker.kind}"); vm.seekTo(marker.endMs) },
                        modifier = Modifier.align(Alignment.BottomEnd).safeDrawingPadding().padding(end = 24.dp, bottom = 96.dp),
                    ) {
                        Text(stringResource(when (marker.kind) { "recap" -> R.string.player_skip_recap; "credits" -> R.string.player_skip_credits; else -> R.string.player_skip_intro }))
                    }
                }
                val next = state.next
                val seconds = upNextIn
                if (next != null && seconds != null) UpNextBar(next, seconds, onPlayNow = { onPlayNextEntry(next, state.nextEntryId) }, onCancel = { upNextIn = null; onClose() })
            }
        }
    }
    if (sheet) TracksSheet(state, vm, onDismiss = { sheet = false })
}

/** Sideways and full screen while the player is open; the phone's own orientation and bars after. */
@Composable
private fun FullScreenLandscape() {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity) {
        val before = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val bars = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        bars.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            bars.show(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = before
        }
    }
}

@Composable
internal fun Controls(
    state: PlayerUiState,
    dragMs: Long?,
    onDrag: (Long) -> Unit,
    onDragEnd: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onTracks: () -> Unit,
    onClose: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.6f), 0.3f to Color.Transparent, 0.65f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f)))) {
        // Title, and the episode it is, at the top with Close and the tracks.
        Row(Modifier.fillMaxWidth().safeDrawingPadding().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(ArcIcons.ArrowLeft, contentDescription = stringResource(R.string.player_close_player), tint = Color.White) }
            val item = state.item
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                if (item != null) {
                    Text(if (item.isEpisode) item.seriesTitle ?: item.title else item.title, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (item.isEpisode) Text(episodeWithTitle(item), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            val plan = state.plan
            if (plan != null && (plan.audioTracks.size > 1 || plan.subtitleTracks.isNotEmpty())) {
                IconButton(onClick = onTracks) { Icon(ArcIcons.Captions, contentDescription = stringResource(R.string.mobile_player_tracks), tint = Color.White) }
            }
        }
        // Back 10 seconds, play or pause, forward 10 seconds, in the middle.
        Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(40.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onSeekBy(-10_000) }, modifier = Modifier.size(56.dp)) { Icon(ArcIcons.RotateCcw, contentDescription = stringResource(R.string.mobile_player_back_10), tint = Color.White, modifier = Modifier.size(32.dp)) }
            IconButton(onClick = onTogglePlay, modifier = Modifier.size(72.dp)) {
                Icon(
                    if (state.playing) ArcIcons.Pause else ArcIcons.Play,
                    contentDescription = stringResource(if (state.playing) R.string.player_pause else R.string.player_play),
                    tint = Color.White, modifier = Modifier.size(48.dp),
                )
            }
            IconButton(onClick = { onSeekBy(10_000) }, modifier = Modifier.size(56.dp)) { Icon(ArcIcons.RotateCw, contentDescription = stringResource(R.string.mobile_player_forward_10), tint = Color.White, modifier = Modifier.size(32.dp)) }
        }
        // The timeline: where it is, the slider, and the time left.
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
            val duration = state.durationMs.coerceAtLeast(1)
            val shown = dragMs ?: state.positionMs
            Slider(
                value = (shown.toFloat() / duration).coerceIn(0f, 1f),
                onValueChange = { onDrag((it * duration).toLong()) },
                onValueChangeFinished = onDragEnd,
                enabled = state.durationMs > 0,
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.3f)),
            )
            Row(Modifier.fillMaxWidth()) {
                Text(clock(shown), style = MaterialTheme.typography.labelMedium, color = Color.White)
                Spacer(Modifier.weight(1f))
                Text("−" + clock((state.durationMs - shown).coerceAtLeast(0)), style = MaterialTheme.typography.labelMedium, color = Color.White)
            }
        }
    }
}

/** The next episode, counting down, with Play Now and Cancel (which closes, as the TV's at the end). */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.UpNextBar(next: Item, seconds: Int, onPlayNow: () -> Unit, onCancel: () -> Unit) {
    Column(
        Modifier.align(Alignment.BottomEnd).safeDrawingPadding().padding(24.dp).width(320.dp)
            .background(ArcColors.Surface.copy(alpha = 0.95f), androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).padding(16.dp),
    ) {
        Text(stringResource(R.string.player_next_title, if (next.isEpisode) episodeWithTitle(next) else next.title), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(stringResource(R.string.mobile_player_next_in, seconds), style = MaterialTheme.typography.bodySmall, color = ArcColors.Muted)
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onPlayNow) { Text(stringResource(R.string.player_play_next)) }
            OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
        }
    }
}

@Composable
private fun CenterMessage(title: String, body: String, action: String, onAction: () -> Unit, onClose: (() -> Unit)? = null) {
    Column(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White)
        Spacer(Modifier.size(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = ArcColors.Muted)
        Spacer(Modifier.size(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onAction) { Text(action) }
            if (onClose != null) OutlinedButton(onClick = onClose) { Text(stringResource(R.string.player_close)) }
        }
    }
}

/** Audio and subtitles in a bottom sheet, as Android's players offer them; choices follow the profile, as on the TV. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TracksSheet(state: PlayerUiState, vm: PlayerViewModel, onDismiss: () -> Unit) {
    val plan = state.plan ?: return
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = ArcColors.Surface) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
            if (plan.audioTracks.size > 1) {
                SheetHeading(stringResource(R.string.mobile_player_audio))
                plan.audioTracks.forEachIndexed { i, t ->
                    TrackRow(audioName(t, i), audioDetail(t).ifBlank { null }, chosen = t.index == plan.audioStream) { vm.chooseAudio(t.index); onDismiss() }
                }
            }
            SheetHeading(stringResource(R.string.settings_subtitles))
            TrackRow(stringResource(R.string.settings_off), null, chosen = state.subtitle == null) { vm.chooseSubtitle(null); onDismiss() }
            plan.subtitleTracks.forEachIndexed { i, t ->
                TrackRow(subtitleName(t, i), subtitleDetail(t), chosen = state.subtitle?.id == t.id) { vm.chooseSubtitle(t); onDismiss() }
            }
        }
    }
}

@Composable
private fun SheetHeading(text: String) =
    Text(text, style = MaterialTheme.typography.titleSmall, color = ArcColors.Muted, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp))

@Composable
private fun TrackRow(name: String, detail: String?, chosen: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(name) },
        supportingContent = detail?.let { { Text(it) } },
        trailingContent = if (chosen) { { Icon(ArcIcons.Check, contentDescription = null) } } else null,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp),
    )
}
