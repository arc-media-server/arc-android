package org.arcmediaserver.android.ui.mobile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable
import org.arcmediaserver.android.AppContainer
import org.arcmediaserver.android.ArcApplication
import org.arcmediaserver.android.data.Item
import org.arcmediaserver.android.ui.DetailsDest
import org.arcmediaserver.android.ui.LaunchDest
import org.arcmediaserver.android.ui.PersonDest
import org.arcmediaserver.android.ui.PlayerDest
import org.arcmediaserver.android.ui.ProfilesDest
import org.arcmediaserver.android.ui.ServerDest
import org.arcmediaserver.android.ui.SignInDest
import org.arcmediaserver.android.ui.components.LocalHideSpoilers
import org.arcmediaserver.android.ui.components.LocalImageUrl
import org.arcmediaserver.android.ui.detailsFor
import org.arcmediaserver.android.ui.launch.Destination

/** The phone's tabs (Home, Libraries, Search), with a title's page and the player above them. */
@Serializable object MobileShellDest
@Serializable object MobileSettingsDest

/** Goes somewhere as a fresh start: nothing to go back to (after signing in, or out). */
private fun NavHostController.startAt(route: Any) = navigate(route) { popUpTo(0) { inclusive = true } }

private fun NavHostController.go(destination: Destination) = startAt(
    when (destination) {
        Destination.Server -> ServerDest
        Destination.SignIn -> SignInDest
        Destination.Profiles -> ProfilesDest
        Destination.Home -> MobileShellDest
    },
)

/**
 * The phone and tablet interface: the same journey as the TV's (server, sign-in, Who's watching,
 * then Home) and the same view models, drawn with Material 3 for touch. Routes the TV also has
 * (a title's page, a person, the player) use the TV's route types, so a link means the same in
 * both interfaces.
 */
@Composable
fun MobileNavHost() {
    val nav = rememberNavController()
    val container = (LocalContext.current.applicationContext as ArcApplication).container
    MobileScreens(container) {
        NavHost(nav, startDestination = LaunchDest) {
            composable<LaunchDest> { MobileLaunchRoute(onDestination = nav::go, onChangeServer = { nav.startAt(ServerDest) }) }
            composable<ServerDest> { MobileServerRoute(onConnected = { nav.startAt(SignInDest) }) }
            composable<SignInDest> { MobileSignInRoute(onSignedIn = nav::go, onChangeServer = { nav.startAt(ServerDest) }) }
            composable<ProfilesDest> {
                MobileProfilesRoute(onChosen = { nav.startAt(MobileShellDest) }, onSignedOut = { nav.startAt(SignInDest) })
            }
            composable<MobileShellDest> {
                MobileShell(
                    onOpen = { item -> nav.navigate(detailsFor(item)) },
                    onPlay = { item, start, entry -> nav.navigate(PlayerDest(item.id, start, entryId = entry)) },
                    onPerson = { id -> nav.navigate(PersonDest(id)) },
                    onAccount = { nav.navigate(MobileSettingsDest) },
                )
            }
            composable<MobileSettingsDest> {
                MobileSettingsRoute(
                    onBack = { nav.popBackStack() },
                    onSwitchProfile = { nav.navigate(ProfilesDest) },
                    onSignedOut = { nav.startAt(SignInDest) },
                    onSwitchServer = { nav.startAt(ServerDest) },
                )
            }
            composable<DetailsDest> { entry ->
                val dest = entry.toRoute<DetailsDest>()
                MobileDetailsRoute(
                    itemId = dest.itemId,
                    episodeId = dest.episodeId,
                    onBack = { nav.popBackStack() },
                    onPlay = { item, start -> nav.navigate(PlayerDest(item.id, start)) },
                    onPlayQueued = { item, e -> nav.navigate(PlayerDest(item.id, item.resumeMs, entryId = e)) },
                    onOpen = { item -> nav.navigate(detailsFor(item)) },
                    onPerson = { id -> nav.navigate(PersonDest(id)) },
                )
            }
            composable<PersonDest> { entry ->
                MobilePersonRoute(entry.toRoute<PersonDest>().personId, onBack = { nav.popBackStack() }, onOpen = { item: Item -> nav.navigate(detailsFor(item)) })
            }
            composable<PlayerDest> { entry ->
                val dest = entry.toRoute<PlayerDest>()
                MobilePlayerRoute(
                    dest.itemId,
                    dest.startMs,
                    dest.entryId,
                    dest.carryOn,
                    dest.fileId,
                    onClose = { nav.popBackStack() },
                    // The next title replaces this one, so Back still returns to where play began.
                    onPlayNextEntry = { next, e ->
                        nav.navigate(PlayerDest(next.id, next.resumeMs, entryId = e, carryOn = e == null, preroll = false)) { popUpTo<PlayerDest> { inclusive = true } }
                    },
                )
            }
        }
    }
}

/** Artwork addresses on the current server and Hide Spoilers, for every phone screen. */
@Composable
fun MobileScreens(container: AppContainer, content: @Composable () -> Unit) {
    val hideSpoilers by container.hideSpoilers.collectAsState()
    CompositionLocalProvider(
        LocalImageUrl provides { path -> container.api()?.absolute(path) ?: path },
        LocalHideSpoilers provides hideSpoilers,
        content = content,
    )
}
