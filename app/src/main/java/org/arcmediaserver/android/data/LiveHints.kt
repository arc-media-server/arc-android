package org.arcmediaserver.android.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Live updates (arc-media-server docs/EVENTS-API.md): Arc sends "something changed" hints on
 * /api/v1/events, naming a topic only (never the record), and the app reloads what's on screen.
 * This file holds the plain parts: reading the stream's text, collecting hints, and which topics
 * touch which screen.
 */
object LiveTopics {
    /** Not a topic Arc sends as such: "anything may have changed" (a resync, or a gap in the stream). */
    const val RESYNC = "resync"

    /** Home's rows, the sidebar's libraries and the Watchlist / Playlists pages. */
    val home = setOf("catalog", "library", "profile", "playback", "artwork")
    /** A title's page: its details, seasons and watched state. */
    val title = setOf("catalog", "profile", "playback", "artwork")
    /** The profile's preferences (Hide Spoilers…). */
    val prefs = setOf("profile")
    /** Home's Watch Together rooms: a personal hint when one is made, joined, declined or ended. */
    val together = setOf("watch_together")

    /** Whether hints for [topics] mean a screen that follows [wanted] should reload. */
    fun touches(topics: Set<String>, wanted: Set<String>): Boolean = RESYNC in topics || topics.any { it in wanted }
}

/** One server-sent event: its type (`invalidate`, `resync`…), its data, and its id when it had one. */
data class SseEvent(val event: String, val data: String, val id: String?)

/**
 * Reads a text/event-stream a line at a time (the WHATWG rules apps need): `event:`, `data:` (lines
 * joined with newlines), `id:`; a line starting with ":" is a comment (Arc's heartbeat); a blank
 * line ends the event. Fields are only sent when there's something to send.
 */
class SseParser {
    private var event = ""
    private val data = StringBuilder()
    private var id: String? = null
    private var hasData = false

    /** Feeds one line (without its line break); returns the event it completes, if any. */
    fun line(line: String): SseEvent? {
        if (line.isEmpty()) {
            val done = if (hasData || event.isNotEmpty()) SseEvent(event.ifEmpty { "message" }, data.toString(), id) else null
            event = ""
            data.clear()
            hasData = false
            // The id carries on to later events until a new one comes (the stream's cursor).
            return done
        }
        if (line.startsWith(":")) return null
        val colon = line.indexOf(':')
        val field = if (colon < 0) line else line.substring(0, colon)
        var value = if (colon < 0) "" else line.substring(colon + 1)
        if (value.startsWith(" ")) value = value.substring(1)
        when (field) {
            "event" -> event = value
            "data" -> {
                if (hasData) data.append('\n')
                data.append(value)
                hasData = true
            }
            "id" -> if (!value.contains('\u0000')) id = value
        }
        return null
    }
}

/** The topic of an `invalidate` hint ({"topic":"catalog", …}), or null if it can't be read. */
fun hintTopic(data: String): String? = runCatching {
    (ArcJson.parseToJsonElement(data) as? JsonObject)?.get("topic")?.let { it as? JsonPrimitive }?.takeIf { it.isString }?.content
}.getOrNull()

/**
 * Collects hints and hands them on at most once every [spacingMs], the first [firstMs] after a quiet
 * spell, as the web's HintBatcher: a library scan sends hints twice a second for minutes, and
 * reloading Home for each would never settle, while waiting for them to stop would show nothing
 * until the scan ends.
 */
class HintBatcher(
    private val scope: CoroutineScope,
    private val firstMs: Long = 1_500,
    private val spacingMs: Long = 5_000,
    private val now: () -> Long = System::currentTimeMillis,
    private val flush: (Set<String>) -> Unit,
) {
    private val pending = LinkedHashSet<String>()
    private var timer: Job? = null
    private var last = Long.MIN_VALUE / 2

    @Synchronized
    fun add(topic: String) {
        pending += topic
        if (timer != null) return
        val wait = maxOf(firstMs, last + spacingMs - now())
        timer = scope.launch {
            delay(wait)
            run()
        }
    }

    /** Hands on what's waiting now (after a reconnect, everything is stale at once). */
    @Synchronized
    fun run() {
        // Called by the timer itself too: cancelling it then is harmless (nothing after this suspends).
        timer?.cancel()
        timer = null
        if (pending.isEmpty()) return
        val topics = pending.toSet()
        pending.clear()
        last = now()
        flush(topics)
    }

    @Synchronized
    fun stop() {
        timer?.cancel()
        timer = null
        pending.clear()
    }
}
