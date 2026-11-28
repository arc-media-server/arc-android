package org.arcmediaserver.android.ui.mobile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.arcmediaserver.android.AppContainer
import org.arcmediaserver.android.R
import org.arcmediaserver.android.data.ArcLog
import org.arcmediaserver.android.data.Profile
import org.arcmediaserver.android.data.display
import org.arcmediaserver.android.ui.arcViewModel
import org.arcmediaserver.android.ui.components.ArcIcons
import org.arcmediaserver.android.ui.components.LoadState
import org.arcmediaserver.android.ui.components.UiError
import org.arcmediaserver.android.ui.components.text
import org.arcmediaserver.android.ui.components.toUiError
import org.arcmediaserver.android.ui.launch.Destination
import org.arcmediaserver.android.ui.launch.LaunchViewModel
import org.arcmediaserver.android.ui.launch.whereNext
import org.arcmediaserver.android.ui.profiles.Avatar
import org.arcmediaserver.android.ui.profiles.ProfilesUiState
import org.arcmediaserver.android.ui.profiles.ProfilesViewModel
import org.arcmediaserver.android.ui.server.ServerProblem
import org.arcmediaserver.android.ui.server.ServerUiState
import org.arcmediaserver.android.ui.server.ServerViewModel
import org.arcmediaserver.android.ui.signin.SignInUiState
import org.arcmediaserver.android.ui.signin.SignInViewModel
import org.arcmediaserver.android.ui.theme.ArcColors

/** A centred column for the sign-in screens: Arc's logo on top, at most 480dp wide on a tablet. */
@Composable
internal fun AuthPage(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.arc_logo), contentDescription = stringResource(R.string.app_name), modifier = Modifier.height(36.dp).aspectRatio(136.02f / 36.2f))
            Spacer(Modifier.height(32.dp))
            content()
        }
    }
}

@Composable
fun MobileLaunchRoute(onDestination: (Destination) -> Unit, onChangeServer: () -> Unit) {
    val vm = arcViewModel { LaunchViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val retrying by vm.retrying.collectAsStateWithLifecycle()
    LaunchedEffect(state) { (state as? LoadState.Ready)?.let { onDestination(it.value) } }
    when (val s = state) {
        // As the TV's "can't connect" screen: which server, why, Try Again and Use Another Server.
        is LoadState.Failed -> AuthPage {
            val name = vm.serverName
            Text(
                if (name != null) stringResource(R.string.offline_title_named, name) else stringResource(R.string.offline_title),
                style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center,
            )
            vm.serverAddress?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = ArcColors.Muted, modifier = Modifier.padding(top = 4.dp)) }
            Spacer(Modifier.height(16.dp))
            Text(s.error.text(), style = MaterialTheme.typography.bodyMedium, color = ArcColors.Muted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Button(onClick = { vm.load(quiet = true) }, enabled = !retrying, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (retrying) R.string.offline_trying else R.string.offline_try_again))
            }
            OutlinedButton(onClick = onChangeServer, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(stringResource(R.string.signin_change_server)) }
        }
        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(R.drawable.arc_logo_stacked), contentDescription = stringResource(R.string.app_name), modifier = Modifier.height(112.dp).aspectRatio(65.42f / 69.16f))
                Spacer(Modifier.height(24.dp))
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
            }
        }
    }
}

@Composable
fun MobileServerRoute(onConnected: () -> Unit) {
    val vm = arcViewModel { ServerViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.connected) {
        if (state.connected) {
            vm.navigated()
            onConnected()
        }
    }
    MobileServerScreen(state, onEdit = vm::edit, onConnect = vm::connect, onChoose = vm::choose, onScan = vm::scan)
}

