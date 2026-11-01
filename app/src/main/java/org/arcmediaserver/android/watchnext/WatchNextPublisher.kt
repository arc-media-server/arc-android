package org.arcmediaserver.android.watchnext

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.tvprovider.media.tv.PreviewChannelHelper
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.arcmediaserver.android.data.ArcApi
import org.arcmediaserver.android.data.ArcLog
import org.arcmediaserver.android.data.WatchNextEntry
import org.arcmediaserver.android.data.WatchNextPublished
import org.arcmediaserver.android.data.planWatchNext
import org.arcmediaserver.android.data.watchNextEntries

/**
 * Keeps Google TV's "Continue watching" row in step with Arc's Continue Watching for the
 * signed-in profile: brought up to date when the app goes to the background, emptied on signing
 * out. Choosing a title there resumes it in Arc ([DeepLink]). Does nothing on devices without
 * the TV provider (phones, older Android TV launchers that lack the row).
 */
class WatchNextPublisher(private val context: Context, private val log: ArcLog = ArcLog.Android) {
    private val mutex = Mutex()
    private val helper by lazy { PreviewChannelHelper(context) }

    private val available: Boolean by lazy {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }

    /** Brings the row up to date from [api] (null: signed out, so the row is emptied). */
    suspend fun sync(api: ArcApi?) {
        if (!available) return
        mutex.withLock {
            val started = System.currentTimeMillis()
            try {
                val items = api?.continueWatching(withUpNext = true).orEmpty()
                val plan = planWatchNext(watchNextEntries(items, started), published())
                plan.remove.forEach { context.contentResolver.delete(TvContractCompat.buildWatchNextProgramUri(it), null, null) }
                plan.update.forEach { (id, entry) -> helper.updateWatchNextProgram(program(entry), id) }
                plan.add.forEach { helper.publishWatchNextProgram(program(it)) }
                val changed = plan.add.size + plan.update.size + plan.remove.size
                val summary = "watch next: ${plan.add.size} added, ${plan.update.size} updated, ${plan.remove.size} removed in ${System.currentTimeMillis() - started} ms"
                if (plan.add.isNotEmpty() || plan.remove.isNotEmpty()) log.info(summary) else if (changed > 0) log.debug(summary)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // The launcher's provider can refuse (no Watch Next on this device, or it's busy).
                log.warn("watch next update failed: ${e.javaClass.simpleName}")
            }
        }
    }

    /** This app's programs in the row (the provider only shows an app its own). */
    @SuppressLint("RestrictedApi")
    private fun published(): List<WatchNextPublished> {
        val out = mutableListOf<WatchNextPublished>()
        context.contentResolver.query(TvContractCompat.WatchNextPrograms.CONTENT_URI, WatchNextProgram.PROJECTION, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val p = WatchNextProgram.fromCursor(c)
                val itemId = p.internalProviderId ?: continue
                out += WatchNextPublished(p.id, itemId, p.lastPlaybackPositionMillis.toLong(), p.isBrowsable)
            }
        }
        return out
    }

    private fun program(e: WatchNextEntry): WatchNextProgram {
        val b = WatchNextProgram.Builder()
        b.setType(if (e.isEpisode) TvContractCompat.WatchNextPrograms.TYPE_TV_EPISODE else TvContractCompat.WatchNextPrograms.TYPE_MOVIE)
            .setWatchNextType(if (e.partWay) TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE else TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_NEXT)
            .setLastEngagementTimeUtcMillis(e.engagedAt)
            .setTitle(e.title)
            .setInternalProviderId(e.itemId)
            .setIntentUri(DeepLink.play(e.itemId))
        e.episodeTitle?.let { b.setEpisodeTitle(it) }
        e.season?.let { b.setSeasonNumber(it) }
        e.episode?.let { b.setEpisodeNumber(it) }
        e.overview?.let { b.setDescription(it) }
        e.year?.takeIf { !e.isEpisode }?.let { b.setReleaseDate(it.toString()) }
        if (e.durationMs > 0) b.setDurationMillis(e.durationMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        if (e.partWay) b.setLastPlaybackPositionMillis(e.positionMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        val art = e.thumb ?: e.poster
        if (art != null) {
            b.setPosterArtUri(ArtworkProvider.uri(art))
            b.setPosterArtAspectRatio(if (e.thumb != null) TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9 else TvContractCompat.PreviewPrograms.ASPECT_RATIO_2_3)
        }
        return b.build()
    }
}

/** arc://play/{id}: resume a title from outside the app (Google TV's row). */
object DeepLink {
    fun play(itemId: String): Uri = Uri.Builder().scheme("arc").authority("play").appendPath(itemId).build()

    /** The title to resume, if [uri] is one of ours. */
    fun itemToPlay(uri: Uri?): String? =
        uri?.takeIf { it.scheme == "arc" && it.host == "play" }?.pathSegments?.singleOrNull()?.takeIf { it.isNotBlank() }
}
