package org.arcmediaserver.android.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Watch Together (arc-media-server docs/WATCH-TOGETHER-API.md; the web's lib/watch-together.ts):
 * a room shares one timeline (title, position, playing or paused); everyone streams their own copy
 * with their own audio, subtitles and quality. This file is the plain part: the room's shape and
 * the clock and drift maths.
 */

@Serializable
data class WatchParticipant(
    val id: String,
    val name: String = "",
    val available: Boolean = true,
    val ready: Boolean = false,
    /** playing, paused or waiting (buffering). */
    val state: String = "",
)

@Serializable
data class RoomItem(val id: String, val title: String = "", val kind: String = "")

@Serializable
data class WatchRoom(
    val id: String,
    val revision: Long = 0,
    /** Bumps on every seek or title change: heartbeats name the one they've loaded. */
    val generation: Long = 0,
    /** paused, playing, waiting (for someone buffering) or ended. */
    val state: String = "paused",
    /** In the file, at server time [atMs]. */
    @SerialName("position_ms") val positionMs: Long = 0,
    @SerialName("at_ms") val atMs: Long = 0,
    val rate: Double = 1.0,
    @SerialName("server_time_ms") val serverTimeMs: Long = 0,
    @SerialName("wait_until_ms") val waitUntilMs: Long? = null,
    @SerialName("host_id") val hostId: String = "",
    @SerialName("host_name") val hostName: String? = null,
    @SerialName("self_id") val selfId: String = "",
    @SerialName("changed_by") val changedBy: String? = null,
    /** False when this profile may not play the room's current title (then no item is sent). */
    val available: Boolean = true,
    @SerialName("item_id") val itemId: String? = null,
    val item: RoomItem? = null,
    val participants: List<WatchParticipant> = emptyList(),
    /** People invited by name who aren't in the room now. */
    val invited: List<InvitedName> = emptyList(),
) {
    val hosting: Boolean get() = hostId.isNotEmpty() && hostId == selfId
    val hostDisplayName: String get() = participants.firstOrNull { it.id == hostId }?.name ?: hostName.orEmpty()
    fun nameOf(id: String?): String? = participants.firstOrNull { it.id == id }?.name
}

@Serializable
data class InvitedName(val name: String = "")

@Serializable
data class WatchInvite(
    val room: WatchRoom,
    @SerialName("invite_code") val inviteCode: String,
    @SerialName("invite_expires_at_ms") val inviteExpiresAtMs: Long = 0,
)

/** A room in this profile's list (Home's Watch Together row). */
@Serializable
data class ListedRoom(
    val id: String,
    val state: String = "",
    @SerialName("position_ms") val positionMs: Long = 0,
    @SerialName("created_at_ms") val createdAtMs: Long = 0,
    @SerialName("host_name") val hostName: String = "",
    /** This profile made it. */
    val hosting: Boolean = false,
    /** This TV and profile are in it. */
    val joined: Boolean = false,
    val item: RoomItem,
    val participants: List<String> = emptyList(),
    val invited: List<String> = emptyList(),
)

@Serializable
data class ListedRooms(val rooms: List<ListedRoom> = emptyList())

@Serializable
data class RoomTime(
    @SerialName("client_sent_ms") val clientSentMs: Long = 0,
    @SerialName("server_received_ms") val serverReceivedMs: Long = 0,
    @SerialName("server_sent_ms") val serverSentMs: Long = 0,
)

/** Room codes as people type and read them. */
object WatchCode {
    /** "abcd 1234" or "ABCD-1234" as Arc's code. */
    fun normalize(input: String): String = input.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }

    /** An eight-character code as it's read aloud: XXXX-XXXX. */
    fun format(code: String): String = normalize(code).let { if (it.length == 8) it.take(4) + "-" + it.drop(4) else it }
}

/** One clock sample: this TV's send and receive times, and the server's receive and send (epoch ms). */
data class ClockSample(val c0: Long, val c3: Long, val s1: Long, val s2: Long)

object RoomSync {
    /**
     * How far the server's clock is ahead of this one, from the sample with the shortest round trip
     * (NTP's midpoint estimate); null with no samples.
     */
    fun clockOffset(samples: List<ClockSample>): Long? =
        samples.minByOrNull { (it.c3 - it.c0) - (it.s2 - it.s1) }?.let { ((it.s1 - it.c0) + (it.s2 - it.c3)) / 2 }

    /** Where the room should be in the file at server time [serverNow]. */
    fun expectedPosition(room: WatchRoom, serverNow: Long): Long =
        if (room.state != "playing") room.positionMs
        else room.positionMs + (maxOf(0L, serverNow - room.atMs) * (if (room.rate > 0) room.rate else 1.0)).toLong()

    /**
     * How to bring this player back in step (the handoff's thresholds): under half a second, leave
     * it; up to three seconds, play slightly slower or faster until caught up; beyond, jump.
     */
    sealed interface Correction {
        data object None : Correction
        data class Rate(val rate: Float) : Correction
        data class Seek(val toMs: Long) : Correction
    }

    fun correction(localMs: Long, expectedMs: Long): Correction {
        val drift = localMs - expectedMs
        val size = kotlin.math.abs(drift)
        return when {
            size < 500 -> Correction.None
            size <= 3_000 -> Correction.Rate(if (drift > 0) 0.97f else 1.03f)
            else -> Correction.Seek(expectedMs)
        }
    }

    /** "Waiting for Sam, Jo": the people still loading (not this TV, and not those who can't watch). */
    fun waitingFor(room: WatchRoom): List<String> =
        room.participants.filter { it.available && !it.ready && it.id != room.selfId }.map { it.name }

    /**
     * What someone else just did to the shared timeline, for a short note: moved it (a seek or new
     * title), paused or played; null for this TV's own changes, heartbeats and no change.
     */
    enum class Change { Moved, Paused, Played }

    fun changeBy(before: WatchRoom?, after: WatchRoom): Pair<Change, String>? {
        if (before == null || after.revision <= before.revision) return null
        val by = after.changedBy?.takeIf { it != after.selfId } ?: return null
        val who = after.nameOf(by) ?: return null
        return when {
            after.generation != before.generation -> Change.Moved to who
            after.state == "paused" && before.state != "paused" -> Change.Paused to who
            after.state == "playing" && before.state == "paused" -> Change.Played to who
            else -> null
        }
    }

    /** The lobby shows until the room first plays, or when it was already under way on joining. */
    fun startedOnce(room: WatchRoom): Boolean = room.state != "paused" || room.positionMs > 0
}
