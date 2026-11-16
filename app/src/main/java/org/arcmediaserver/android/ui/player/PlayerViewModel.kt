package org.arcmediaserver.android.ui.player

import io.github.peerless2012.ass.media.type.AssRenderType
import io.github.peerless2012.ass.media.parser.AssSubtitleParserFactory
import io.github.peerless2012.ass.media.kt.withAssSupport
import io.github.peerless2012.ass.media.AssHandlerConfig
import io.github.peerless2012.ass.media.AssHandler
import androidx.media3.extractor.text.SubtitleParser
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.common.Format
import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.common.text.Cue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import org.arcmediaserver.android.playback.TextSubtitles
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.arcmediaserver.android.AppContainer
import org.arcmediaserver.android.data.ApiException
import org.arcmediaserver.android.data.ArcApi
import org.arcmediaserver.android.data.ArcLog
import org.arcmediaserver.android.data.Item
import org.arcmediaserver.android.data.PlaybackPlan
import org.arcmediaserver.android.data.PlaybackRequest
import org.arcmediaserver.android.data.PlayerCaps
import org.arcmediaserver.android.data.ProgressReport
import org.arcmediaserver.android.data.SubtitleTrack
import org.arcmediaserver.android.playback.PlaybackClock
import org.arcmediaserver.android.data.Prefs
import org.arcmediaserver.android.data.Trickplay
import org.arcmediaserver.android.playback.UpNext
import org.arcmediaserver.android.playback.audioLanguageToRemember
import org.arcmediaserver.android.playback.chooseSubtitle
import org.arcmediaserver.android.playback.rememberedSubtitleLanguage
import org.arcmediaserver.android.playback.fallbackCaps
import org.arcmediaserver.android.ui.components.UiError
import org.arcmediaserver.android.ui.components.toUiError

data class PlayerUiState(
    val item: Item? = null,
    val plan: PlaybackPlan? = null,
    val loading: Boolean = true,
    val playing: Boolean = false,
    /** Where in the file, and how long the file is. */
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedMs: Long = 0,
    val subtitle: SubtitleTrack? = null,
    val error: UiError? = null,
    /** Arc stopped this playback (from its Dashboard), with the admin's message. */
    val stopped: String? = null,
    val ended: Boolean = false,
    /** The episode that follows, for Next and Play Next: the queue's next title, else Auto-Play's. */
    val next: Item? = null,
    /** [next]'s entry in the play queue, when it comes from the queue. */
    val nextEntryId: String? = null,
    /** -1 Automatic, 0 Original, or a limit in kbit/s (the web player's quality menu). */
    val quality: Int = -1,
    val speed: Float = 1f,
    /**
     * Whether the next title is offered (and counted down to): the profile's Auto-Play, or always
     * for the play queue's next title (it was queued on purpose).
     */
    val autoPlay: Boolean = true,
    /** The profile's Skip Intros Automatically. */
    val autoSkip: Boolean = false,
    /** The profile's subtitle look, for text subtitles. */
    val subtitleStyle: org.arcmediaserver.android.data.SubtitleStyle = org.arcmediaserver.android.data.SubtitleStyle(),
    /** The playing file's timeline thumbnails, or null when it has none. */
    val trickplay: Trickplay? = null,
    /** The controls' Cast tab: the episode's own actors, else the show's. */
    val cast: List<org.arcmediaserver.android.data.Credit> = emptyList(),
    /** The controls' Episodes tab: the playing episode's season. */
    val episodes: List<Item> = emptyList(),
    /** Zoom Mode: the picture fills the screen, cropping its edges. */
    val zoom: Boolean = false,
)

/**
 * An ExoPlayer signed in to Arc (streams and files go through the app's authorised client). With
 * [ass], ASS/SSA scripts the app loads from Arc (subtitle ids starting "arc-") are drawn by libass,
 * keeping their fonts, colours, positions and effects; every other subtitle is read as usual.
 */