/** Connect to your Arc server: type its address, or pick one found on this network. */
@Composable
fun MobileServerScreen(
    state: ServerUiState,
    onEdit: (String) -> Unit,
    onConnect: () -> Unit,
    onChoose: (org.arcmediaserver.android.data.FoundServer) -> Unit,
    onScan: () -> Unit,
) = AuthPage {
    Text(stringResource(R.string.server_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.server_body), style = MaterialTheme.typography.bodyMedium, color = ArcColors.Muted, textAlign = TextAlign.Center)
    Spacer(Modifier.height(24.dp))
    val problem = when (val p = state.problem) {
        ServerProblem.Invalid -> stringResource(R.string.server_invalid)
        ServerProblem.NotArc -> stringResource(R.string.server_not_arc)
        is ServerProblem.Failed -> p.error.text()
        null -> null
    }
    OutlinedTextField(
        value = state.address,
        onValueChange = onEdit,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.server_hint)) },
        isError = problem != null,
        supportingText = { Text(problem ?: stringResource(R.string.server_port_note)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onGo = { onConnect() }),
        enabled = !state.checking,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(16.dp))
    Button(onClick = onConnect, enabled = !state.checking && state.address.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(if (state.checking) R.string.server_checking else R.string.server_connect))
    }
    if (!state.canScan) return@AuthPage
    Spacer(Modifier.height(32.dp))
    Text(stringResource(R.string.server_on_network), style = MaterialTheme.typography.titleSmall, color = ArcColors.Muted, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    when {
        state.scanning && state.found.isEmpty() -> Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.size(12.dp))
            Text(stringResource(R.string.server_looking), style = MaterialTheme.typography.bodyMedium, color = ArcColors.Muted)
        }
        state.found.isEmpty() -> {
            Text(stringResource(R.string.server_none_found), style = MaterialTheme.typography.bodyMedium, color = ArcColors.Muted, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = onScan, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.server_search_again)) }
        }
        else -> state.found.forEach { server ->
            ListItem(
                headlineContent = { Text(server.name) },
                supportingContent = { Text(stringResource(R.string.server_found_detail, server.address, server.version)) },
                leadingContent = { Icon(ArcIcons.Tv, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = ArcColors.Background),
                modifier = Modifier.clickable(enabled = !state.checking) { onChoose(server) },
            )
            HorizontalDivider()
        }
    }
}

/** How a phone signs in: the household password (or a friend's name and password), or a code. */
enum class SignInWay { Password, Code }

data class PasswordSignIn(
    val friend: Boolean = false,
    val name: String = "",
    val password: String = "",
    val checking: Boolean = false,
    val error: UiError? = null,
    val done: Destination? = null,
)

/**
 * Signing in with a password, as Plex's phone app signs in with an account: the household's
 * password (then Who's watching), or a friend's own name and password (their profile already).
 * The password is sent once and never kept; Arc's device token is kept as the TV's is.
 */
class PasswordSignInViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(PasswordSignIn())
    val state: StateFlow<PasswordSignIn> = _state

    fun edit(transform: (PasswordSignIn) -> PasswordSignIn) = _state.update { transform(it).copy(error = null) }

    fun signIn() {
        val api = container.api() ?: return
        val s = _state.value
        if (s.checking || s.password.isEmpty() || (s.friend && s.name.isBlank())) return
        _state.update { it.copy(checking = true, error = null) }
        viewModelScope.launch {
            try {
                val token = if (s.friend) api.friendLogin(s.name.trim(), s.password, container.deviceName) else api.login(s.password, container.deviceName)
                container.session.token = token
                ArcLog.Android.info(if (s.friend) "signed in as a friend with a password" else "signed in with the household password")
                _state.update { it.copy(password = "", done = whereNext(container)) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // A wrong password is expected (Arc says so); anything else is worth a line.
                if ((e as? org.arcmediaserver.android.data.ApiException)?.status !in 400..499) ArcLog.Android.warn("password sign-in failed: ${e.javaClass.simpleName}")
                _state.update { it.copy(checking = false, error = e.toUiError()) }
            }
        }
    }
}

