package org.arcmediaserver.android.ui.details

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.arcmediaserver.android.AppContainer
import org.arcmediaserver.android.data.ArcLog
import org.arcmediaserver.android.data.Item

/**
 * Theme Music, as the web's (web/src/components/ThemeMusic.tsx) and Plex: a film's or show's theme
 * tune plays quietly while its page is open, once, fading in, and fades out when the page is left
 * or anything else plays. The profile's Theme Music (`theme_music`, on unless turned off) decides.
 */
object ThemeTune {
    /** How loud the tune plays (the web's 0.25), and how it fades: this much every [STEP_MS]. */
    const val VOLUME = 0.25f
    const val FADE_STEP = 0.02f
    const val STEP_MS = 60L

    /** The tune to play on [item]'s page, or null: films and shows with one, while Theme Music is on. */
    fun urlFor(item: Item?, themeMusic: Boolean): String? =
        item?.takeIf { themeMusic && (it.isSeries || it.kind == "movie") }?.themeUrl?.takeIf { it.isNotBlank() }

    /** The volume one fade step after [now], heading for [target]. */
    fun step(now: Float, target: Float): Float =
        if (kotlin.math.abs(target - now) <= FADE_STEP) target else if (target > now) now + FADE_STEP else now - FADE_STEP
}

/** One tune playing on an audio-only player signed in to Arc, with its fades. */
private class TunePlayer(context: android.content.Context, container: AppContainer, url: String) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var fade: Job? = null
    private val player: ExoPlayer = org.arcmediaserver.android.ui.player.arcPlayer(context, container).apply {
        volume = 0f
        repeatMode = Player.REPEAT_MODE_OFF
        setMediaItem(MediaItem.fromUri(container.api()?.absolute(url) ?: url))
        addListener(object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                // A tune that won't play is nothing to show: the page carries on in silence.
                ArcLog.Android.debug("theme music didn't play: ${error.errorCodeName}")
            }
        })
        prepare()
        playWhenReady = true
    }

    init {
        fadeTo(ThemeTune.VOLUME)
    }

    private fun fadeTo(target: Float, done: () -> Unit = {}) {
        fade?.cancel()
        fade = scope.launch {
            while (player.volume != target) {
                delay(ThemeTune.STEP_MS)
                player.volume = ThemeTune.step(player.volume, target)
            }
            done()
        }
    }

    /** Fades out, then lets the player go. */
    fun stop() {
        fadeTo(0f) {
            player.release()
            scope.cancel() // ends this fade too: nothing follows it
        }
    }

}

/**
 * Plays [url] (a title's theme tune, or null for none) while this page is on screen: once per
 * visit, so coming back from the player doesn't start it again; leaving the page (or opening the
 * player, a trailer or another page over it) fades it out.
 */
@Composable
fun ThemeMusicEffect(key: String, url: String?) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as? org.arcmediaserver.android.ArcApplication)?.container }
    var played by rememberSaveable(key) { mutableStateOf(false) }
    var tune by remember { mutableStateOf<TunePlayer?>(null) }
    LifecycleResumeEffect(key, url) {
        if (url != null && !played && container != null) {
            played = true
            tune = TunePlayer(context, container, url)
        }
        onPauseOrDispose {
            tune?.stop()
            tune = null
        }
    }
}
