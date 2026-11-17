package org.arcmediaserver.android.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** What the player tells the room every couple of seconds. */
data class PlayerReport(
    /** Where it is in the file (never a converted stream's own clock). */
    val positionMs: Long,
    /** playing, paused, or waiting while it buffers. */
    val state: String,
    val ready: Boolean,
)

/** Why a room membership ended: the host ended the room, or the membership is gone (404). */
enum class RoomEnd { Ended, Gone }

/**
 * This TV's membership of a room, as the web's RoomSession: the room's own live stream (the full
 * room on every change), clock sync, heartbeats and commands. [room] is always the newest room
 * seen; [onEnd] is called once when the room ends or the membership is gone.
 */
class RoomSession(
    first: WatchRoom,
    private val api: ArcApi,
    client: OkHttpClient,
    private val onEnd: (RoomEnd) -> Unit,
    private val log: ArcLog = ArcLog.Android,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // Arc sends a full room every few seconds, so a silent minute means the line is dead.
    private val streamClient = client.newBuilder().readTimeout(60, TimeUnit.SECONDS).callTimeout(0, TimeUnit.MILLISECONDS).build()

    private val _room = MutableStateFlow(first)
    val room: StateFlow<WatchRoom> = _room

    /** How far Arc's clock is ahead of this TV's. */
    @Volatile private var offsetMs = 0L
    @Volatile private var reporter: (() -> PlayerReport?)? = null
    @Volatile private var stopped = false

    fun start() {
        scope.launch { stream() }
        scope.launch {
            while (isActiveNow()) {
                syncClock()
                delay(60_000)
            }
        }
        scope.launch {
            while (isActiveNow()) {
                delay(2_000)
                heartbeat()
            }
        }
    }

    /** Stops following the room (it carries on without this TV; Arc drops it after 30 s). */
    fun stop() {
        stopped = true
        scope.cancel()
    }

    /** Leaves the room, then stops. */
    suspend fun leave() {
        stop()
        runCatching { api.leaveRoom(_room.value.id) }.onFailure { log.debug("watch together: leaving failed (${it.javaClass.simpleName})") }
    }

    /** Arc's clock now, as well as this TV can tell. */
    fun serverNow(): Long = now() + offsetMs

    /** Where in the file the room is now. */
    fun expected(): Long = RoomSync.expectedPosition(_room.value, serverNow())

    fun setReporter(reporter: (() -> PlayerReport?)?) {
        this.reporter = reporter
    }

    /** Reports at once (starting or finishing buffering) rather than on the next beat. */
    fun reportNow() {
        if (!stopped) scope.launch { heartbeat() }
    }

    suspend fun play() = command("play")
    suspend fun pause() = command("pause")
    suspend fun seek(positionMs: Long) = command("seek", positionMs = positionMs.coerceAtLeast(0))
    suspend fun changeItem(itemId: String, positionMs: Long = 0) = command("change_item", positionMs = positionMs, itemId = itemId)
    suspend fun end() = command("end")

    /** A new code for the room (host only); the old one stops working. */
    suspend fun renewCode(): WatchInvite {
        val invite = try {
            api.renewRoomCode(_room.value.id, _room.value.revision)
        } catch (e: ApiException) {
            val newer = conflictRoom(e) ?: throw e
            adopt(newer)
            api.renewRoomCode(_room.value.id, _room.value.revision)
        }
        adopt(invite.room)
        return invite
    }

    /** Sends a command with the room's revision; if the room changed meanwhile, adopts it and tries once more. */
    private suspend fun command(action: String, positionMs: Long? = null, itemId: String? = null) {
        val id = _room.value.id
        try {
            adopt(api.roomCommand(id, _room.value.revision, action, positionMs, itemId))
        } catch (e: ApiException) {
            val newer = conflictRoom(e)
            when {
                newer != null -> {
                    adopt(newer)
                    adopt(api.roomCommand(id, _room.value.revision, action, positionMs, itemId))
                }
                e.status == 404 -> gone()
                else -> throw e
            }
        }
    }

    private suspend fun heartbeat() {
        if (stopped) return
        val r = reporter?.invoke() ?: return
        val room = _room.value
        try {
            adopt(api.roomHeartbeat(room.id, room.generation, r.positionMs, r.state, r.ready && r.state != "waiting"))
        } catch (e: ApiException) {
            // A seek or a new title: report against it next time.
            conflictRoom(e)?.let { adopt(it); return }
            if (e.status == 404) gone()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            // Offline for a moment: the next beat tries again.
        }
    }

    private suspend fun syncClock() {
        val samples = mutableListOf<ClockSample>()
        repeat(3) {
            val c0 = now()
            val t = runCatching { api.roomTime(c0) }.getOrNull() ?: return@repeat
            samples += ClockSample(c0, now(), t.serverReceivedMs, t.serverSentMs)
        }
        RoomSync.clockOffset(samples)?.let { offsetMs = it }
    }

    /** Takes a room from Arc if it's at least as new as the one held. */
    fun adopt(room: WatchRoom) {
        if (stopped) return
        val held = _room.value
        if (room.id == held.id && room.revision < held.revision) return
        _room.value = room
        if (room.state == "ended") end(RoomEnd.Ended)
    }

    private fun gone() = end(RoomEnd.Gone)

    private fun end(why: RoomEnd) {
        if (stopped) return
        stop()
        log.info("watch together: ${if (why == RoomEnd.Ended) "the room ended" else "no longer in the room"}")
        onEnd(why)
    }

    /** The room's own stream: a full room on every change; reopened when it ends, until the room's gone. */
    private suspend fun stream() {
        var backoff = 2_000L
        while (isActiveNow()) {
            try {
                readStream { backoff = 2_000L }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                log.debug("watch together: the room stream dropped (${e.javaClass.simpleName})")
            }
            if (!isActiveNow()) return
            // Still ours? (404: the room ended, or this TV was dropped from it.)
            try {
                adopt(api.room(_room.value.id))
            } catch (e: ApiException) {
                if (e.status == 404) { gone(); return }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            }
            delay(backoff)
            backoff = minOf(backoff * 2, 30_000L)
        }
    }

    private suspend fun readStream(connected: () -> Unit) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(api.absolute("/api/v1/watch-together/${java.net.URLEncoder.encode(_room.value.id, "UTF-8")}/events"))
            .header("Accept", "text/event-stream")
            .build()
        val call = streamClient.newCall(request)
        currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
        call.execute().use { response ->
            if (response.code == 404) { gone(); return@withContext }
            if (!response.isSuccessful) return@withContext
            connected()
            val parser = SseParser()
            val source = response.body.source()
            while (currentCoroutineContext().isActive) {
                val line = source.readUtf8Line() ?: break
                val event = parser.line(line) ?: continue
                when (event.event) {
                    "room" -> runCatching { ArcJson.decodeFromString(WatchRoom.serializer(), event.data) }.getOrNull()?.let { adopt(it) }
                    "ended" -> end(RoomEnd.Ended)
                }
            }
        }
    }

    private suspend fun isActiveNow() = !stopped && currentCoroutineContext().isActive

    companion object {
        /** The room in a 409 refusal ("room changed"), to adopt. */
        fun conflictRoom(e: ApiException): WatchRoom? {
            if (e.status != 409) return null
            val body = e.body ?: return null
            return runCatching {
                val obj = ArcJson.parseToJsonElement(body) as? kotlinx.serialization.json.JsonObject
                obj?.get("room")?.let { ArcJson.decodeFromJsonElement(WatchRoom.serializer(), it) }
            }.getOrNull()
        }
    }
}
