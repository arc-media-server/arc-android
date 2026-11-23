package org.arcmediaserver.android.ui.launch

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import org.arcmediaserver.android.data.display
import org.arcmediaserver.android.ui.components.UiError
import org.arcmediaserver.android.ui.components.text
import org.arcmediaserver.android.ui.components.requestWhenReady
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Icon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.arcmediaserver.android.AppContainer
import org.arcmediaserver.android.R
import org.arcmediaserver.android.data.ApiException
import org.arcmediaserver.android.data.launchProfile
import org.arcmediaserver.android.data.ArcLog
import org.arcmediaserver.android.ui.arcViewModel
import org.arcmediaserver.android.ui.components.ErrorPane
import org.arcmediaserver.android.ui.components.LoadState
import org.arcmediaserver.android.ui.components.Spinner
import org.arcmediaserver.android.ui.components.toUiError
import org.arcmediaserver.android.ui.theme.ArcColors

/** Where the app goes next, from what it knows about the server and the sign-in. */
enum class Destination { Server, SignIn, Profiles, Home }

/** Asks Arc whether this TV is still signed in, and as whom. */
suspend fun whereNext(container: AppContainer, fresh: Boolean = false): Destination {
    val api = container.api() ?: return Destination.Server
    if (container.session.token == null) return Destination.SignIn
    val status = api.authStatus()
    if (!status.signedIn) {
        ArcLog.Android.info("this TV's sign-in is no longer valid; asking for a new code")
        container.session.token = null
        return Destination.SignIn
    }
    if (status.friend) return Destination.Home

    val automaticId = container.session.automaticProfileId
    val selected = launchProfile(status, automaticId, fresh)
    if (selected != null) {
        val chosen = if (selected.id == status.profile?.id) true else try {
            api.chooseProfile(selected.id, pin = null)
            true
        } catch (e: ApiException) {
            if (e.status !in 400..499) throw e
            ArcLog.Android.warn("automatic profile sign-in failed (HTTP ${e.status})")
            false
        }
        if (chosen) {
            if (fresh && selected.id == automaticId) ArcLog.Android.info("signing in automatically as the saved profile")
            return Destination.Home
        }
    }
    if (fresh) {
        if (status.profile != null) api.leaveProfile()
        ArcLog.Android.info("starting at Who's watching")
    }
    return Destination.Profiles
}

class LaunchViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow<LoadState<Destination>>(LoadState.Loading)
    val state: StateFlow<LoadState<Destination>> = _state

    private val _retrying = MutableStateFlow(false)
    /** Trying again while the "can't connect" screen stays up (it doesn't flash back to the logo). */
    val retrying: StateFlow<Boolean> = _retrying
    private var failures = 0

    /** The server this TV connects to, for the "can't connect" screen. */
    val serverName: String? get() = container.session.serverName
    val serverAddress: String? get() = container.session.server?.display()

    init {
        load()
    }

    /** Asks Arc where to go; [quiet] keeps the error up while trying again (automatic or Retry). */
    fun load(quiet: Boolean = _state.value is LoadState.Failed) {
        if (_retrying.value) return
        if (quiet) _retrying.value = true else _state.value = LoadState.Loading
        viewModelScope.launch {
            val next = try {
                LoadState.Ready(whereNext(container, fresh = true))
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                LoadState.Failed(e.toUiError())
            }
            if (next is LoadState.Failed) {
                failures++
                // Once when it starts failing; the automatic tries after that only at debug.
                if (failures == 1) ArcLog.Android.warn("can't connect to Arc at launch: ${next.error.javaClass.simpleName}")
                else ArcLog.Android.debug("still can't connect to Arc (try $failures)")
            } else if (failures > 0) {
                ArcLog.Android.info("connected to Arc after ${failures + 1} tries")
                failures = 0
            }
            _state.value = next
            _retrying.value = false
        }
    }
}

/** Whether a launch failure is worth trying again by itself: the server down, slow or failing. */
fun retriesItself(error: UiError): Boolean = when (error) {
    UiError.Unreachable, UiError.Timeout -> true
    is UiError.Server -> error.status >= 500 || error.status == 0
    else -> false
}

