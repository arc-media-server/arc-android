package org.arcmediaserver.android.ui.mobile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.arcmediaserver.android.R
import org.arcmediaserver.android.data.Prefs
import org.arcmediaserver.android.ui.arcViewModel
import org.arcmediaserver.android.ui.components.ArcIcons
import org.arcmediaserver.android.ui.components.LoadState
import org.arcmediaserver.android.ui.profiles.Avatar
import org.arcmediaserver.android.ui.settings.SettingsUiState
import org.arcmediaserver.android.ui.settings.SettingsViewModel
import org.arcmediaserver.android.ui.theme.ArcColors

@Composable
fun MobileSettingsRoute(onBack: () -> Unit, onSwitchProfile: () -> Unit, onSignedOut: () -> Unit, onSwitchServer: () -> Unit) {
    val vm = arcViewModel { SettingsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    MobileSettingsScreen(
        state,
        onBack = onBack,
        onChange = vm::change,
        onRetry = vm::load,
        onSwitchProfile = onSwitchProfile,
        onSwitchServer = { vm.signOut(onSwitchServer) },
        onSignOut = { vm.signOut(onSignedOut) },
    )
}

/**
 * The profile and Settings, as Android's settings lists: who's watching (Switch Profile), the
 * profile's playback preferences (the same ones the TV and web change: they follow the profile
 * everywhere), the server, and About. Settings only a TV has (frame rate, passthrough) aren't here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileSettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onChange: (Prefs, Prefs, kotlinx.serialization.json.JsonObject) -> Unit,
    onRetry: () -> Unit,
    onSwitchProfile: () -> Unit,
    onSwitchServer: () -> Unit,
    onSignOut: () -> Unit,
) {
    val context = LocalContext.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }
    Scaffold(
        containerColor = ArcColors.Background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_settings)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(ArcIcons.ArrowLeft, contentDescription = stringResource(R.string.mobile_back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ArcColors.Background),
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            state.profile?.let { profile ->
                item {
                    Row(
                        profile.name,
                        stringResource(R.string.nav_switch_profile),
                        leading = { Avatar(profile, 40.dp) },
                        onClick = onSwitchProfile,
                    )
                    HorizontalDivider()
                }
            }
            item { Heading(stringResource(R.string.settings_playback)) }
            when (val p = state.prefs) {
                LoadState.Loading -> item { Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                is LoadState.Failed -> item { MobileError(p.error, onRetry) }
                is LoadState.Ready -> {
                    val prefs = p.value
                    item { Toggle(stringResource(R.string.settings_auto_play), prefs.autoPlay) { onChange(prefs, prefs.copy(autoPlay = it), buildJsonObject { put("auto_play", it) }) } }
                    item { Toggle(stringResource(R.string.settings_auto_skip), prefs.autoSkip) { onChange(prefs, prefs.copy(autoSkip = it), buildJsonObject { put("auto_skip", it) }) } }
                    item { Toggle(stringResource(R.string.settings_hide_spoilers), prefs.hideSpoilers) { onChange(prefs, prefs.copy(hideSpoilers = it), buildJsonObject { put("hide_spoilers", it) }) } }
                    item { Toggle(stringResource(R.string.settings_show_missing), prefs.showMissingEpisodes) { onChange(prefs, prefs.copy(showMissingEpisodes = it), buildJsonObject { put("show_missing_episodes", it) }) } }
                    item { Text(stringResource(R.string.settings_profile_note), style = MaterialTheme.typography.bodySmall, color = ArcColors.Muted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
                }
            }
            if (state.failed) item { Text(stringResource(R.string.settings_save_failed), style = MaterialTheme.typography.bodyMedium, color = ArcColors.Error, modifier = Modifier.padding(16.dp)) }
            item { HorizontalDivider(); Heading(stringResource(R.string.settings_server)) }
            item { Row(state.health?.name?.takeIf { it.isNotBlank() } ?: state.serverName ?: state.address.orEmpty(), state.address, onClick = onSwitchServer, trailing = stringResource(R.string.mobile_settings_change)) }
            item { Row(stringResource(R.string.nav_sign_out), stringResource(R.string.mobile_settings_sign_out_note), onClick = onSignOut) }
            item { HorizontalDivider(); Heading(stringResource(R.string.settings_about)) }
            item { Row(stringResource(R.string.mobile_about_app_name), stringResource(R.string.about_version, version)) }
            state.health?.version?.let { v -> item { Row(stringResource(R.string.about_arc_version), v) } }
            item { Text(stringResource(R.string.about_credit), style = MaterialTheme.typography.bodySmall, color = ArcColors.Muted, modifier = Modifier.padding(16.dp)) }
        }
    }
}

@Composable
private fun Heading(text: String) =
    Text(text, style = MaterialTheme.typography.titleSmall, color = ArcColors.Muted, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp))

@Composable
private fun Row(title: String, sub: String?, leading: (@Composable () -> Unit)? = null, trailing: String? = null, onClick: (() -> Unit)? = null) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = sub?.takeIf { it.isNotBlank() }?.let { { Text(it) } },
        leadingContent = leading,
        trailingContent = trailing?.let { { Text(it, style = MaterialTheme.typography.labelLarge) } },
        colors = ListItemDefaults.colors(containerColor = ArcColors.Background),
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    )
}

@Composable
private fun Toggle(title: String, on: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = {
            Switch(
                checked = on, onCheckedChange = null,
                colors = SwitchDefaults.colors(checkedThumbColor = ArcColors.OnAccent, checkedTrackColor = ArcColors.Foreground),
            )
        },
        colors = ListItemDefaults.colors(containerColor = ArcColors.Background),
        modifier = Modifier.clickable { onChange(!on) },
    )
}
