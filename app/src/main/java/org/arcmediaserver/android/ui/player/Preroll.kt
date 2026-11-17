package org.arcmediaserver.android.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.withTimeoutOrNull
import org.arcmediaserver.android.ArcApplication
import org.arcmediaserver.android.R
import org.arcmediaserver.android.data.ArcLog
import org.arcmediaserver.android.data.PrerollClip
import org.arcmediaserver.android.ui.components.ArcIcons
import org.arcmediaserver.android.ui.components.Spinner
import org.arcmediaserver.android.ui.components.requestWhenReady

/**
 * Pre-roll videos before a film, as Plex's cinema trailers and the web player
 * (`GET /items/{id}/preroll`, set in the web's Settings): when [enabled], the clips play one after
 * another, each with Skip, then [content] (the film's player) starts. Back leaves the player. A
 * clip that fails, or a list that doesn't come within a few seconds, is passed over: pre-rolls
 * never stand between someone and their film.
 */
@Composable
fun PrerollGate(itemId: String, enabled: Boolean, onClose: () -> Unit, content: @Composable () -> Unit) {
    val container = (LocalContext.current.applicationContext as ArcApplication).container
    var clips by remember { mutableStateOf<List<PrerollClip>?>(if (enabled) null else emptyList()) }
    // Kept across recreation, so a played clip isn't played again.
    var index by rememberSaveable { mutableStateOf(0) }
    var done by rememberSaveable { mutableStateOf(!enabled) }
    LaunchedEffect(itemId, enabled) {
        if (done) return@LaunchedEffect
        val list = withTimeoutOrNull(PREROLL_WAIT_MS) {
            try {
                container.api()?.preroll(itemId).orEmpty()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                ArcLog.Android.warn("pre-rolls didn't load: ${e.javaClass.simpleName}")
                emptyList()
            }
        }
        if (list == null) ArcLog.Android.warn("pre-rolls didn't load in time; playing the film")
        clips = list.orEmpty()
        if (clips.isNullOrEmpty()) done = true else ArcLog.Android.info("player: ${clips!!.size} pre-roll(s) before the film")
    }
    val list = clips
    when {
        done -> content()
        list == null -> {
            BackHandler(onBack = onClose)
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) { Spinner(size = 56.dp) }
        }
        index >= list.size -> LaunchedEffect(Unit) { done = true }
        else -> PrerollClipPlayer(
            list[index],
            onDone = { if (index + 1 >= list.size) done = true else index++ },
            onSkip = {
                ArcLog.Android.debug("player: pre-roll skipped")
                if (index + 1 >= list.size) done = true else index++
            },
            onClose = onClose,
        )
    }
}

/** One pre-roll, full screen, with Skip (focused) at the bottom right, as the web's. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun PrerollClipPlayer(clip: PrerollClip, onDone: () -> Unit, onSkip: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as ArcApplication).container
    // The end, a failure and Skip can each move on: only the first counts.
    val finished = remember(clip) { java.util.concurrent.atomic.AtomicBoolean(false) }
    val finish = { action: () -> Unit -> if (finished.compareAndSet(false, true)) action() }
    val player = remember(clip) {
        arcPlayer(context.applicationContext, container).apply {
            setMediaItem(MediaItem.fromUri(container.api()?.absolute(clip.url) ?: clip.url))
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) finish(onDone)
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                ArcLog.Android.warn("pre-roll failed to play: ${error.errorCodeName}; moving on")
                finish(onDone)
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    BackHandler(onBack = onClose)
    val skip = remember { FocusRequester() }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    isFocusable = false
                    keepScreenOn = true
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = { it.player = player },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize(),
        )
        Button(
            onClick = { finish(onSkip) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = 48.dp).focusRequester(skip),
            colors = ButtonDefaults.colors(
                containerColor = Color.Black.copy(alpha = 0.55f), contentColor = Color.White,
                focusedContainerColor = Color.White, focusedContentColor = Color.Black,
            ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.player_skip_preroll), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(8.dp))
                Icon(ArcIcons.SkipForward, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
    LaunchedEffect(clip) { skip.requestWhenReady() }
}

/** How long the pre-roll list may take before the film plays without it. */
private const val PREROLL_WAIT_MS = 3_000L
