package org.arcmediaserver.android

import android.content.Context
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.arcmediaserver.android.data.ArcApi
import org.arcmediaserver.android.data.SessionStore
import org.arcmediaserver.android.data.sessionStore
import org.arcmediaserver.android.playback.deviceCaps
import java.util.concurrent.TimeUnit

/** The app's long-lived objects, made once in [ArcApplication]. */
class AppContainer(private val context: Context, val session: SessionStore = sessionStore(context)) {
    /** The application context, for system services (the network, for finding Arc on it). */
    val appContext: Context get() = context

    /** Work that must finish even when its screen has gone (telling Arc playback stopped). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Sends the device token to Arc, and only to Arc (artwork may redirect to TMDB). */
    private val auth = Interceptor { chain ->
        val req = chain.request()
        val server = session.server
        val token = session.token
        if (server != null && token != null && req.url.host == server.host && req.url.port == server.port) {
            chain.proceed(req.newBuilder().header("Authorization", "Bearer $token").build())
        } else {
            chain.proceed(req)
        }
    }

    private val baseClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** For artwork and streams: signed in to the current server. */
    val mediaClient: OkHttpClient = baseClient.newBuilder().addInterceptor(auth).build()

    private var cachedApi: ArcApi? = null

    /** The API of the server chosen, or null before one is. */
    fun api(): ArcApi? {
        val server = session.server ?: return null
        cachedApi?.takeIf { it.baseUrl == server }?.let { return it }
        return ArcApi(server, baseClient, token = { session.token }).also { cachedApi = it }
    }

    /**
     * Watched and watchlist changes made in the app (a card's menu, a title's page), so every card
     * of that title shows them at once, whichever screen loaded it. Playing a title clears its
     * entry, since playing changes it on the server.
     */
    val itemChanges = androidx.compose.runtime.mutableStateMapOf<String, org.arcmediaserver.android.data.Item>()

    /** Titles taken out of Continue Watching this session, left off Home's row at once. */
    val offContinue = androidx.compose.runtime.mutableStateMapOf<String, Boolean>()

    /**
     * Ultra Blur colours Arc gave for titles this session, by title id (null: Arc had none). Kept
     * for the signed-in profile only: cleared when the profile changes or the TV signs out.
     */
    val palettes = java.util.concurrent.ConcurrentHashMap<String, java.util.Optional<org.arcmediaserver.android.data.PaletteColors>>()

    /** Google TV's Continue watching row, kept in step with Arc's. */
    val watchNext = org.arcmediaserver.android.watchnext.WatchNextPublisher(context)

    /** Brings Google TV's Continue watching row up to date (the app going to the background). */
    fun syncWatchNext() {
        appScope.launch { watchNext.sync(if (session.token != null) api() else null) }
    }

    /** A title Google TV's row asked to resume, until the app is where it can play it. */
    val pendingPlay = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    /** This TV's play queue on Arc, for the profile. */
    val queues = org.arcmediaserver.android.data.PlayQueues(::api)

    /** Live updates from Arc while the app is on screen (MainActivity starts and stops them). */
    val live = org.arcmediaserver.android.data.LiveUpdates(::api, mediaClient, appScope).also { live ->
        // The profile's preferences changed elsewhere (the web, another TV): follow Hide Spoilers
        // and Theme Music.
        appScope.launch {
            live.changes.collect { topics ->
                if (!org.arcmediaserver.android.data.LiveTopics.touches(topics, org.arcmediaserver.android.data.LiveTopics.prefs)) return@collect
                runCatching { api()?.prefs() }.getOrNull()?.let { applyPrefs(it) }
            }
        }
    }

    /** Watch Together: the rooms on Home and this TV's room membership. */
    val together by lazy { org.arcmediaserver.android.ui.together.Together(this) }

    init {
        // The rooms list changed (made, invited, joined, left, declined, ended): Home's row follows.
        appScope.launch {
            live.changes.collect { topics ->
                if (org.arcmediaserver.android.data.LiveTopics.touches(topics, org.arcmediaserver.android.data.LiveTopics.together)) together.refreshRooms()
            }
        }
    }

    /** A user-facing string, for code outside a screen (notes, cards built in view models). */
    fun text(id: Int, vararg args: Any): String = context.getString(id, *args)

    /** The profile's Hide Spoilers, as last read from (or saved to) its preferences. */
    val hideSpoilers = kotlinx.coroutines.flow.MutableStateFlow(false)

    /** The profile's Theme Music, as last read from (or saved to) its preferences. */
    val themeMusic = kotlinx.coroutines.flow.MutableStateFlow(true)

    /** The profile's Show Missing Episodes, as last read from (or saved to) its preferences. */
    val showMissingEpisodes = kotlinx.coroutines.flow.MutableStateFlow(false)

    /** Takes up the profile's preferences that every screen follows. */
    fun applyPrefs(prefs: org.arcmediaserver.android.data.Prefs) {
        hideSpoilers.value = prefs.hideSpoilers
        themeMusic.value = prefs.themeMusic
        showMissingEpisodes.value = prefs.showMissingEpisodes
    }

    /** A different profile (or none): what was kept for the last one doesn't carry over. */
    fun profileChanged() {
        live.reset()
        together.reset()
        hideSpoilers.value = false
        themeMusic.value = true
        showMissingEpisodes.value = false
        palettes.clear()
        itemChanges.clear()
        offContinue.clear()
        queues.reset()
    }

    /** [item] with any change made in the app since it was loaded. */
    fun withChanges(item: org.arcmediaserver.android.data.Item): org.arcmediaserver.android.data.Item =
        itemChanges[item.id]?.let { item.copy(userData = it.userData, unwatchedCount = it.unwatchedCount) } ?: item

    /** Signs this TV out of Arc (forgetting its token), then calls [done] on the main thread. */
    fun signOut(done: () -> Unit) {
        val api = api()
        appScope.launch {
            runCatching { api?.signOut() }
            session.token = null
            profileChanged()
            watchNext.sync(null)
            org.arcmediaserver.android.data.ArcLog.Android.info("signed out")
            kotlinx.coroutines.withContext(Dispatchers.Main) { done() }
        }
    }

    /** An API for checking an address before it's saved. */
    fun apiFor(server: HttpUrl): ArcApi = ArcApi(server, baseClient, token = { null })

    // Maximum audio channels: sample the output again for each new player, after route changes.
    val playerCaps get() = deviceCaps(context)

    /** How this TV appears in Arc's Settings > Devices: the name it was given, e.g. "Living Room TV". */
    val deviceName: String by lazy {
        val named = runCatching { Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME) }.getOrNull()
        named?.takeIf { it.isNotBlank() } ?: "${Build.MANUFACTURER} ${Build.MODEL}".trim()
    }
}
