package org.arcmediaserver.android.ui.server

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.arcmediaserver.android.AppContainer
import org.arcmediaserver.android.R
import org.arcmediaserver.android.ui.components.requestWhenReady
import org.arcmediaserver.android.data.ArcLog
import org.arcmediaserver.android.data.display
import org.arcmediaserver.android.data.normalizeServerAddress
import org.arcmediaserver.android.ui.arcViewModel
import org.arcmediaserver.android.ui.components.Spinner
import org.arcmediaserver.android.ui.components.UiError
import org.arcmediaserver.android.ui.components.text
import org.arcmediaserver.android.ui.components.toUiError
import org.arcmediaserver.android.ui.theme.ArcColors

sealed interface ServerProblem {
    data object Invalid : ServerProblem
    data object NotArc : ServerProblem
    data class Failed(val error: UiError) : ServerProblem
}

data class ServerUiState(
    val address: String = "",
    val checking: Boolean = false,
    val problem: ServerProblem? = null,
    val connected: Boolean = false,
    /** Arc servers found on the home network; [scanning] while it looks. */
    val found: List<org.arcmediaserver.android.data.FoundServer> = emptyList(),
    val scanning: Boolean = false,
    /** Whether a home network could be searched at all (false off Wi-Fi/Ethernet, or on a public one). */
    val canScan: Boolean = true,
)

class ServerViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(ServerUiState(address = container.session.server?.display().orEmpty()))
    val state: StateFlow<ServerUiState> = _state

    fun edit(text: String) = _state.update { it.copy(address = text, problem = null) }

    fun navigated() = _state.update { it.copy(connected = false) }

    init {
        scan()
    }

    /**
     * Looks for Arc on the home network (`GET /discover` on Arc's port across this TV's /24), so
     * it can be chosen rather than typed, as Plex finds its servers.
     */
    fun scan() {
        if (_state.value.scanning) return
        val local = homeAddress()
        if (local == null) {
            _state.update { it.copy(canScan = false, scanning = false) }
            return
        }
        val hosts = org.arcmediaserver.android.data.Discovery.candidates(local.first, local.second)
        _state.update { it.copy(scanning = true, canScan = true, found = emptyList()) }
        viewModelScope.launch {
            val started = System.currentTimeMillis()
            val all = org.arcmediaserver.android.data.Discovery.scan(hosts, okhttp3.OkHttpClient()) { found ->
                _state.update { s -> s.copy(found = (s.found + found).sortedBy { it.name.lowercase() }) }
            }
            ArcLog.Android.info("server discovery: ${all.size} found among ${hosts.size} addresses in ${System.currentTimeMillis() - started} ms")
            _state.update { it.copy(scanning = false) }
        }
    }

    /** Connects to a server found on the network. */
    fun choose(server: org.arcmediaserver.android.data.FoundServer) {
        _state.update { it.copy(address = server.address, problem = null) }
        connect()
    }

    /** This TV's private IPv4 address and network prefix, from the active network's link. */
    private fun homeAddress(): Pair<ByteArray, Int>? = try {
        val cm = container.appContext.getSystemService(android.net.ConnectivityManager::class.java)
        val link = cm?.getLinkProperties(cm.activeNetwork)
        link?.linkAddresses
            ?.firstOrNull { it.address is java.net.Inet4Address && !it.address.isLoopbackAddress && org.arcmediaserver.android.data.Discovery.isPrivate(it.address.address) }
            ?.let { it.address.address to it.prefixLength }
    } catch (e: Exception) {
        ArcLog.Android.debug("server discovery: no network address (${e.javaClass.simpleName})")
        null
    }

    fun connect() {
        val url = normalizeServerAddress(_state.value.address)
        if (url == null) {
            _state.update { it.copy(problem = ServerProblem.Invalid) }
            return
        }
        _state.update { it.copy(checking = true, problem = null) }
        viewModelScope.launch {
            val problem = try {
                val health = container.apiFor(url).health()
                if (health.apiVersion < 1) ServerProblem.NotArc else null.also {
                    ArcLog.Android.info("connected to ${health.name} ${health.version} (API v${health.apiVersion})")
                    container.session.server = url
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // A web page that isn't Arc answers health with HTML (or 404).
                if (e.toUiError() == UiError.Unexpected || (e as? org.arcmediaserver.android.data.ApiException)?.status == 404) ServerProblem.NotArc
                else ServerProblem.Failed(e.toUiError())
            }
            _state.update { it.copy(checking = false, problem = problem, connected = problem == null) }
        }
    }
}

@Composable
fun ServerRoute(onConnected: () -> Unit) {
    val vm = arcViewModel { ServerViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.connected) {
        if (state.connected) {
            vm.navigated()
            onConnected()
        }
    }
    ServerScreen(state, onEdit = vm::edit, onConnect = vm::connect, onChoose = vm::choose, onScan = vm::scan)
}

