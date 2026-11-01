package org.arcmediaserver.android.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.arcmediaserver.android.AppContainer
import org.arcmediaserver.android.R
import org.arcmediaserver.android.data.ArcLog
import org.arcmediaserver.android.data.Prefs
import org.arcmediaserver.android.data.SubtitleStyle
import org.arcmediaserver.android.ui.components.toUiError
import org.arcmediaserver.android.playback.languageName
import org.arcmediaserver.android.ui.arcViewModel
import org.arcmediaserver.android.ui.components.ArcIcons
import org.arcmediaserver.android.ui.components.LoadState
import org.arcmediaserver.android.ui.components.Loaded
import org.arcmediaserver.android.ui.components.Popover
import org.arcmediaserver.android.ui.components.PopoverItem
import org.arcmediaserver.android.ui.components.PopoverList
import org.arcmediaserver.android.ui.components.requestWhenReady
import org.arcmediaserver.android.ui.theme.ArcColors

data class SettingsUiState(
    val prefs: LoadState<Prefs> = LoadState.Loading,
    val serverName: String? = null,
    /** A change Arc couldn't save (it was put back). */
    val failed: Boolean = false,
)

/** The profile's playback and subtitle preferences (GET/PATCH /me/prefs), shared with every Arc app. */
class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState(serverName = container.session.serverName))
    val state: StateFlow<SettingsUiState> = _state

    init { load() }

    fun load() {
        val api = container.api() ?: return
        _state.update { it.copy(prefs = LoadState.Loading) }
        viewModelScope.launch {
            val result = try {
                LoadState.Ready(api.prefs())
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                ArcLog.Android.warn("settings failed to load: ${e.javaClass.simpleName}")
                LoadState.Failed(e.toUiError())
            }
            _state.update { it.copy(prefs = result) }
        }
    }

    /** Shows [after] at once and saves [changes]; if Arc can't, puts [before] back. */
    fun change(before: Prefs, after: Prefs, changes: JsonObject) {
        val api = container.api() ?: return
        _state.update { it.copy(prefs = LoadState.Ready(after), failed = false) }
        viewModelScope.launch {
            try {
                api.updatePrefs(changes)
                ArcLog.Android.info("settings: saved ${changes.keys.joinToString()}")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                ArcLog.Android.warn("settings: saving ${changes.keys.joinToString()} failed: ${e.javaClass.simpleName}")
                _state.update { it.copy(prefs = LoadState.Ready(before), failed = true) }
            }
        }
    }

    fun signOut(done: () -> Unit) = container.signOut(done)
}