/** Seconds between automatic tries while the server can't be reached (Plex keeps trying too). */
const val RETRY_EVERY_SECONDS = 10

@Composable
fun LaunchRoute(onDestination: (Destination) -> Unit, onChangeServer: () -> Unit) {
    val vm = arcViewModel { LaunchViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val retrying by vm.retrying.collectAsStateWithLifecycle()
    LaunchedEffect(state) { (state as? LoadState.Ready)?.let { onDestination(it.value) } }
    when (val s = state) {
        is LoadState.Failed -> {
            // Counts down to the next automatic try, while the server is down or slow.
            var secondsLeft by remember { mutableStateOf<Int?>(null) }
            LaunchedEffect(s, retrying) {
                secondsLeft = null
                if (retrying || !retriesItself(s.error)) return@LaunchedEffect
                for (n in RETRY_EVERY_SECONDS downTo 1) {
                    secondsLeft = n
                    kotlinx.coroutines.delay(1_000)
                }
                secondsLeft = null
                vm.load(quiet = true)
            }
            ServerOffline(
                serverName = vm.serverName,
                address = vm.serverAddress,
                error = s.error,
                retrying = retrying,
                secondsLeft = secondsLeft,
                onRetry = { vm.load(quiet = true) },
                onChangeServer = onChangeServer,
            )
        }
        else -> Box(Modifier.fillMaxSize().background(ArcColors.Background), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.foundation.Image(painterResource(R.drawable.arc_logo_stacked), contentDescription = stringResource(R.string.app_name), modifier = Modifier.height(140.dp).aspectRatio(65.42f / 69.16f))
                Spacer(Modifier.height(24.dp))
                Spinner(size = 28.dp)
            }
        }
    }
}

/**
 * Arc can't be reached as the app opens, as Plex's "Unable to connect": which server (its name and
 * address), why, Try Again (focused) and Use Another Server. While the server is down or slow it
 * tries again every [RETRY_EVERY_SECONDS] seconds by itself, and moves on once it answers.
 */
@Composable
fun ServerOffline(
    serverName: String?,
    address: String?,
    error: UiError,
    retrying: Boolean,
    secondsLeft: Int?,
    onRetry: () -> Unit,
    onChangeServer: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    Box(Modifier.fillMaxSize().background(ArcColors.Background), contentAlignment = Alignment.Center) {
        org.arcmediaserver.android.ui.profiles.AuthGlow()
        Column(Modifier.widthIn(max = 600.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.foundation.Image(painterResource(R.drawable.arc_logo_stacked), contentDescription = stringResource(R.string.app_name), modifier = Modifier.height(72.dp).aspectRatio(65.42f / 69.16f))
            Spacer(Modifier.height(28.dp))
            Text(
                if (serverName.isNullOrBlank()) stringResource(R.string.offline_title) else stringResource(R.string.offline_title_named, serverName),
                style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center,
            )
            if (!address.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(address, style = MaterialTheme.typography.titleMedium, color = ArcColors.Muted, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(14.dp))
            Text(error.text(), style = MaterialTheme.typography.bodyLarge, color = ArcColors.Muted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Button(onClick = onRetry, enabled = !retrying, modifier = Modifier.focusRequester(focus)) {
                    Text(stringResource(R.string.offline_try_again))
                }
                OutlinedButton(onClick = onChangeServer) { Text(stringResource(R.string.signin_change_server)) }
            }
            Spacer(Modifier.height(18.dp))
            // "Trying again in 8 s" (or "Trying again…"), so it's clear the TV will carry on by itself.
            Row(Modifier.height(24.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    retrying -> {
                        Spinner(size = 16.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.offline_trying), style = MaterialTheme.typography.bodyMedium, color = ArcColors.Subtle)
                    }
                    secondsLeft != null -> Text(stringResource(R.string.offline_trying_in, secondsLeft), style = MaterialTheme.typography.bodyMedium, color = ArcColors.Subtle)
                }
            }
        }
    }
    LaunchedEffect(Unit) { focus.requestWhenReady() }
}