@Composable
fun ServerScreen(
    state: ServerUiState,
    onEdit: (String) -> Unit,
    onConnect: () -> Unit,
    onChoose: (org.arcmediaserver.android.data.FoundServer) -> Unit = {},
    onScan: () -> Unit = {},
) {
    val fieldFocus = remember { FocusRequester() }
    val firstFound = remember { FocusRequester() }
    Box(Modifier.fillMaxSize().background(ArcColors.Background), contentAlignment = Alignment.Center) {
        BackdropGlow()
        Column(Modifier.width(560.dp), horizontalAlignment = Alignment.Start) {
            androidx.compose.foundation.Image(painterResource(R.drawable.arc_logo), contentDescription = stringResource(R.string.app_name), modifier = Modifier.height(48.dp).aspectRatio(136.02f / 36.2f))
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.server_title), style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.server_body), style = MaterialTheme.typography.bodyLarge, color = ArcColors.Muted)
            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AddressField(state.address, onEdit, onConnect, Modifier.weight(1f).focusRequester(fieldFocus))
                Button(onClick = onConnect, enabled = !state.checking) {
                    Text(stringResource(if (state.checking) R.string.server_checking else R.string.server_connect))
                }
            }
            Spacer(Modifier.height(14.dp))
            val problem = state.problem
            when {
                state.checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Spinner(size = 18.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.server_checking), style = MaterialTheme.typography.bodyMedium, color = ArcColors.Muted)
                }
                problem != null -> Text(
                    when (problem) {
                        ServerProblem.Invalid -> stringResource(R.string.server_invalid)
                        ServerProblem.NotArc -> stringResource(R.string.server_not_arc)
                        is ServerProblem.Failed -> problem.error.text()
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = ArcColors.Error,
                )
                else -> Text(stringResource(R.string.server_port_note), style = MaterialTheme.typography.bodyMedium, color = ArcColors.Subtle)
            }
            if (state.canScan) {
                Spacer(Modifier.height(28.dp))
                FoundOnNetwork(state, onChoose, onScan, firstFound)
            }
        }
    }
    LaunchedEffect(Unit) { fieldFocus.requestWhenReady() }
    // The first server found takes the focus, unless an address is being typed: one press of OK
    // connects (as Plex lists its servers).
    val firstId = state.found.firstOrNull()?.id
    LaunchedEffect(firstId) { if (firstId != null && state.address.isEmpty()) firstFound.requestWhenReady() }
}

@Composable
private fun AddressField(value: String, onEdit: (String) -> Unit, onDone: () -> Unit, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    BasicTextField(
        value = value,
        onValueChange = onEdit,
        singleLine = true,
        textStyle = MaterialTheme.typography.titleLarge.copy(color = ArcColors.Foreground),
        cursorBrush = SolidColor(ArcColors.Accent),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onGo = { onDone() }),
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(if (focused) ArcColors.Surface2 else ArcColors.Surface, shape)
                    .border(BorderStroke(2.dp, if (focused) ArcColors.Accent else ArcColors.Surface3), shape)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
            ) {
                if (value.isEmpty()) Text(stringResource(R.string.server_hint), style = MaterialTheme.typography.titleLarge, color = ArcColors.Subtle)
                inner()
            }
        },
    )
}

/** The soft accent light behind sign-in screens. */
@Composable
fun BackdropGlow() {
    org.arcmediaserver.android.ui.profiles.AuthGlow()
}

/**
 * "On your network": the Arc servers found, each a button with its name, address and version;
 * while looking, a spinner; when none answer, a note and Search Again.
 */
@Composable
private fun FoundOnNetwork(
    state: ServerUiState,
    onChoose: (org.arcmediaserver.android.data.FoundServer) -> Unit,
    onScan: () -> Unit,
    firstFound: FocusRequester,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.server_on_network), style = MaterialTheme.typography.titleMedium, color = ArcColors.Foreground)
            if (state.scanning) {
                Spacer(Modifier.width(12.dp))
                Spinner(size = 16.dp)
            }
        }
        state.found.forEachIndexed { i, server ->
            Button(
                onClick = { onChoose(server) },
                enabled = !state.checking,
                modifier = Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(firstFound) else Modifier),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Text(server.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text(
                        stringResource(R.string.server_found_detail, displayAddress(server.address), server.version),
                        style = MaterialTheme.typography.bodySmall, maxLines = 1,
                    )
                }
            }
        }
        if (!state.scanning && state.found.isEmpty()) {
            Text(stringResource(R.string.server_none_found), style = MaterialTheme.typography.bodyMedium, color = ArcColors.Muted)
            Button(onClick = onScan) { Text(stringResource(R.string.server_search_again)) }
        } else if (state.scanning && state.found.isEmpty()) {
            Text(stringResource(R.string.server_looking), style = MaterialTheme.typography.bodyMedium, color = ArcColors.Muted)
        }
    }
}

/** "192.168.1.20" for a found server's "http://192.168.1.20:8099" (Arc's port goes without saying). */
private fun displayAddress(address: String): String = address.removePrefix("http://").removeSuffix(":${org.arcmediaserver.android.data.Discovery.PORT}")

