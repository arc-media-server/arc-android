package org.arcmediaserver.android.ui

import org.arcmediaserver.android.data.Item
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable
import org.arcmediaserver.android.ArcApplication
import org.arcmediaserver.android.ui.browse.BrowseRoute
import org.arcmediaserver.android.ui.components.LocalImageUrl
import org.arcmediaserver.android.ui.details.DetailsRoute
import org.arcmediaserver.android.ui.launch.Destination
import org.arcmediaserver.android.ui.launch.LaunchRoute
import org.arcmediaserver.android.ui.person.PersonRoute
import org.arcmediaserver.android.ui.player.PlayerRoute
import org.arcmediaserver.android.ui.profiles.ProfilesRoute
import org.arcmediaserver.android.ui.server.ServerRoute
import org.arcmediaserver.android.ui.signin.SignInRoute

@Serializable object LaunchDest
@Serializable object ServerDest
@Serializable object SignInDest
@Serializable object ProfilesDest
@Serializable object BrowseDest
/** A title's page; for an episode's card, its show's page landing on [episodeId] (as Plex). */
@Serializable data class DetailsDest(val itemId: String, val episodeId: String? = null)

/** Where a card opens: a title's own page, or for an episode, its show's page on that episode. */
private fun detailsFor(item: Item) =
    if (item.isEpisode && item.parentId != null) DetailsDest(item.parentId, episodeId = item.id) else DetailsDest(item.id)
/** The player: [entryId] plays on through the queue from that entry; [carryOn] adds Auto-Play's episode to it. */
@Serializable data class PlayerDest(val itemId: String, val startMs: Long, val entryId: String? = null, val carryOn: Boolean = false, val fileId: Long? = null)
@Serializable data class PersonDest(val personId: Long)

/** A collection's page. */
@Serializable data class CollectionDest(val id: String)

/** A playlist's page. */
@Serializable data class PlaylistDest(val id: String)

/** A trailer or other extra, played from its file. */
@Serializable data class ExtraDest(val url: String)

/** Goes somewhere as a fresh start: nothing to go back to (after signing in, or out). */
private fun NavHostController.startAt(route: Any) = navigate(route) { popUpTo(0) { inclusive = true } }

private fun NavHostController.go(destination: Destination) = startAt(
    when (destination) {
        Destination.Server -> ServerDest
        Destination.SignIn -> SignInDest
        Destination.Profiles -> ProfilesDest
        Destination.Home -> BrowseDest
    },
)