@Composable
fun SettingsRoute(onSignedOut: () -> Unit, onSwitchServer: () -> Unit) {
    val vm = arcViewModel { SettingsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    SettingsContent(
        state,
        onChange = vm::change,
        onRetry = vm::load,
        onSwitchServer = { vm.signOut(onSwitchServer) },
        onSignOut = { vm.signOut(onSignedOut) },
    )
}

/** The languages offered for audio and subtitles (as the TV web app's Settings). */
private val Languages = listOf(
    "eng", "fra", "deu", "spa", "ita", "por", "nld", "swe", "nor", "dan", "fin", "pol", "ces",
    "hun", "ell", "tur", "rus", "ukr", "ara", "heb", "hin", "jpn", "kor", "zho", "tha", "vie",
)

private data class Choice(val value: String, val label: String)

private class Picking(val title: String, val choices: List<Choice>, val current: String, val pick: (String) -> Unit)

/**
 * Settings, as the Apple TV app's: grouped rows, each with its value on the right; a choice opens
 * the floating list. Playback and Subtitles belong to the profile; This TV to the device.
 */
@Composable
fun SettingsContent(
    state: SettingsUiState,
    onChange: (before: Prefs, after: Prefs, changes: JsonObject) -> Unit,
    onRetry: () -> Unit,
    onSwitchServer: () -> Unit,
    onSignOut: () -> Unit,
) {
    var picking by remember { mutableStateOf<Picking?>(null) }
    val first = remember { FocusRequester() }
    Box(Modifier.fillMaxSize()) {
        Loaded(state.prefs, onRetry) { p ->
            val on = stringResource(R.string.settings_on)
            val off = stringResource(R.string.settings_off)
            val style = p.subtitleStyle
            val languageChoices = Languages.map { Choice(it, languageName(it) ?: it) }
            val audio = listOf(Choice("", stringResource(R.string.settings_file_default))) + languageChoices
            val subtitles = listOf(Choice("", stringResource(R.string.settings_none))) + languageChoices
            val modes = listOf(
                Choice("", stringResource(R.string.settings_mode_last)),
                Choice("foreign", stringResource(R.string.settings_mode_foreign)),
                Choice("always", stringResource(R.string.settings_mode_always)),
                Choice("manual", stringResource(R.string.settings_mode_manual)),
            )
            val sizes = listOf(
                Choice("medium", stringResource(R.string.settings_size_medium)),
                Choice("small", stringResource(R.string.settings_size_small)),
                Choice("large", stringResource(R.string.settings_size_large)),
                Choice("huge", stringResource(R.string.settings_size_huge)),
            )
            val colors = listOf(Choice("white", stringResource(R.string.settings_color_white)), Choice("yellow", stringResource(R.string.settings_color_yellow)))
            val backgrounds = listOf(
                Choice("box", stringResource(R.string.settings_background_box)),
                Choice("solid", stringResource(R.string.settings_background_solid)),
                Choice("none", stringResource(R.string.settings_background_none)),
            )
            val positions = listOf(Choice("bottom", stringResource(R.string.settings_position_bottom)), Choice("top", stringResource(R.string.settings_position_top)))
            fun nameOf(list: List<Choice>, v: String) = (list.firstOrNull { it.value == v } ?: list.first()).label
            fun setStyle(next: SubtitleStyle) = onChange(
                p, p.copy(subtitleStyle = next),
                buildJsonObject {
                    put("subtitle_style", buildJsonObject {
                        put("size", next.size); put("color", next.color); put("background", next.background); put("position", next.position)
                    })
                },
            )
            fun setString(key: String, value: String, after: Prefs) = onChange(p, after, JsonObject(mapOf(key to JsonPrimitive(value))))

            @Composable
            fun ChoiceRow(@StringRes label: Int, list: List<Choice>, current: String, modifier: Modifier = Modifier, done: (String) -> Unit) {
                val title = stringResource(label)
                SettingsRow(title, nameOf(list, current), modifier) { picking = Picking(title, list, current) { v -> picking = null; done(v) } }
            }

            LazyColumn(contentPadding = PaddingValues(start = 48.dp, top = 36.dp, bottom = 48.dp)) {
                item { Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(bottom = 4.dp)) }
                item { Heading(R.string.settings_playback) }
                item {
                    SettingsRow(stringResource(R.string.settings_auto_play), if (p.autoPlay) on else off, Modifier.focusRequester(first)) {
                        onChange(p, p.copy(autoPlay = !p.autoPlay), buildJsonObject { put("auto_play", !p.autoPlay) })
                    }
                }
                item {
                    SettingsRow(stringResource(R.string.settings_auto_skip), if (p.autoSkip) on else off) {
                        onChange(p, p.copy(autoSkip = !p.autoSkip), buildJsonObject { put("auto_skip", !p.autoSkip) })
                    }
                }
                item { ChoiceRow(R.string.settings_audio_language, audio, p.audioLanguage) { setString("audio_language", it, p.copy(audioLanguage = it)) } }
                item { Heading(R.string.settings_subtitles) }
                item { ChoiceRow(R.string.settings_subtitle_mode, modes, p.subtitleMode) { setString("subtitle_mode", it, p.copy(subtitleMode = it)) } }
                item { ChoiceRow(R.string.settings_subtitle_language, subtitles, p.subtitleLanguage) { setString("subtitle_language", it, p.copy(subtitleLanguage = it)) } }
                item { ChoiceRow(R.string.settings_subtitle_size, sizes, style.size.ifEmpty { "medium" }) { setStyle(style.copy(size = it)) } }
                item { ChoiceRow(R.string.settings_subtitle_color, colors, style.color.ifEmpty { "white" }) { setStyle(style.copy(color = it)) } }
                item { ChoiceRow(R.string.settings_subtitle_background, backgrounds, style.background.ifEmpty { "box" }) { setStyle(style.copy(background = it)) } }
                item { ChoiceRow(R.string.settings_subtitle_position, positions, style.position.ifEmpty { "bottom" }) { setStyle(style.copy(position = it)) } }
                item { Note(stringResource(if (state.failed) R.string.settings_save_failed else R.string.settings_profile_note), warn = state.failed) }
                item { Heading(R.string.settings_this_tv) }
                item { SettingsRow(stringResource(R.string.settings_server), state.serverName.orEmpty(), onClick = onSwitchServer) }
                item { SettingsRow(stringResource(R.string.settings_sign_out), "", onClick = onSignOut) }
                item {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }
                    SettingsRow(stringResource(R.string.settings_version), version) {}
                }
                item { Note(stringResource(R.string.settings_server_note)) }
            }
            LaunchedEffect(Unit) { first.requestWhenReady() }
        }
        picking?.let { pk ->
            val current = remember(pk) { FocusRequester() }
            Popover(anchor = null, onClose = { picking = null }, title = pk.title, width = 340.dp) {
                PopoverList {
                    pk.choices.forEach { c ->
                        val chosen = c.value == pk.current
                        PopoverItem(c.label, if (chosen) ArcIcons.Check else null, focus = if (chosen) current else null) { pk.pick(c.value) }
                    }
                }
            }
            LaunchedEffect(pk) { current.requestWhenReady() }
        }
    }
}

@Composable
private fun Heading(@StringRes text: Int) {
    Text(
        stringResource(text), style = MaterialTheme.typography.titleSmall, color = ArcColors.Muted,
        modifier = Modifier.padding(start = 12.dp, top = 18.dp, bottom = 6.dp),
    )
}

@Composable
private fun Note(text: String, warn: Boolean = false) {
    Text(
        text, style = MaterialTheme.typography.bodySmall, color = if (warn) ArcColors.Warning else ArcColors.Muted,
        modifier = Modifier.padding(start = 12.dp, top = 4.dp).widthIn(max = 500.dp),
    )
}

/** One setting: its name on the left, its value on the right; white when focused. */
@Composable
private fun SettingsRow(label: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.padding(bottom = 4.dp).width(550.dp).height(38.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.06f),
            contentColor = ArcColors.Foreground,
            focusedContainerColor = ArcColors.Foreground,
            focusedContentColor = ArcColors.Background,
        ),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (value.isNotEmpty()) {
                Spacer(Modifier.width(12.dp))
                Text(value, style = MaterialTheme.typography.bodyMedium, color = LocalContentColor.current.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
