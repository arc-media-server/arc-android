package org.arcmediaserver.android.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/**
 * This TV's play queue, kept by Arc for the TV and its profile, so it's still there after the app
 * restarts (Home's Queue row). Playing a title starts a new queue; Play Next and Add to Queue add
 * to it; the player moves it along. Calls run one at a time with the latest revision. When Arc
 * refuses an edit because the queue changed (another app signed in with this TV's token, or it
 * expired), the queue is read again and the edit tried once more on it.
 *
 * Servers without play queues (older Arc) are left alone: every call then does nothing.
 */
class PlayQueues(private val api: () -> ArcApi?, private val log: ArcLog = ArcLog.Android) {
    private val mutex = Mutex()
    private val _queue = MutableStateFlow<PlayQueue?>(null)
    val queue: StateFlow<PlayQueue?> = _queue

    @Volatile private var supported: Boolean? = null

    /** Whether this server keeps queues for the profile (asked once per profile). */
    suspend fun supported(): Boolean {
        supported?.let { return it }
        val api = api() ?: return false
        val usable = try {
            api.capabilities().usable("play_queues")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            log.debug("capabilities unavailable: ${e.javaClass.simpleName}")
            return false // asked again next time
        }
        supported = usable
        return usable
    }

    /** A different profile (or none): its queue is its own. */
    fun reset() {
        supported = null
        _queue.value = null
    }

    /** Reads the queue Arc has for this TV and profile (none: null). */
    suspend fun refresh(): PlayQueue? = locked("reading the queue") { api -> api.currentPlayQueue() }

    /** A new queue of [itemIds] (a show adds its episodes), replacing this TV's last one. */
    suspend fun start(itemIds: List<String>): PlayQueue? = locked("starting a queue") { api ->
        try {
            api.createPlayQueue(itemIds, _queue.value).also { log.debug("queue started: ${it.entries.size} entries") }
        } catch (e: ApiException) {
            // The queue this app knew changed or expired, or there's one it hasn't read yet.
            if (e.status != 409) throw e
            api.createPlayQueue(itemIds, api.currentPlayQueue())
        }
    }

    /** Play Next ([next]) or Add to Queue; with no queue yet, these titles start one. */
    suspend fun add(itemIds: List<String>, next: Boolean): PlayQueue? = locked("adding to the queue") { api ->
        val current = _queue.value ?: api.currentPlayQueue()
        if (current == null || current.currentEntryId.isEmpty()) {
            api.createPlayQueue(itemIds, current)
        } else {
            edit(api, current, if (next) "next" else "append", mapOf("item_ids" to JsonArray(itemIds.map { JsonPrimitive(it) })))
        }
    }

    /** Points the queue at [entryId]: Advance when it's simply the next one, else Select. */
    suspend fun moveTo(entryId: String): PlayQueue? = locked("moving the queue on") { api ->
        val q = _queue.value ?: api.currentPlayQueue() ?: return@locked null
        when {
            q.currentEntryId == entryId -> q
            q.entries.getOrNull(q.currentIndex + 1)?.id == entryId && q.currentIndex >= 0 -> edit(api, q, "advance")
            else -> edit(api, q, "select", mapOf("entry_id" to JsonPrimitive(entryId)))
        }
    }

    /** Adds [itemId] at the end and moves the queue to it (Auto-Play's next episode). */
    suspend fun carryOnWith(itemId: String): String? {
        val q = add(listOf(itemId), next = false) ?: return null
        val entry = q.entries.lastOrNull { it.itemId == itemId } ?: return null
        moveTo(entry.id)
        return entry.id
    }

    /** The queue played to its end: Arc forgets it. */
    suspend fun end() {
        locked("ending the queue") { api ->
            _queue.value?.let { api.deletePlayQueue(it) }
            null
        }
    }

    private suspend fun edit(api: ArcApi, q: PlayQueue, operation: String, fields: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap()): PlayQueue? = try {
        api.editPlayQueue(q, operation, fields)
    } catch (e: ApiException) {
        if (e.status != 409) throw e
        log.info("queue changed elsewhere; reloaded before $operation")
        val fresh = api.currentPlayQueue() ?: return null
        api.editPlayQueue(fresh, operation, fields)
    }

    /** Runs [block] alone, keeping its queue; null when it failed (or there's no queue). */
    private suspend fun locked(what: String, block: suspend (ArcApi) -> PlayQueue?): PlayQueue? {
        if (!supported()) return null
        val api = api() ?: return null
        return mutex.withLock {
            try {
                block(api).also { _queue.value = it }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                log.warn("$what failed: ${e.javaClass.simpleName}")
                null // the queue stays as it was
            }
        }
    }
}
