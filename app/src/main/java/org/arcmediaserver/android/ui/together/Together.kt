package org.arcmediaserver.android.ui.together

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.arcmediaserver.android.AppContainer
import org.arcmediaserver.android.data.ApiException
import org.arcmediaserver.android.data.ArcLog
import org.arcmediaserver.android.data.Item
import org.arcmediaserver.android.data.ListedRoom
import org.arcmediaserver.android.data.RoomEnd
import org.arcmediaserver.android.data.RoomSession
import org.arcmediaserver.android.data.WatchRoom

/** The code to share for the room this TV is hosting, and when it runs out (epoch ms). */
data class RoomCode(val roomId: String, val code: String, val expiresAtMs: Long)

/** A room on Home's Watch Together row and page, with its title's details once they're loaded. */
data class RoomCard(val room: ListedRoom, val item: Item?)

/** Why joining didn't work, in the web's words (player.json together.*). */
enum class JoinProblem { CodeNotFound, TooManyTries, Failed }

/**
 * Watch Together for the whole app, as the web's WatchTogetherProvider: whether this server and
 * profile may use it, the rooms on Home, and this TV's one room membership ([session]). Joining or
 * starting a room sets [session]; ArcNavHost then opens the player on the room's title, where the
 * room is (and moves it on when the room changes title).
 */
class Together(private val container: AppContainer) {
    private val log = ArcLog.Android
    private val lock = Mutex()

    private val _available = MutableStateFlow(false)
    /** This server has Watch Together, and this profile may use it. */
    val available: StateFlow<Boolean> = _available
    private var checked = false

    private val _session = MutableStateFlow<RoomSession?>(null)
    val session: StateFlow<RoomSession?> = _session

    private val _code = MutableStateFlow<RoomCode?>(null)
    /** The code to share, for a room this TV made with a code. */
    val code: StateFlow<RoomCode?> = _code

    private val _ended = MutableStateFlow<RoomEnd?>(null)
    /** How the last room ended (for the player's note), until the next one starts. */
    val ended: StateFlow<RoomEnd?> = _ended

    private val _rooms = MutableStateFlow<List<RoomCard>>(emptyList())
    /** Rooms this profile made, was invited to or is in, newest first. */
    val rooms: StateFlow<List<RoomCard>> = _rooms
    private val items = java.util.concurrent.ConcurrentHashMap<String, Item>()

    /** Asks Arc once per profile whether Watch Together is there to use. */
    suspend fun check(): Boolean {
        if (checked) return _available.value
        val api = container.api() ?: return false
        val usable = try {
            api.capabilities().usable("watch_together")
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            log.debug("watch together: capabilities unavailable (${e.javaClass.simpleName})")
            return false
        }
        checked = true
        _available.value = usable
        return usable
    }

    /** Reads the rooms list again (Home, the Watch Together page, and when Arc says it changed). */
    suspend fun refreshRooms() {
        if (!check()) return
        val api = container.api() ?: return
        val listed = try {
            api.listedRooms()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            log.debug("watch together: the rooms list didn't load (${e.javaClass.simpleName})")
            return
        }
        _rooms.value = listed.map { room ->
            RoomCard(room, items[room.item.id] ?: runCatching { api.item(room.item.id) }.getOrNull()?.also { items[room.item.id] = it })
        }
    }

    /** A room with a code to share for [item], from [startMs]; this TV joins it as host. */
    suspend fun start(item: Item, startMs: Long = 0): Boolean = lock.withLock {
        val api = container.api() ?: return false
        return try {
            val invite = api.createRoom(item.id, startMs)
            _code.value = RoomCode(invite.room.id, invite.inviteCode, invite.inviteExpiresAtMs)
            log.info("watch together: started a room")
            begin(invite.room)
            true
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            log.warn("watch together: starting a room failed (${(e as? ApiException)?.status ?: e.javaClass.simpleName})")
            false
        }
    }

    /** Joins with a code the host shared; null when it worked. */
    suspend fun join(code: String): JoinProblem? = lock.withLock {
        val api = container.api() ?: return JoinProblem.Failed
        return try {
            val room = api.joinRoom(code)
            _code.value = null
            log.info("watch together: joined with a code")
            begin(room)
            null
        } catch (e: ApiException) {
            log.info("watch together: joining with a code refused (${e.status})")
            when (e.status) {
                404 -> JoinProblem.CodeNotFound
                429 -> JoinProblem.TooManyTries
                else -> JoinProblem.Failed
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            log.warn("watch together: joining failed (${e.javaClass.simpleName})")
            JoinProblem.Failed
        }
    }

    /** Join (or Return to) a room from the list; false if Arc refused. */
    suspend fun accept(roomId: String): Boolean = lock.withLock {
        val api = container.api() ?: return false
        return try {
            val room = api.acceptRoom(roomId)
            if (_code.value?.roomId != roomId) _code.value = null
            log.info("watch together: joined a room from the list")
            begin(room)
            container.appScope.launch { refreshRooms() }
            true
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            log.warn("watch together: joining a room failed (${(e as? ApiException)?.status ?: e.javaClass.simpleName})")
            container.appScope.launch { refreshRooms() }
            false
        }
    }

    /** Decline an invitation, or Cancel a room this profile made. */
    suspend fun dismiss(roomId: String) {
        val api = container.api() ?: return
        runCatching { api.dismissRoom(roomId) }.onFailure { log.warn("watch together: declining failed (${it.javaClass.simpleName})") }
        _rooms.value = _rooms.value.filterNot { it.room.id == roomId }
        refreshRooms()
    }

    /** Leaves the room this TV is in (the others carry on). */
    suspend fun leave() {
        val s = _session.value ?: return
        _session.value = null
        _code.value = null
        s.leave()
        log.info("watch together: left the room")
        container.appScope.launch { refreshRooms() }
    }

    /** Ends the room for everyone (host only). */
    suspend fun end() {
        runCatching { _session.value?.end() }.onFailure { log.warn("watch together: ending the room failed (${it.javaClass.simpleName})") }
    }

    /** A new code for the room this TV hosts. */
    suspend fun renewCode() {
        val s = _session.value ?: return
        runCatching { s.renewCode() }
            .onSuccess { _code.value = RoomCode(it.room.id, it.inviteCode, it.inviteExpiresAtMs) }
            .onFailure { log.warn("watch together: a new code failed (${it.javaClass.simpleName})") }
    }

    /** A different profile (or none): this TV's membership doesn't carry over (Arc drops it after 30 s). */
    fun reset() {
        _session.value?.stop()
        _session.value = null
        _code.value = null
        _ended.value = null
        _rooms.value = emptyList()
        items.clear()
        checked = false
        _available.value = false
    }

    private fun begin(room: WatchRoom) {
        _ended.value = null
        _session.value?.stop()
        val api = container.api() ?: return
        lateinit var s: RoomSession
        s = RoomSession(room, api, container.mediaClient, onEnd = { why ->
            if (_session.value === s) {
                _session.value = null
                _code.value = null
                _ended.value = why
                container.appScope.launch { refreshRooms() }
            }
        })
        _session.value = s
        s.start()
    }
}
