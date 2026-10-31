package org.arcmediaserver.android.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import org.arcmediaserver.android.ArcApplication
import org.arcmediaserver.android.data.ArcLog

/**
 * A trailer or other extra, as the web plays them: straight from its file, with Media3's own
 * controls (play/pause and seeking with the D-pad), no progress kept. Back, or the end, closes it.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun ExtraPlayerRoute(url: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as ArcApplication).container
    // The end and Back can both close it: only the first counts, or the title page would go too.
    val closed = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    val close = { if (closed.compareAndSet(false, true)) onClose() }
    val player = remember {
        arcPlayer(context.applicationContext, container).apply {
            setMediaItem(MediaItem.fromUri(container.api()?.absolute(url) ?: url))
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) close()
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                ArcLog.Android.warn("extra failed to play: ${error.errorCodeName}")
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    BackHandler(onBack = close)
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
                setShowNextButton(false)
                setShowPreviousButton(false)
                requestFocus()
            }
        },
        modifier = Modifier.fillMaxSize().background(Color.Black),
    )
}