@Composable
fun ArcNavHost() {
    val nav = rememberNavController()
    val container = (LocalContext.current.applicationContext as ArcApplication).container
    ResumeFromHomeScreen(nav, container)
    val hideSpoilers by container.hideSpoilers.collectAsState()
    CompositionLocalProvider(
        LocalImageUrl provides { path -> container.api()?.absolute(path) ?: path },
        org.arcmediaserver.android.ui.components.LocalHideSpoilers provides hideSpoilers,
    ) {
        CardMenuHost(container) {
            NavHost(nav, startDestination = LaunchDest) {
                composable<LaunchDest> { LaunchRoute(onDestination = nav::go, onChangeServer = { nav.startAt(ServerDest) }) }
                composable<ServerDest> { ServerRoute(onConnected = { nav.startAt(SignInDest) }) }
                composable<SignInDest> { SignInRoute(onSignedIn = nav::go, onChangeServer = { nav.startAt(ServerDest) }) }
                composable<ProfilesDest> {
                    ProfilesRoute(onChosen = { nav.startAt(BrowseDest) }, onSignedOut = { nav.startAt(SignInDest) })
                }
                composable<BrowseDest> {
                    BrowseRoute(
                        onOpen = { item -> nav.navigate(detailsFor(item)) },
                        onPerson = { person -> nav.navigate(PersonDest(person.id)) },
                        onSwitchProfile = { nav.navigate(ProfilesDest) },
                        onSignedOut = { nav.startAt(SignInDest) },
                        onOpenCollection = { c -> nav.navigate(CollectionDest(c.id)) },
                        onOpenPlaylist = { p -> nav.navigate(PlaylistDest(p.id)) },
                        onPlayQueued = { item, entry -> nav.navigate(PlayerDest(item.id, item.resumeMs, entryId = entry)) },
                        onSwitchServer = { nav.startAt(ServerDest) },
                        onPlay = { item, start -> nav.navigate(PlayerDest(item.id, start)) },
                    )
                }
                composable<CollectionDest> { entry ->
                    org.arcmediaserver.android.ui.browse.CollectionRoute(
                        entry.toRoute<CollectionDest>().id,
                        onOpen = { item -> nav.navigate(detailsFor(item)) },
                        onPlayQueued = { item, entry -> nav.navigate(PlayerDest(item.id, 0, entryId = entry)) },
                    )
                }
                composable<PlaylistDest> { entry ->
                    org.arcmediaserver.android.ui.browse.PlaylistRoute(
                        entry.toRoute<PlaylistDest>().id,
                        onOpen = { item -> nav.navigate(detailsFor(item)) },
                        onPlayQueued = { item, e -> nav.navigate(PlayerDest(item.id, 0, entryId = e)) },
                    )
                }
                composable<DetailsDest> { entry ->
                    val dest = entry.toRoute<DetailsDest>()
                    DetailsRoute(
                        itemId = dest.itemId,
                        episodeId = dest.episodeId,
                        onPlay = { item, start -> nav.navigate(PlayerDest(item.id, start)) },
                        onOpen = { id -> nav.navigate(DetailsDest(id)) },
                        onPerson = { id -> nav.navigate(PersonDest(id)) },
                        onExtra = { extra -> nav.navigate(ExtraDest(extra.streamUrl)) },
                        onPlayQueued = { item, entry -> nav.navigate(PlayerDest(item.id, item.resumeMs, entryId = entry)) },
                        onPlayVersion = { item, start, file -> nav.navigate(PlayerDest(item.id, start, fileId = file)) },
                    )
                }
                composable<ExtraDest> { entry ->
                    org.arcmediaserver.android.ui.player.ExtraPlayerRoute(entry.toRoute<ExtraDest>().url, onClose = { nav.popBackStack() })
                }
                composable<PersonDest> { entry ->
                    PersonRoute(entry.toRoute<PersonDest>().personId, onOpen = { item -> nav.navigate(detailsFor(item)) })
                }
                composable<PlayerDest> { entry ->
                    val dest = entry.toRoute<PlayerDest>()
                    PlayerRoute(
                        dest.itemId,
                        dest.startMs,
                        dest.entryId,
                        dest.carryOn,
                        dest.fileId,
                        onClose = { nav.popBackStack() },
                        // The next title replaces this one, so Back still returns to where play began.
                        onPlayNextEntry = { next, entry ->
                            nav.navigate(PlayerDest(next.id, next.resumeMs, entryId = entry, carryOn = entry == null)) { popUpTo<PlayerDest> { inclusive = true } }
                        },
                    )
                }
            }
        }
    }
}

/**
 * A title chosen in Google TV's Continue watching row plays once the app is signed in with a
 * profile (past the launch, sign-in and profile screens), from where Arc has it now; Back then
 * goes to wherever the app was (Home on a fresh start). A title Arc can't find opens nothing.
 */
@Composable
private fun ResumeFromHomeScreen(nav: NavHostController, container: org.arcmediaserver.android.AppContainer) {
    val pending by container.pendingPlay.collectAsState()
    val entry by nav.currentBackStackEntryAsState()
    val signedIn = entry?.destination?.let { d ->
        !(d.hasRoute<LaunchDest>() || d.hasRoute<ServerDest>() || d.hasRoute<SignInDest>() || d.hasRoute<ProfilesDest>())
    } ?: false
    LaunchedEffect(pending, signedIn) {
        val id = pending ?: return@LaunchedEffect
        if (!signedIn) return@LaunchedEffect
        container.pendingPlay.value = null
        val item = try {
            container.api()?.item(id)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            org.arcmediaserver.android.data.ArcLog.Android.warn("resuming from the home screen failed: ${e.javaClass.simpleName}")
            null
        } ?: return@LaunchedEffect
        if (!item.playable) { nav.navigate(detailsFor(item)); return@LaunchedEffect }
        nav.navigate(PlayerDest(item.id, item.resumeMs)) { popUpTo<PlayerDest> { inclusive = true } }
    }
}