@Composable
fun MobileSignInRoute(onSignedIn: (Destination) -> Unit, onChangeServer: () -> Unit) {
    var way by rememberSaveable { mutableStateOf(SignInWay.Password) }
    val password = arcViewModel { PasswordSignInViewModel(it) }
    val passwordState by password.state.collectAsStateWithLifecycle()
    LaunchedEffect(passwordState.done) { passwordState.done?.let(onSignedIn) }
    AuthPage {
        Text(stringResource(R.string.signin_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(20.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SignInWay.entries.forEachIndexed { i, w ->
                SegmentedButton(
                    selected = way == w,
                    onClick = { way = w },
                    shape = SegmentedButtonDefaults.itemShape(i, SignInWay.entries.size),
                ) { Text(stringResource(if (w == SignInWay.Password) R.string.mobile_signin_password else R.string.mobile_signin_code)) }
            }
        }
        Spacer(Modifier.height(24.dp))
        when (way) {
            SignInWay.Password -> PasswordForm(passwordState, password::edit, password::signIn)
            // The code is only asked for while that tab is open.
            SignInWay.Code -> {
                val vm = arcViewModel { SignInViewModel(it) }
                val state by vm.state.collectAsStateWithLifecycle()
                LaunchedEffect(state.done) { state.done?.let(onSignedIn) }
                CodeSignIn(state, onNewCode = { vm.start() })
            }
        }
        Spacer(Modifier.height(24.dp))
        val context = androidx.compose.ui.platform.LocalContext.current
        val server = androidx.compose.runtime.remember { (context.applicationContext as org.arcmediaserver.android.ArcApplication).container.session.server?.display().orEmpty() }
        if (server.isNotEmpty()) Text(stringResource(R.string.signin_server, server), style = MaterialTheme.typography.bodySmall, color = ArcColors.Muted)
        TextButton(onClick = onChangeServer) { Text(stringResource(R.string.signin_change_server)) }
    }
}

@Composable
private fun PasswordForm(state: PasswordSignIn, onEdit: ((PasswordSignIn) -> PasswordSignIn) -> Unit, onSignIn: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(if (state.friend) R.string.mobile_signin_friend_body else R.string.mobile_signin_household_body),
            style = MaterialTheme.typography.bodyMedium, color = ArcColors.Muted,
        )
        Spacer(Modifier.height(16.dp))
        if (state.friend) {
            OutlinedTextField(
                value = state.name,
                onValueChange = { v -> onEdit { it.copy(name = v) } },
                label = { Text(stringResource(R.string.mobile_signin_name)) },
                singleLine = true,
                enabled = !state.checking,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }
        OutlinedTextField(
            value = state.password,
            onValueChange = { v -> onEdit { it.copy(password = v) } },
            label = { Text(stringResource(R.string.mobile_signin_password_field)) },
            singleLine = true,
            enabled = !state.checking,
            visualTransformation = PasswordVisualTransformation(),
            isError = state.error != null,
            supportingText = state.error?.let { e -> { Text(e.text()) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onDone = { onSignIn() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onSignIn,
            enabled = !state.checking && state.password.isNotEmpty() && (!state.friend || state.name.isNotBlank()),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(if (state.checking) R.string.mobile_signin_checking else R.string.mobile_signin_button)) }
        TextButton(onClick = { onEdit { it.copy(friend = !it.friend) } }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(if (state.friend) R.string.mobile_signin_as_household else R.string.mobile_signin_as_friend))
        }
    }
}

/** The TV's code, for a phone without the password: approved from Arc on another device. */
@Composable
private fun CodeSignIn(state: SignInUiState, onNewCode: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.mobile_signin_code_body), style = MaterialTheme.typography.bodyMedium, color = ArcColors.Muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        when {
            state.error != null -> {
                Text(state.error.text(), style = MaterialTheme.typography.bodyMedium, color = ArcColors.Error, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onNewCode) { Text(stringResource(R.string.signin_new_code)) }
            }
            state.code == null -> CircularProgressIndicator(Modifier.size(32.dp), strokeWidth = 3.dp)
            else -> {
                Text(state.code, style = MaterialTheme.typography.displayMedium, letterSpacing = MaterialTheme.typography.displayMedium.fontSize * 0.15f)
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(if (state.renewed) R.string.signin_expired else R.string.signin_waiting),
                    style = MaterialTheme.typography.bodySmall, color = ArcColors.Muted,
                )
            }
        }
    }
}

