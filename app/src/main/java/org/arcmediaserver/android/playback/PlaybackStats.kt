package org.arcmediaserver.android.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.analytics.AnalyticsListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.arcmediaserver.android.data.ArcLog

/**
 * What the player is really doing ("stats for nerds"): the decoders it chose and the formats
 * they're fed, which is where a Dolby Vision stream shows whether it plays as Dolby Vision or falls
 * back to its HDR10 base, and the frames it dropped.
 */
data class PlaybackStats(
    val videoDecoder: String? = null,
    val videoMime: String? = null,
    val videoCodecs: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val frameRate: Float = 0f,
    /** C.COLOR_TRANSFER_*: ST2084 is PQ (HDR10 / Dolby Vision), HLG is HLG. */
    val colorTransfer: Int = Format.NO_VALUE,
    val videoBitrate: Int = Format.NO_VALUE,
    val audioDecoder: String? = null,
    val audioMime: String? = null,
    val audioChannels: Int = 0,
    val droppedFrames: Int = 0,
) {
    val dolbyVision: Boolean get() = videoMime == MimeTypes.VIDEO_DOLBY_VISION

    /** The dynamic range as played: Dolby Vision, HDR10, HLG or SDR. */
    val dynamicRange: String get() = when {
        dolbyVision -> "Dolby Vision"
        colorTransfer == C.COLOR_TRANSFER_ST2084 -> "HDR10"
        colorTransfer == C.COLOR_TRANSFER_HLG -> "HLG"
        colorTransfer == Format.NO_VALUE -> "–"
        else -> "SDR"
    }
}

/** Collects [PlaybackStats] from the player's analytics events and logs the decoder choices. */
@OptIn(UnstableApi::class)
class PlaybackStatsCollector(private val log: ArcLog = ArcLog.Android) : AnalyticsListener {
    private val _stats = MutableStateFlow(PlaybackStats())
    val stats: StateFlow<PlaybackStats> = _stats

    fun reset() {
        _stats.value = PlaybackStats()
    }

    override fun onVideoDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
        _stats.update { it.copy(videoDecoder = decoderName) }
        val s = _stats.value
        // Once per decoder: the line that answers "why isn't this Dolby Vision?".
        log.info("video decoder $decoderName for ${s.videoMime} ${s.videoCodecs.orEmpty()} (${s.dynamicRange})")
    }

    override fun onVideoInputFormatChanged(eventTime: AnalyticsListener.EventTime, format: Format, decoderReuseEvaluation: DecoderReuseEvaluation?) {
        _stats.update {
            it.copy(
                videoMime = format.sampleMimeType, videoCodecs = format.codecs, width = format.width, height = format.height,
                frameRate = format.frameRate, colorTransfer = format.colorInfo?.colorTransfer ?: Format.NO_VALUE, videoBitrate = format.bitrate,
            )
        }
    }

    override fun onAudioDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
        _stats.update { it.copy(audioDecoder = decoderName) }
        log.debug("audio decoder $decoderName for ${_stats.value.audioMime}")
    }

    override fun onAudioInputFormatChanged(eventTime: AnalyticsListener.EventTime, format: Format, decoderReuseEvaluation: DecoderReuseEvaluation?) {
        // The decoder (if any) is reported just before its format, so it's kept here; each new
        // stream starts from reset(). Passthrough never names a decoder.
        _stats.update { it.copy(audioMime = format.sampleMimeType, audioChannels = format.channelCount) }
    }

    override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
        _stats.update { it.copy(droppedFrames = it.droppedFrames + droppedFrames) }
    }
}
