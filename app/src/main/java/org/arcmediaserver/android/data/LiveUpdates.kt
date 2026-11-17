package org.arcmediaserver.android.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Keeps one /api/v1/events stream open while the app is on screen ([start] / [stop]) and hands
 * on what changed through [changes], gathered by [HintBatcher]. Screens reload what they show when
 * a topic touches them (LiveTopics); [LiveTopics.RESYNC] means everything.
 *
 * As the web's live-updates.ts:
 * - A new stream (no cursor) starts with a resync: the screens have just loaded, so it's skipped.
 * - A resync on a resumed stream is real (Arc's replay window didn't reach back far enough).
 * - After a gap with no stream (a refusal or a lost connection), everything refreshes once it's back.
 * - The stream resumes with Last-Event-ID; Arc closes streams after 30 minutes and the app reopens.
 * - A refusal (429 when Arc's 32 streams are in use) is honoured with its Retry-After, else 5 s
 *   doubling to a minute.
 */
class LiveUpdates(
    private val api: () -> ArcApi?,
    client: OkHttpClient,
    private val scope: CoroutineScope,
    private val log: ArcLog = ArcLog.Android,
) {
    // Arc sends a heartbeat every 10 s, so a read that waits much longer means the line is dead.
    private val streamClient = client.newBuilder().readTimeout(45, TimeUnit.SECONDS).callTimeout(0, TimeUnit.MILLISECONDS).build()

    private val _changes = MutableSharedFlow<Set<String>>(extraBufferCapacity = 16)
    /** Topics that changed (gathered); a set holding [LiveTopics.RESYNC] means anything may have. */
    val changes: SharedFlow<Set<String>> = _changes

    private val batcher = HintBatcher(scope) { topics -> _changes.tryEmit(topics) }
    private var job: Job? = null
    /** The last event's id, to resume from; forgotten with the profile. */
    @Volatile private var cursor: String? = null
    /** The app is on screen: the stream should be open. */
    private var wanted = false

    /** The app came to the front. */
    @Synchronized
    fun start() {
        wanted = true
        if (job?.isActive == true) return
        job = scope.launch { run() }
    }

    /** The app went to the background (or signed out): no stream. */
    @Synchronized
    fun stop() {
        wanted = false
        close()
    }

    private fun close() {
        job?.cancel()
        job = null
        batcher.stop()
    }

    /** A different profile (or none): its stream starts afresh, if the app is on screen. */
    @Synchronized
    fun reset() {
        close()
        cursor = null
        if (wanted) job = scope.launch { run() }
    }

    private suspend fun run() {
        var backoff = 5_000L
        var missed = false
        var failures = 0
        while (currentCoroutineContext().isActive) {
            val api = api() ?: return
            val wait = try {
                val outcome = stream(api) {
                    // Connected: anything could have changed while there was no stream.
                    backoff = 5_000L
                    if (failures > 0) log.debug("live updates: connected again")
                    failures = 0
                    if (missed) {
                        missed = false
                        batcher.add(LiveTopics.RESYNC)
                        batcher.run()
                    }
                }
                when {
                    outcome.ended -> 1_000L // Arc closed it (after 30 minutes): reopen, resuming.
                    else -> {
                        failures++
                        missed = true
                        // Logged once in a row: Arc refusing is ordinary when its streams are full.
                        if (failures == 1) log.warn("live updates: Arc answered ${outcome.status}; trying again later")
                        outcome.retryAfterMs ?: backoff.also { backoff = minOf(backoff * 2, 60_000L) }
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                failures++
                missed = true
                if (failures == 1) log.warn("live updates: the stream dropped (${e.javaClass.simpleName}); trying again later")
                else log.debug("live updates: still can't connect (${e.javaClass.simpleName})")
                backoff.also { backoff = minOf(backoff * 2, 60_000L) }
            }
            if (wait > 0) delay(wait)
        }
    }

    private data class Outcome(val ended: Boolean, val status: Int = 200, val retryAfterMs: Long? = null)

    /** Reads one stream until it ends; [connected] once Arc accepts it. */
    private suspend fun stream(api: ArcApi, connected: () -> Unit): Outcome = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(api.absolute("/api/v1/events"))
            .header("Accept", "text/event-stream")
            .apply { cursor?.let { header("Last-Event-ID", it) } }
            .build()
        val call = streamClient.newCall(request)
        currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
        call.execute().use { response ->
            if (!response.isSuccessful) {
                val retry = response.header("Retry-After")?.toLongOrNull()?.takeIf { it in 1..600 }?.times(1000)
                return@withContext Outcome(ended = false, status = response.code, retryAfterMs = retry)
            }
            connected()
            // No cursor: Arc starts the stream with a resync, which the screens just loaded don't need.
            var fresh = cursor == null
            val parser = SseParser()
            val source = response.body.source()
            while (currentCoroutineContext().isActive) {
                val line = source.readUtf8Line() ?: break
                val event = parser.line(line) ?: continue
                event.id?.let { cursor = it }
                when (event.event) {
                    "invalidate" -> {
                        fresh = false
                        hintTopic(event.data)?.let { batcher.add(it) }
                    }
                    "resync" -> if (fresh) fresh = false else batcher.add(LiveTopics.RESYNC)
                }
            }
            Outcome(ended = true)
        }
    }
}