@androidx.annotation.OptIn(UnstableApi::class)
fun arcPlayer(context: Context, container: AppContainer, ass: AssHandler? = null): ExoPlayer {
    val renderers = DefaultRenderersFactory(context).setEnableDecoderFallback(true)
    val sources = DefaultMediaSourceFactory(context).setDataSourceFactory(OkHttpDataSource.Factory(container.mediaClient))
    if (ass != null) sources.setSubtitleParserFactory(ArcSubtitleParsers(AssSubtitleParserFactory(ass)))
    val player = ExoPlayer.Builder(context)
        .setRenderersFactory(if (ass != null) renderers.withAssSupport(ass) else renderers)
        .setMediaSourceFactory(sources)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
        .setHandleAudioBecomingNoisy(true)
        .build()
    ass?.init(player)
    return player
}

/**
 * Sends the ASS script the app loaded from Arc to libass, and every other subtitle (including ASS
 * tracks inside a direct-played file, which the app doesn't show) to Media3's own parsers, so
 * libass only ever has the one track it's drawing.
 */
@androidx.annotation.OptIn(UnstableApi::class)
private class ArcSubtitleParsers(private val ass: AssSubtitleParserFactory) : SubtitleParser.Factory {
    private val default = DefaultSubtitleParserFactory()
    // The id can come back prefixed ("1:arc-…") once Media3 merges the subtitle with the video.
    private fun libass(format: Format) = format.sampleMimeType == MimeTypes.TEXT_SSA && format.id?.contains("arc-") == true
    override fun supportsFormat(format: Format) = if (libass(format)) ass.supportsFormat(format) else default.supportsFormat(format)
    override fun getCueReplacementBehavior(format: Format) = if (libass(format)) ass.getCueReplacementBehavior(format) else default.getCueReplacementBehavior(format)
    override fun create(format: Format): SubtitleParser {
        if (libass(format)) {
            ArcLog.Android.info("ASS subtitles drawn by libass")
            return ass.create(format)
        }
        if (format.sampleMimeType == MimeTypes.TEXT_SSA) ArcLog.Android.debug("an ASS track read as plain text (not Arc's)")
        return default.create(format)
    }
}

/**
 * Plays a movie or episode the way Arc's web player does: ask Arc how to play it with what this
 * device can play, play the file or the converted stream, save progress every 10 seconds and on
 * pause and close, keep a converting session alive, and end it when done.
 */