@Composable
fun MobileProfilesRoute(onChosen: () -> Unit, onSignedOut: () -> Unit) {
    val vm = arcViewModel { ProfilesViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.chosen) {
        if (state.chosen) {
            vm.navigated()
            onChosen()
        }
    }
    BackHandler(enabled = state.pin != null) { vm.cancelPin() }
    MobileProfilesScreen(state, onChoose = vm::choose, onRetry = vm::load, onDigit = vm::typeDigit, onDelete = vm::deleteDigit, onCancelPin = vm::cancelPin, onSignOut = { vm.signOut(onSignedOut) })
}

/** Who's watching? Round avatars in a grid (the web's picker), then a PIN when the profile has one. */
@Composable
fun MobileProfilesScreen(
    state: ProfilesUiState,
    onChoose: (Profile) -> Unit,
    onRetry: () -> Unit,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onCancelPin: () -> Unit,
    onSignOut: () -> Unit,
) {
    val pin = state.pin
    if (pin != null) {
        AuthPage {
            Avatar(pin.profile, 88.dp)
            Spacer(Modifier.height(12.dp))
            Text(pin.profile.name, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(24.dp))
            Text(stringResource(if (pin.wrong) R.string.pin_wrong else R.string.pin_enter), style = MaterialTheme.typography.bodyLarge, color = if (pin.wrong) ArcColors.Error else ArcColors.Muted)
            Spacer(Modifier.height(16.dp))
            // Digits typed on the number keyboard go to the same PIN check as the TV's pad.
            OutlinedTextField(
                value = pin.digits,
                onValueChange = { v ->
                    val clean = v.filter(Char::isDigit)
                    when {
                        clean.length > pin.digits.length -> clean.drop(pin.digits.length).forEach(onDigit)
                        clean.length < pin.digits.length -> repeat(pin.digits.length - clean.length) { onDelete() }
                    }
                },
                enabled = !pin.checking,
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center),
                modifier = Modifier.widthIn(max = 200.dp),
            )
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onCancelPin) { Text(stringResource(R.string.pin_back)) }
        }
        return
    }
    AuthPage {
        Text(stringResource(R.string.profiles_title), style = MaterialTheme.typography.headlineSmall)
        state.serverName?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = ArcColors.Muted, modifier = Modifier.padding(top = 4.dp)) }
        Spacer(Modifier.height(32.dp))
        when (val p = state.profiles) {
            LoadState.Loading -> CircularProgressIndicator()
            is LoadState.Failed -> MobileError(p.error, onRetry)
            is LoadState.Ready -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(104.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    userScrollEnabled = false,
                    // A grid inside the page's scrolling column needs a bounded height.
                    modifier = Modifier.fillMaxWidth().height((((p.value.size + 2) / 3) * 140).dp),
                ) {
                    items(p.value, key = { it.id }) { profile ->
                        val locked = profile.hasPin || (profile.isAdmin && !state.passwordSession)
                        Column(
                            Modifier.clickable { onChoose(profile) }.padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box {
                                Avatar(profile, 88.dp)
                                if (locked) Icon(ArcIcons.Lock, contentDescription = stringResource(R.string.profiles_locked), modifier = Modifier.align(Alignment.BottomEnd).size(20.dp))
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(profile.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        }
                    }
                }
            }
        }
        state.error?.let { Text(it.text(), style = MaterialTheme.typography.bodyMedium, color = ArcColors.Error, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp)) }
        if (state.needsPassword) Text(stringResource(R.string.profiles_needs_password), style = MaterialTheme.typography.bodyMedium, color = ArcColors.Muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp))
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onSignOut) { Text(stringResource(R.string.nav_sign_out)) }
    }
}

/** A failure with Try Again, in the phone's style. */
@Composable
internal fun MobileError(error: UiError, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.error_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(error.text(), style = MaterialTheme.typography.bodyMedium, color = ArcColors.Muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
    }
}
