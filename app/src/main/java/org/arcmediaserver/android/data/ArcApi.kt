package org.arcmediaserver.android.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A failure Arc answered with: its status, its plain-words message and what it needs next, if anything. */
/** [body] is the server's answer as it came (Watch Together's 409 carries the room as it is now). */
class ApiException(val status: Int, message: String, val needs: String? = null, val body: String? = null) : IOException(message)

/** The answer to asking whether a TV sign-in code has been approved. */
sealed interface LinkPoll {
    data object Waiting : LinkPoll
    data object Expired : LinkPoll
    data class Approved(val token: String) : LinkPoll
}

val ArcJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
    encodeDefaults = true
}

/**
 * Arc's stable API (/api/v1), signed in with a device token. Every call is made on OkHttp's
 * threads and can be cancelled with its coroutine.
 */
class ArcApi(
    val baseUrl: HttpUrl,
    private val client: OkHttpClient,
    private val token: () -> String?,
    private val log: ArcLog = ArcLog.Android,
) {
    suspend fun health(): Health = get("health", Health.serializer())

    suspend fun startLink(device: String): LinkStart =
        send("POST", "auth/link", JsonObject(mapOf("device" to JsonPrimitive(device))).toString(), LinkStart.serializer())

    suspend fun pollLink(poll: String): LinkPoll {
        val (status, body) = raw("GET", "auth/link/$poll", null, route = "auth/link/{poll}")
        return when (status) {
            202 -> LinkPoll.Waiting
            200 -> LinkPoll.Approved(decode(body, TokenAnswer.serializer(), "auth/link/{poll}").token)
            404 -> LinkPoll.Expired
            else -> throw failure(status, body, "auth/link/{poll}")
        }
    }

    suspend fun authStatus(): AuthStatus = get("auth/status", AuthStatus.serializer())

    suspend fun chooseProfile(id: String, pin: String?): Profile {
        val body = buildMap {
            put("id", JsonPrimitive(id))
            if (!pin.isNullOrEmpty()) put("pin", JsonPrimitive(pin))
        }
        return send("POST", "auth/profile", JsonObject(body).toString(), Profile.serializer())
    }

    suspend fun leaveProfile() {
        val (status, body) = raw("DELETE", "auth/profile", null)
        if (status !in 200..299) throw failure(status, body, "auth/profile")
    }

    suspend fun signOut() {
        raw("POST", "auth/logout", "{}")
    }

    /** This server's name and identity; needs a chosen profile. */
    suspend fun identity(): Identity = get("identity", Identity.serializer())

    suspend fun capabilities(): Capabilities = get("capabilities", Capabilities.serializer())

    /** Starts this TV's queue with [itemIds] (a show adds its episodes), replacing [replace]. */
    suspend fun createPlayQueue(itemIds: List<String>, replace: PlayQueue? = null, playlistId: String? = null): PlayQueue {
        val body = buildMap {
            if (playlistId != null) put("playlist_id", JsonPrimitive(playlistId))
            else put("item_ids", kotlinx.serialization.json.JsonArray(itemIds.map { JsonPrimitive(it) }))
            if (replace != null) {
                put("replace_id", JsonPrimitive(replace.id))
                put("revision", JsonPrimitive(replace.revision))
            }
        }
        return send("POST", "play-queues", JsonObject(body).toString(), PlayQueue.serializer())
    }

    /** This TV and profile's queue, or null when there's none (or it expired). */
    suspend fun currentPlayQueue(): PlayQueue? {
        val route = "play-queues/current"
        val (status, body) = raw("GET", route, null)
        return when (status) {
            404 -> null
            in 200..299 -> decode(body, PlayQueue.serializer(), route)
            else -> throw failure(status, body, route)
        }
    }

    /**
     * One edit to the queue: [operation] (append, next, remove, clear, advance, select...) with its
     * [fields]. Arc refuses it (409) when [queue] isn't its latest revision.
     */
    suspend fun editPlayQueue(queue: PlayQueue, operation: String, fields: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap()): PlayQueue {
        val body = JsonObject(mapOf("revision" to JsonPrimitive(queue.revision), "operation" to JsonPrimitive(operation)) + fields)
        return send("PATCH", "play-queues/${enc(queue.id)}", body.toString(), PlayQueue.serializer(), route = "play-queues/{id}")
    }

    suspend fun deletePlayQueue(queue: PlayQueue) {
        val (status, body) = raw("DELETE", "play-queues/${enc(queue.id)}?revision=${queue.revision}", null, route = "play-queues/{id}")
        if (status !in 200..299 && status != 404) throw failure(status, body, "play-queues/{id}")
    }

    suspend fun libraries(): List<Library> = get("libraries", kotlinx.serialization.builtins.ListSerializer(Library.serializer()))

    suspend fun prefs(): Prefs = get("me/prefs", Prefs.serializer())

    /** Changes only the preferences in [changes] (e.g. audio_language), answering with them all. */
    suspend fun updatePrefs(changes: Map<String, String>): Prefs =
        send("PATCH", "me/prefs", JsonObject(changes.mapValues { JsonPrimitive(it.value) }).toString(), Prefs.serializer())

    /** Changes only the preferences in [changes], which may be any JSON (auto_play, subtitle_style). */
    suspend fun updatePrefs(changes: JsonObject): Prefs = send("PATCH", "me/prefs", changes.toString(), Prefs.serializer())

    suspend fun search(query: String): SearchResults = get("search?q=${enc(query)}", SearchResults.serializer(), route = "search")

    suspend fun person(id: Long): PersonPage = get("people/$id", PersonPage.serializer(), route = "people/{id}")

    /**
     * Marks a movie or episode (or all of a show's episodes) watched or not, for the profile. With
     * [seasonNumber], a show's one season in its display order (`season_number`).
     */
    suspend fun setPlayed(itemId: String, played: Boolean, seasonNumber: Int? = null) {
        val body = buildMap {
            put("played", JsonPrimitive(played))
            if (seasonNumber != null) put("season_number", JsonPrimitive(seasonNumber))
        }
        put("items/${enc(itemId)}/played", JsonObject(body).toString(), "items/{id}/played")
    }

    /** Takes a title out of Continue Watching (and Up Next) without marking it watched. */
    suspend fun removeFromContinue(itemId: String) {
        val (status, body) = raw("DELETE", "home/continue/${enc(itemId)}", null, route = "home/continue/{id}")
        if (status !in 200..299) throw failure(status, body, "home/continue/{id}")
    }

    /** The profile's own rating of a movie, show or episode: 1–10 (half stars out of five), 0 clears it. */
    suspend fun setRating(itemId: String, rating: Int) =
        put("items/${enc(itemId)}/rating", JsonObject(mapOf("rating" to JsonPrimitive(rating.coerceIn(0, 10)))).toString(), "items/{id}/rating")

    /** Puts a movie or show on the profile's watchlist, or takes it off. */
    suspend fun setWatchlist(itemId: String, on: Boolean) =
        put("items/${enc(itemId)}/watchlist", JsonObject(mapOf("on" to JsonPrimitive(on))).toString(), "items/{id}/watchlist")

    /** A file's timeline thumbnails, or null when it has none (yet): Arc answers 404 then. */
    suspend fun trickplay(itemId: String, fileId: Long): Trickplay? {
        val route = "items/{id}/trickplay"
        val (status, body) = raw("GET", "items/${enc(itemId)}/trickplay?file=$fileId", null, route = route)
        return when (status) {
            404 -> null
            in 200..299 -> decode(body, Trickplay.serializer(), route)
            else -> throw failure(status, body, route)
        }
    }

    suspend fun continueWatching(withUpNext: Boolean): List<Item> =
        get(if (withUpNext) "home/continue?with_up_next=1" else "home/continue", ItemList, route = "home/continue")

    suspend fun nextUp(): List<Item> = get("home/next-up", ItemList)

    suspend fun watchlist(): List<Item> = get("watchlist", ItemList)

    /** Browse a library: [params] are /items' query parameters (library_id, kind, sort, limit...). */
    suspend fun items(params: Map<String, String>): ItemPage {
        val query = params.entries.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }
        return get("items?$query", ItemPage.serializer(), route = "items")
    }

    /** The values a library's filters can take (genres, decades, qualities). */
    suspend fun facets(libraryId: Long): Facets = get("libraries/$libraryId/facets", Facets.serializer(), route = "libraries/{id}/facets")

    /** A library's Recommended view for the profile. */
    suspend fun recommended(libraryId: Long): List<RecommendedRow> =
        get("libraries/$libraryId/recommended", RecommendedPage.serializer(), route = "libraries/{id}/recommended").rows

    /** A home videos library folder by folder: [path] "" is the top. */
    suspend fun libraryFolders(libraryId: Long, path: String): FolderPage =
        get("libraries/$libraryId/folders" + (if (path.isEmpty()) "" else "?path=${enc(path)}"), FolderPage.serializer(), route = "libraries/{id}/folders")

    suspend fun libraryCollections(libraryId: Long): List<CollectionRef> =
        get("libraries/$libraryId/collections", kotlinx.serialization.builtins.ListSerializer(CollectionRef.serializer()), route = "libraries/{id}/collections")

    suspend fun recentShows(libraryId: Long): List<RecentCard> =
        get("libraries/$libraryId/recent-shows", kotlinx.serialization.builtins.ListSerializer(RecentCard.serializer()), route = "libraries/{id}/recent-shows")

    suspend fun item(id: String): Item = get("items/${enc(id)}", Item.serializer(), route = "items/{id}")

    /**
     * A title's artwork palette, or null when this server has none to give (an older Arc answers
     * 404; a title without usable artwork answers available = false).
     */
    suspend fun artworkPalette(id: String): ArtworkPalette? {
        val route = "items/{id}/artwork/palette"
        val (status, body) = raw("GET", "items/${enc(id)}/artwork/palette", null, route = route)
        return when (status) {
            404 -> null
            in 200..299 -> decode(body, ArtworkPalette.serializer(), route).takeIf { it.available && it.colors != null }
            else -> throw failure(status, body, route)
        }
    }

    /** Titles like this one in the same library (More Like This). */
    suspend fun similar(id: String): List<Item> = get("items/${enc(id)}/similar", ItemList, route = "items/{id}/similar")

    /** The profile's playlists (made on the web). */
    suspend fun playlists(): List<PlaylistInfo> = get("playlists", kotlinx.serialization.builtins.ListSerializer(PlaylistInfo.serializer()))

    /** Adds titles (a show adds its episodes) to the end of an ordinary playlist; how many entries were added. */
    suspend fun addToPlaylist(playlistId: String, itemIds: List<String>): Int {
        val body = JsonObject(mapOf("item_ids" to kotlinx.serialization.json.JsonArray(itemIds.map { JsonPrimitive(it) })))
        return send("POST", "playlists/${enc(playlistId)}/items", body.toString(), PlaylistAdded.serializer(), route = "playlists/{id}/items").added
    }

    suspend fun playlist(id: String): PlaylistPage = get("playlists/${enc(id)}", PlaylistPage.serializer(), route = "playlists/{id}")

    suspend fun collection(id: String): CollectionPage = get("collections/${enc(id)}", CollectionPage.serializer(), route = "collections/{id}")

    /** A show's seasons in its display order; with [missing], also the episodes it has no file for. */
    suspend fun seasons(seriesId: String, missing: Boolean = false): SeasonsResponse =
        get("series/${enc(seriesId)}/seasons${if (missing) "?missing=1" else ""}", SeasonsResponse.serializer(), route = "series/{id}/seasons")

    suspend fun startPlayback(itemId: String, request: PlaybackRequest): PlaybackPlan =
        send("POST", "items/${enc(itemId)}/playback", ArcJson.encodeToString(PlaybackRequest.serializer(), request), PlaybackPlan.serializer(), route = "items/{id}/playback")

    /** The episode that follows in the profile's order, or null (204) for none. */
    /** The pre-roll videos to play before a film started from the beginning (none for anything else). */
    suspend fun preroll(itemId: String): List<PrerollClip> =
        get("items/${enc(itemId)}/preroll", kotlinx.serialization.builtins.ListSerializer(PrerollClip.serializer()), route = "items/{id}/preroll")

    suspend fun next(itemId: String): Item? {
        val (status, body) = raw("GET", "items/${enc(itemId)}/next", null, route = "items/{id}/next")
        return when (status) {
            204 -> null
            in 200..299 -> decode(body, Item.serializer(), "items/{id}/next")
            else -> throw failure(status, body, "items/{id}/next")
        }
    }

    suspend fun reportProgress(itemId: String, report: ProgressReport) {
        val (status, body) = raw("PUT", "items/${enc(itemId)}/progress", ArcJson.encodeToString(ProgressReport.serializer(), report), route = "items/{id}/progress")
        if (status !in 200..299) throw failure(status, body, "items/{id}/progress")
    }

    suspend fun pingTranscode(sessionId: String) {
        val (status, body) = raw("POST", "transcode/${enc(sessionId)}/ping", "{}", route = "transcode/{sid}/ping")
        if (status !in 200..299) throw failure(status, body, "transcode/{sid}/ping")
    }

    suspend fun stopTranscode(sessionId: String) {
        raw("DELETE", "transcode/${enc(sessionId)}", null, route = "transcode/{sid}")
    }

    /**
     * An address Arc gave (artwork, a stream, a playlist) as a full URL. Arc answers with its
     * unversioned /api/ paths; the same endpoints live under /api/v1/, which is what apps use.
     */
    fun absolute(path: String): String {
        val versioned = when {
            path.startsWith("/api/v1/") -> path
            path.startsWith("/api/") -> "/api/v1/" + path.removePrefix("/api/")
            else -> path
        }
        return baseUrl.resolve(versioned)?.toString() ?: (baseUrl.toString().trimEnd('/') + versioned)
    }

    // --- plumbing ---

    private suspend fun put(path: String, json: String, route: String) {
        val (status, body) = raw("PUT", path, json, route)
        if (status !in 200..299) throw failure(status, body, route)
    }

    private suspend fun <T> get(path: String, de: DeserializationStrategy<T>, route: String = path): T = send("GET", path, null, de, route)

    private suspend fun <T> send(method: String, path: String, json: String?, de: DeserializationStrategy<T>, route: String = path): T {
        val (status, body) = raw(method, path, json, route)
        if (status !in 200..299) throw failure(status, body, route)
        // Large pages (a whole library) are decoded off the main thread too.
        return withContext(Dispatchers.Default) { decode(body, de, route) }
    }

    private fun <T> decode(body: String, de: DeserializationStrategy<T>, route: String): T = try {
        ArcJson.decodeFromString(de, body)
    } catch (e: SerializationException) {
        log.warn("unexpected answer from $route: ${e.message}")
        throw IOException("unexpected answer from the server", e)
    } catch (e: IllegalArgumentException) {
        log.warn("unexpected answer from $route: ${e.message}")
        throw IOException("unexpected answer from the server", e)
    }

    private fun failure(status: Int, body: String, route: String): ApiException {
        val obj = runCatching { ArcJson.decodeFromString(JsonObject.serializer(), body) }.getOrNull()
        val message = obj?.get("error")?.jsonPrimitive?.contentOrNull ?: "the server answered $status"
        val needs = obj?.get("needs")?.jsonPrimitive?.contentOrNull
        // Signed out, a wrong PIN or a missing profile are expected; anything else is worth a warning.
        if (status >= 500) log.warn("$route failed: $status $message") else log.debug("$route answered $status")
        return ApiException(status, message, needs, body)
    }

    /** Sends a request to /api/v1/[path]; [route] names it in logs without ids or query strings. */
    private suspend fun raw(method: String, path: String, json: String?, route: String = path): Pair<Int, String> {
        val url = baseUrl.toString().trimEnd('/') + "/api/v1/" + path
        val builder = Request.Builder().url(url).header("Accept", "application/json")
        token()?.let { builder.header("Authorization", "Bearer $it") }
        val body = json?.toRequestBody(JSON)
        builder.method(method, body ?: if (method == "POST" || method == "PUT") ByteArray(0).toRequestBody(JSON) else null)
        val call = client.newCall(builder.build())
        return try {
            call.awaitText()
        } catch (e: IOException) {
            log.warn("$route: can't reach the server (${e.javaClass.simpleName})")
            throw e
        }
    }

    // --- Watch Together (docs/WATCH-TOGETHER-API.md): a room shares a title, a timeline and play/pause ---

    private fun roomPath(id: String) = "watch-together/${enc(id)}"

    /** A room for [itemId] from [startMs], with a code to share (codes last 15 minutes). */
    suspend fun createRoom(itemId: String, startMs: Long = 0): WatchInvite =
        send("POST", "watch-together", JsonObject(mapOf("item_id" to JsonPrimitive(itemId), "start_ms" to JsonPrimitive(startMs.coerceAtLeast(0)))).toString(), WatchInvite.serializer(), route = "watch-together")

    /** Who this profile may invite; with [itemId], whether each may play it. */
    suspend fun roomPeople(itemId: String?): List<WatchPerson> =
        get("watch-together/people" + (itemId?.let { "?item_id=${enc(it)}" } ?: ""), WatchPeople.serializer(), route = "watch-together/people").people

    /** A room for [itemId] made for [profileIds], listed on their Home rows; nobody's in it until they join. */
    suspend fun createInvitedRoom(itemId: String, startMs: Long, profileIds: List<String>): InvitedRoom {
        val body = JsonObject(mapOf(
            "item_id" to JsonPrimitive(itemId),
            "start_ms" to JsonPrimitive(startMs.coerceAtLeast(0)),
            "profile_ids" to kotlinx.serialization.json.JsonArray(profileIds.map { JsonPrimitive(it) }),
        ))
        return send("POST", "watch-together/rooms", body.toString(), InvitedRoom.serializer(), route = "watch-together/rooms")
    }

    /** Invites more people to the room this TV hosts. */
    suspend fun inviteToRoom(id: String, profileIds: List<String>): WatchRoom {
        val body = JsonObject(mapOf("profile_ids" to kotlinx.serialization.json.JsonArray(profileIds.map { JsonPrimitive(it) })))
        return send("POST", "${roomPath(id)}/people", body.toString(), WatchRoom.serializer(), route = "watch-together/{id}/people")
    }

    /** Joins with a code the host shared ("ABCD-1234" or "abcd1234"). */
    suspend fun joinRoom(code: String): WatchRoom =
        send("POST", "watch-together/join", JsonObject(mapOf("code" to JsonPrimitive(WatchCode.normalize(code)))).toString(), WatchRoom.serializer(), route = "watch-together/join")

    /** This TV and profile's own room, or null (404: none). */
    suspend fun currentRoom(): WatchRoom? {
        val (status, body) = raw("GET", "watch-together/current", null, route = "watch-together/current")
        if (status == 404) return null
        if (status !in 200..299) throw failure(status, body, "watch-together/current")
        return decode(body, WatchRoom.serializer(), "watch-together/current")
    }

    suspend fun room(id: String): WatchRoom = get(roomPath(id), WatchRoom.serializer(), route = "watch-together/{id}")

    /** play, pause, seek ([positionMs]), change_item ([itemId]) or end, against the room's [revision]. */
    suspend fun roomCommand(id: String, revision: Long, action: String, positionMs: Long? = null, itemId: String? = null): WatchRoom {
        val body = buildMap {
            put("revision", JsonPrimitive(revision))
            put("action", JsonPrimitive(action))
            positionMs?.let { put("position_ms", JsonPrimitive(it.coerceAtLeast(0))) }
            itemId?.let { put("item_id", JsonPrimitive(it)) }
        }
        return send("POST", "${roomPath(id)}/commands", JsonObject(body).toString(), WatchRoom.serializer(), route = "watch-together/{id}/commands")
    }

    /** Where this TV is (file position), whether it's playing, paused or waiting, and ready. */
    suspend fun roomHeartbeat(id: String, generation: Long, positionMs: Long, state: String, ready: Boolean): WatchRoom {
        val body = JsonObject(mapOf(
            "generation" to JsonPrimitive(generation), "position_ms" to JsonPrimitive(positionMs.coerceAtLeast(0)),
            "state" to JsonPrimitive(state), "ready" to JsonPrimitive(ready),
        ))
        return send("POST", "${roomPath(id)}/heartbeat", body.toString(), WatchRoom.serializer(), route = "watch-together/{id}/heartbeat")
    }

    suspend fun leaveRoom(id: String) {
        val (status, body) = raw("POST", "${roomPath(id)}/leave", null, route = "watch-together/{id}/leave")
        if (status !in 200..299 && status != 404) throw failure(status, body, "watch-together/{id}/leave")
    }

    /** The host's new code (the old one stops working). */
    suspend fun renewRoomCode(id: String, revision: Long): WatchInvite =
        send("POST", "${roomPath(id)}/invite", JsonObject(mapOf("revision" to JsonPrimitive(revision))).toString(), WatchInvite.serializer(), route = "watch-together/{id}/invite")

    /** Rooms this profile made, was invited to or is in: Home's Watch Together row. */
    suspend fun listedRooms(): List<ListedRoom> = get("watch-together/rooms", ListedRooms.serializer(), route = "watch-together/rooms").rooms

    /** Joins a room from the list, without a code. */
    suspend fun acceptRoom(id: String): WatchRoom =
        send("POST", "watch-together/rooms/${enc(id)}/join", null, WatchRoom.serializer(), route = "watch-together/rooms/{id}/join")

    /** Declines an invitation, or cancels a room this profile made. */
    suspend fun dismissRoom(id: String) {
        val (status, body) = raw("POST", "watch-together/rooms/${enc(id)}/dismiss", null, route = "watch-together/rooms/{id}/dismiss")
        if (status !in 200..299 && status != 404) throw failure(status, body, "watch-together/rooms/{id}/dismiss")
    }

    /** One clock sample: the server's receive and send times for [clientSentMs]. */
    suspend fun roomTime(clientSentMs: Long): RoomTime = get("watch-together/time?client_sent_ms=$clientSentMs", RoomTime.serializer(), route = "watch-together/time")

    private companion object {
        val JSON = "application/json".toMediaType()
        val ItemList = kotlinx.serialization.builtins.ListSerializer(Item.serializer())
        fun enc(s: String): String = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    }
}

/**
 * Sends the call and reads the whole answer on OkHttp's own thread: reading a body is network
 * I/O too, and Android refuses it on the main thread (NetworkOnMainThreadException).
 */
private suspend fun Call.awaitText(): Pair<Int, String> = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            val result = try {
                response.use { it.code to it.body.string() }
            } catch (e: IOException) {
                if (!cont.isCancelled) cont.resumeWithException(e)
                return
            }
            cont.resume(result)
        }
        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}

/** A filter's value as a query parameter, or null for something /items doesn't take. */
internal fun JsonPrimitive.asQueryValue(): String? = when {
    isString -> content
    booleanOrNull != null -> if (booleanOrNull == true) "1" else null
    else -> contentOrNull
}