@OptIn(UnstableApi::class)
class PlayerViewModel(
    private val container: AppContainer,
    context: Context,
    private val itemId: String,
    private val startMs: Long,
    /** This title's entry in the play queue (playing on through it), or null to start a new queue. */
    private val entryId: String? = null,
    /** Auto-Play's next episode: it joins the end of the queue rather than starting a new one. */
    private val carryOn: Boolean = false,
    /** The version (file) chosen with Play Version… on the title's page, or null for Arc's choice. */
    private val fileId: Long? = null,
) : ViewModel() {
    private val api: ArcApi = container.api() ?: error("no server")
    private val log = ArcLog.Android
    private var currentCaps: PlayerCaps = container.playerCaps.also {
        // Once per player: what Arc is told this TV shows and plays (no ids or addresses).
        log.info("this TV: hdr ${it.hdrFormats ?: "unknown"}, Dolby Vision profiles ${it.dolbyVisionProfiles ?: "none"}, passthrough ${it.audioPassthrough}, max ${it.maxHeight}p")
    }
    private var plan: PlaybackPlan? = null
    /** The profile's preferences: the languages to start with and remember, Auto-Play. */
    private var prefs = Prefs()
    private var trickplayFile: Long? = null
    private var retried = false
    private var loadJob: Job? = null
    private var maxKbps: Int? = null

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state

    /** Draws ASS/SSA subtitles with libass, over the player's subtitle view (PlayerScreen). */
    @androidx.annotation.OptIn(UnstableApi::class)
    val ass = AssHandler(AssRenderType.OVERLAY_OPEN_GL, AssHandlerConfig(maxRenderPixels = 1920 * 1080))

    val player: ExoPlayer = arcPlayer(context, container, ass)

    /** Stats for nerds: the decoders and formats really in use. */
    private val statsCollector = org.arcmediaserver.android.playback.PlaybackStatsCollector().also { player.addAnalyticsListener(it) }
    val stats: StateFlow<org.arcmediaserver.android.playback.PlaybackStats> = statsCollector.stats

    /** Text subtitles the app draws itself (see [TextSubtitles]); ASS/SSA go to libass instead. */
    private val _cues = MutableStateFlow<List<Cue>>(emptyList())
    val cues: StateFlow<List<Cue>> = _cues
    private var textSubtitles: TextSubtitles? = null
    private var textJob: Job? = null

    /** What this TV told Arc it plays, for the stats panel. */
    val caps: PlayerCaps get() = currentCaps

    init {
        // Playing changes its watched state on the server: what's loaded next is right again.
        container.itemChanges.remove(itemId)
    }

    init {
        player.addListener(object : Player.Listener {
            // The subtitle the app loaded from Arc is the one shown, not a track of the same
            // language inside the file.
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                val ours = tracks.groups.firstOrNull { g -> g.type == C.TRACK_TYPE_TEXT && (0 until g.length).any { g.getTrackFormat(it).id?.contains("arc-") == true } } ?: return
                if (ours.isSelected) return
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setOverrideForType(androidx.media3.common.TrackSelectionOverride(ours.mediaTrackGroup, 0))
                    .build()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(playing = isPlaying) }
                if (!isPlaying && player.playbackState == Player.STATE_READY && !player.playWhenReady) report("paused")
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) _state.update { it.copy(loading = false) }
                if (playbackState == Player.STATE_BUFFERING) _state.update { it.copy(loading = true) }
                if (playbackState == Player.STATE_ENDED) {
                    val p = plan ?: return
                    report("stopped", position = p.durationMs)
                    _state.update { it.copy(ended = true) }
                    // Nothing after it: the queue is done, and Arc forgets it.
                    if (_state.value.next == null) container.appScope.launch { container.queues.end() }
                }
            }

            override fun onPlayerError(error: PlaybackException) = onStreamError(error)
        })
        val loadedPrefs = viewModelScope.async {
            runCatching { api.prefs() }.onFailure { log.warn("preferences failed to load: ${it.javaClass.simpleName}") }.getOrNull()
                ?.also { container.hideSpoilers.value = it.hideSpoilers } ?: Prefs()
        }
        viewModelScope.launch {
            // The title, and the episode after it unless the profile turned Auto-Play off (as the
            // web player, which then neither offers nor counts down to one).
            val item = runCatching { api.item(itemId) }.getOrNull()
            _state.update { it.copy(item = item) }
            if (item != null) launch { loadCastAndEpisodes(item) }
            val autoPlay = loadedPrefs.await().autoPlay
            // The play queue follows what's playing: this title's entry, or a new queue of it.
            val queues = container.queues
            val entry = when {
                entryId != null -> entryId.also { queues.moveTo(it) }
                carryOn -> queues.carryOnWith(itemId)
                else -> queues.start(listOf(itemId))?.currentEntryId
            }
            val queued = queues.queue.value?.takeIf { entry != null && it.currentEntryId == entry }?.nextAvailable()
            if (queued?.item != null) {
                _state.update { it.copy(next = queued.item, nextEntryId = queued.id, autoPlay = true) }
                return@launch
            }
            val next = if (item?.isEpisode == true && autoPlay) runCatching { api.next(itemId) }.getOrNull() else null
            _state.update { it.copy(next = UpNext.offered(next, autoPlay), autoPlay = autoPlay) }
        }
        viewModelScope.launch {
            prefs = loadedPrefs.await()
            _state.update { it.copy(subtitleStyle = prefs.subtitleStyle, autoSkip = prefs.autoSkip) }
            load(startMs, fileId = fileId)
        }
        viewModelScope.launch { tick() }
        viewModelScope.launch { drawCues() }
        viewModelScope.launch { keepAlive() }
    }

    /**
     * The controls' Cast and Episodes tabs: the episode's actors, else the show's; and the season
     * the episode is in. Either missing just leaves its tab out.
     */
    private suspend fun loadCastAndEpisodes(item: Item) {
        val actors = { credits: List<org.arcmediaserver.android.data.Credit> -> credits.filter { it.role == "actor" } }
        var cast = actors(item.credits)
        val seriesId = item.parentId
        if (item.isEpisode && seriesId != null) {
            if (cast.isEmpty()) {
                cast = runCatching { actors(api.item(seriesId).credits) }
                    .onFailure { log.debug("player: the show's cast didn't load: ${it.javaClass.simpleName}") }
                    .getOrDefault(emptyList())
            }
            val episodes = runCatching { api.seasons(seriesId).seasons.firstOrNull { it.seasonNumber == item.seasonNumber }?.episodes.orEmpty() }
                .onFailure { log.debug("player: the season's episodes didn't load: ${it.javaClass.simpleName}") }
                .getOrDefault(emptyList())
            _state.update { it.copy(episodes = episodes) }
        }
        _state.update { it.copy(cast = cast) }
    }

    fun toggleZoom() = _state.update { it.copy(zoom = !it.zoom) }

    /** Where in the file the player is now. */
    private fun position(): Long = plan?.let { PlaybackClock.filePosition(it, player.currentPosition.coerceAtLeast(0)) } ?: startMs

    /** Starts (or restarts) playback at a point in the file, optionally with another audio track. */
    fun load(fromMs: Long, audioStream: Int? = null, fileId: Long? = null) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            plan?.sessionId?.let { sid -> runCatching { api.stopTranscode(sid) } }
            val p = try {
                api.startPlayback(itemId, PlaybackRequest(
                    currentCaps,
                    startMs = fromMs,
                    fileId = fileId ?: plan?.fileId,
                    // A new version starts with its own default audio.
                    audioStream = audioStream ?: plan?.audioStream?.takeIf { it >= 0 && fileId == null },
                    maxKbps = maxKbps,
                ))
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (e is ApiException && e.status == 403) {
                    _state.update { it.copy(stopped = e.message, loading = false) }
                } else {
                    log.warn("playback decision failed: ${e.javaClass.simpleName} ${(e as? ApiException)?.status ?: ""}")
                    _state.update { it.copy(error = e.toUiError(), loading = false) }
                }
                return@launch
            }
            plan = p
            // Why it converts is worth explaining later (CLAUDE.md, Logging).
            if (p.method == "direct") log.info("playing directly")
            else log.info("Arc is ${if (p.method == "remux") "repackaging" else "converting"}: ${p.reasons.joinToString()}${p.encoder?.let { " ($it)" } ?: ""}")
            val first = _state.value.plan == null
            val subtitle = if (first) {
                chooseSubtitle(p.subtitleTracks, prefs.subtitleLanguage, p.audioTracks.find { it.index == p.audioStream }?.language, prefs.subtitleMode)
            } else {
                _state.value.subtitle?.takeIf { s -> p.subtitleTracks.any { it.id == s.id } }
            }
            _state.update { it.copy(plan = p, durationMs = p.durationMs, subtitle = subtitle) }
            install(p, fromMs, subtitle)
            if (p.fileId != trickplayFile) loadTrickplay(p.fileId)
        }
    }

    /** The file's timeline thumbnails for scrubbing; none (yet) is normal and just shows none. */
    private fun loadTrickplay(fileId: Long) {
        trickplayFile = fileId
        _state.update { it.copy(trickplay = null) }
        viewModelScope.launch {
            val thumbs = try {
                api.trickplay(itemId, fileId)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                log.debug("timeline thumbnails failed: ${e.javaClass.simpleName}")
                null
            }
            if (thumbs == null) log.debug("no timeline thumbnails for this file")
            if (trickplayFile == fileId) _state.update { it.copy(trickplay = thumbs) }
        }
    }

    /** Remembers a track choice on the profile, as the web player does, so the next title starts with it. */
    private fun remember(field: String, value: String) {
        prefs = when (field) {
            "audio_language" -> prefs.copy(audioLanguage = value)
            else -> prefs.copy(subtitleLanguage = value)
        }
        viewModelScope.launch {
            try {
                api.updatePrefs(mapOf(field to value))
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                log.warn("saving the $field preference failed: ${e.javaClass.simpleName} ${(e as? ApiException)?.status ?: ""}")
            }
        }
    }

    private fun install(p: PlaybackPlan, fileMs: Long, subtitle: SubtitleTrack?) {
        statsCollector.reset()
        statsCollector.streamKbps = p.sourceKbps
        val builder = MediaItem.Builder().setUri(api.absolute(p.url))
        if (p.hls) {
            builder.setMimeType(MimeTypes.APPLICATION_M3U8)
            // Arc's playlist grows as it converts (an HLS "event" playlist). Play from the start
            // of it at normal speed, rather than racing to its live edge.
            builder.setLiveConfiguration(MediaItem.LiveConfiguration.Builder().setMinPlaybackSpeed(1f).setMaxPlaybackSpeed(1f).build())
        }
        // ASS/SSA as its own script (with the video's fonts) for libass, which the player loads.
        // Everything else is WebVTT the app loads and draws itself, so the film never waits for it.
        val ass = subtitle != null && isAss(subtitle)
        loadText(p, subtitle.takeUnless { ass })
        if (subtitle != null && ass) {
            val url = subtitleUrl(p, subtitle, "ass")
            builder.setSubtitleConfigurations(
                listOf(
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(url))
                        .setMimeType(MimeTypes.TEXT_SSA)
                        .setLanguage(subtitle.language)
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT or C.SELECTION_FLAG_FORCED)
                        .setId("arc-${subtitle.id}")
                        .build(),
                ),
            )
        }
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !ass)
            .apply { if (ass) setPreferredTextLanguage(subtitle!!.language) }
            .build()
        player.setMediaItem(builder.build(), PlaybackClock.playerStart(p, fileMs))
        player.prepare()
        player.playWhenReady = true
    }

    private fun isAss(t: SubtitleTrack) = t.codec == "ass" || t.codec == "ssa"

    private fun subtitleUrl(p: PlaybackPlan, t: SubtitleTrack, format: String): String {
        val shift = if (p.hls) -p.offsetMs else 0
        return api.absolute("/api/items/${enc(itemId)}/subtitles/${enc(t.id)}/$format?file=${p.fileId}&shift_ms=$shift")
    }

    /**
     * Loads a text subtitle in the background. Arc may take a while to extract it from the film;
     * meanwhile the film plays and the subtitles appear once they're here. Tries three times.
     */
    private fun loadText(p: PlaybackPlan, track: SubtitleTrack?) {
        textJob?.cancel()
        textSubtitles = null
        _cues.value = emptyList()
        if (track == null) return
        val url = subtitleUrl(p, track, "vtt")
        textJob = viewModelScope.launch {
            val started = System.currentTimeMillis()
            for (attempt in 1..3) {
                try {
                    val bytes = fetch(url)
                    val subs = withContext(Dispatchers.Default) { TextSubtitles.parse(bytes) }
                    textSubtitles = subs
                    log.info("subtitles loaded: ${subs.size} cues in ${System.currentTimeMillis() - started} ms")
                    return@launch
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.warn("loading subtitles failed (attempt $attempt): ${e.javaClass.simpleName}")
                    if (attempt < 3) delay(3_000L * attempt)
                }
            }
        }
    }

    private suspend fun fetch(url: String): ByteArray = withContext(Dispatchers.IO) {
        val call = container.mediaClient.newCall(Request.Builder().url(url).build())
        suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: java.io.IOException) { cont.resumeWith(Result.failure(e)) }
                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use {
                            if (!it.isSuccessful) throw java.io.IOException("HTTP ${it.code}")
                            it.body.bytes()
                        }
                    }
                    cont.resumeWith(result)
                }
            })
        }
    }

    /** Puts the due text subtitles on screen, ten times a second. */
    private suspend fun drawCues() {
        while (currentCoroutineContextActive()) {
            delay(100)
            val subs = textSubtitles
            val now = if (subs == null) emptyList() else subs.cuesAt(player.currentPosition.coerceAtLeast(0) * 1000)
            if (now != _cues.value) _cues.value = now
        }
    }

    /** The file as it is (or repackaged) didn't play here: ask Arc to do more, once, as the web player does. */
    private fun onStreamError(error: PlaybackException) {
        val p = plan
        if (p != null && !retried && p.method != "transcode") {
            retried = true
            log.info("${p.method} playback failed on this device (${error.errorCodeName}); asking Arc to convert more")
            currentCaps = fallbackCaps(currentCaps, p.method)
            load(position())
            return
        }
        log.warn("playback failed: ${error.errorCodeName}")
        _state.update { it.copy(error = UiError.Server(error.errorCodeName, 0), loading = false) }
    }

    fun togglePlay() {
        if (player.isPlaying) player.pause() else player.play()
    }

    /** Seeks to a point in the file, asking for a new stream when the converted one doesn't reach it. */
    fun seekTo(fileMs: Long) {
        val p = plan ?: return
        val target = fileMs.coerceIn(0, (p.durationMs - 1_000).coerceAtLeast(0))
        _state.update { it.copy(positionMs = target) }
        val available = player.duration.takeIf { it != C.TIME_UNSET } ?: 0
        if (PlaybackClock.seekInStream(p, target, available)) {
            player.seekTo(PlaybackClock.playerStart(p, target))
        } else {
            load(target)
        }
    }

    fun seekBy(deltaMs: Long) = seekTo(position() + deltaMs)

    fun chooseAudio(index: Int) {
        val p = plan ?: return
        if (index == p.audioStream) return
        audioLanguageToRemember(p.audioTracks.find { it.index == index }, prefs.audioLanguage)?.let { remember("audio_language", it) }
        load(position(), audioStream = index)
    }

    fun chooseSubtitle(track: SubtitleTrack?) {
        val p = plan ?: return
        rememberedSubtitleLanguage(track, prefs.subtitleMode)?.takeIf { it != prefs.subtitleLanguage }?.let { remember("subtitle_language", it) }
        val before = _state.value.subtitle
        _state.update { it.copy(subtitle = track) }
        // Between text subtitles (or off), only the drawn cues change: the film carries on.
        if ((before == null || !isAss(before)) && (track == null || !isAss(track))) loadText(p, track)
        else install(p, position(), track)
    }

    /** Quality as the web player offers it: -1 Automatic, 0 Original, or a limit in kbit/s. */
    fun chooseQuality(kbps: Int) {
        if (kbps == _state.value.quality) return
        _state.update { it.copy(quality = kbps) }
        val limit = kbps.takeIf { it > 0 }
        if (limit == maxKbps) return
        maxKbps = limit
        load(position())
    }

    fun chooseVersion(fileId: Long) {
        if (fileId == plan?.fileId) return
        load(position(), fileId = fileId)
    }

    fun setSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
        _state.update { it.copy(speed = speed) }
    }

    fun retry() {
        retried = false
        load(position())
    }

    private suspend fun tick() {
        var lastReport = System.currentTimeMillis()
        while (currentCoroutineContextActive()) {
            delay(500)
            val p = plan ?: continue
            val pos = position()
            val buffered = PlaybackClock.filePosition(p, player.bufferedPosition.coerceAtLeast(0))
            _state.update { it.copy(positionMs = pos, bufferedMs = buffered) }
            if (player.isPlaying && System.currentTimeMillis() - lastReport >= REPORT_EVERY_MS) {
                lastReport = System.currentTimeMillis()
                report("playing")
            }
        }
    }

    private suspend fun keepAlive() {
        while (currentCoroutineContextActive()) {
            delay(PING_EVERY_MS)
            val sid = plan?.sessionId ?: continue
            runCatching { api.pingTranscode(sid) }.onFailure { log.debug("keeping the conversion alive failed: ${it.javaClass.simpleName}") }
        }
    }

    private suspend fun currentCoroutineContextActive() = kotlin.coroutines.coroutineContext.isActive

    /** Saves progress. Arc answers 403 with needs "stopped" when an admin ends this playback. */
    private fun report(state: String, position: Long = position()) {
        val p = plan ?: return
        val duration = p.durationMs.takeIf { it > 0 } ?: return
        val report = ProgressReport(position.coerceIn(0, duration), duration, state)
        container.appScope.launch {
            try {
                api.reportProgress(itemId, report)
            } catch (e: ApiException) {
                if (e.needs == "stopped" || e.status == 403) {
                    viewModelScope.launch {
                        player.pause()
                        _state.update { it.copy(stopped = e.message) }
                    }
                } else {
                    log.warn("saving progress failed: ${e.status}")
                }
            } catch (e: Exception) {
                log.warn("saving progress failed: ${e.javaClass.simpleName}")
            }
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    override fun onCleared() {
        val p = plan
        if (p != null && !_state.value.ended) report("stopped")
        p?.sessionId?.let { sid -> container.appScope.launch { runCatching { api.stopTranscode(sid) } } }
        player.release()
        ass.release()
    }

    private companion object {
        const val REPORT_EVERY_MS = 10_000L
        const val PING_EVERY_MS = 30_000L
        fun enc(s: String): String = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    }
}
