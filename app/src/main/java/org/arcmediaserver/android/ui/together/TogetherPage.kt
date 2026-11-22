package org.arcmediaserver.android.ui.together

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import org.arcmediaserver.android.AppContainer
import org.arcmediaserver.android.R
import org.arcmediaserver.android.data.MediaCard
import org.arcmediaserver.android.data.WatchCode
import org.arcmediaserver.android.data.cardFor
import org.arcmediaserver.android.ui.components.ArcIcons
import org.arcmediaserver.android.ui.components.MediaCardView
import org.arcmediaserver.android.ui.components.Popover
import org.arcmediaserver.android.ui.components.PopoverItem
import org.arcmediaserver.android.ui.components.PopoverPlace
import org.arcmediaserver.android.ui.components.Shelf
import org.arcmediaserver.android.ui.components.requestWhenReady
import org.arcmediaserver.android.ui.theme.ArcColors

private const val ROOM_KEY = "room:"

/**
 * The rooms as cards (Home's Watch Together row, and the page's): the title's artwork, "You're
 * hosting" or "Hosted by Sam" under it, and "Watching now" while the room plays, as the web's
 * WatchTogetherRow. Rooms whose title didn't load are left out.
 */
fun roomCards(rooms: List<RoomCard>, container: AppContainer): List<MediaCard> = rooms.mapNotNull { r ->
    val item = r.item ?: return@mapNotNull null
    cardFor(item, key = ROOM_KEY + r.room.id).copy(
        sub = if (r.room.hosting) container.text(R.string.together_youre_hosting) else container.text(R.string.together_hosted_by, r.room.hostName),
        tag = if (r.room.state == "playing") container.text(R.string.together_card_playing) else null,
        progress = null,
    )
}

/** The room a Watch Together card stands for. */
fun MediaCard.roomId(): String? = key.takeIf { it.startsWith(ROOM_KEY) }?.removePrefix(ROOM_KEY)

/**
 * A room's choices (a Watch Together card chosen): Join, or Return to a room this TV is in, then
 * Decline an invitation or Cancel a room this profile made; who's there and who's invited above.
 */
@Composable
fun RoomMenu(container: AppContainer, roomId: String, onClose: () -> Unit) {
    val rooms by container.together.rooms.collectAsState()
    val card = rooms.firstOrNull { it.room.id == roomId }
    val scope = rememberCoroutineScope()
    var failed by remember { mutableStateOf(false) }
    if (card == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val room = card.room
    val first = remember { FocusRequester() }
    val body = listOfNotNull(
        if (room.hosting) stringResource(R.string.together_youre_hosting) else stringResource(R.string.together_hosted_by, room.hostName),
        room.participants.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.together_watching_names, it.joinToString(", ")) },
        room.invited.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.together_invited_names, it.joinToString(", ")) },
        if (failed) stringResource(R.string.together_join_failed) else null,
    ).joinToString("\n")
    Popover(anchor = null, place = PopoverPlace.Center, onClose = onClose, title = card.item?.title ?: room.item.title, body = body) {
        PopoverItem(stringResource(if (room.joined) R.string.together_return else R.string.together_join_action), ArcIcons.Users, focus = first) {
            scope.launch { if (container.together.accept(room.id)) onClose() else failed = true }
        }
        PopoverItem(stringResource(if (room.hosting) R.string.together_cancel_room else R.string.together_decline), ArcIcons.Close) {
            scope.launch { container.together.dismiss(room.id) }
            onClose()
        }
    }
    LaunchedEffect(Unit) { first.requestWhenReady() }
}

/**
 * Join Watch Together, from the profile at the top of the sidebar (as the web's profile menu):
 * the code the host shared, typed with the remote or the TV's keyboard. Joining opens the player
 * on the room's lobby; rooms this profile made or was invited to are on Home's Watch Together row.
 */
@Composable
fun JoinTogetherDialog(container: AppContainer, onClose: () -> Unit) {
    val box = remember { FocusRequester() }
    org.arcmediaserver.android.ui.details.Overlay(620.dp, onBack = onClose) {
        JoinWithCode(container, onJoined = onClose, focus = box)
    }
    LaunchedEffect(Unit) { box.requestWhenReady() }
}

/** The code box and Join, with the web's messages when a code doesn't work. */
@Composable
private fun JoinWithCode(container: AppContainer, modifier: Modifier = Modifier, onJoined: () -> Unit = {}, focus: FocusRequester? = null) {
    var code by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<JoinProblem?>(null) }
    var joining by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val ready = WatchCode.normalize(code).length == 8
    val join = {
        if (ready && !joining) {
            joining = true
            problem = null
            keyboard?.hide()
            scope.launch {
                problem = container.together.join(WatchCode.normalize(code))
                joining = false
                if (problem == null) {
                    code = ""
                    onJoined()
                }
            }
        }
    }
    Column(modifier.widthIn(max = 560.dp)) {
        Text(stringResource(R.string.together_join_title), style = MaterialTheme.typography.titleLarge, color = ArcColors.Foreground)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.together_join_body), style = MaterialTheme.typography.bodyMedium, color = ArcColors.Muted)
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            var focused by remember { mutableStateOf(false) }
            BasicTextField(
                value = code,
                // Typed as the host reads it: letters and digits, the dash added after four.
                onValueChange = { typed -> code = WatchCode.format(WatchCode.normalize(typed).take(8)); problem = null },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge.copy(color = if (focused) Color.Black else ArcColors.Foreground),
                cursorBrush = SolidColor(if (focused) Color.Black else ArcColors.Foreground),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onGo = { join() }),
                modifier = Modifier.width(260.dp).then(if (focus != null) Modifier.focusRequester(focus) else Modifier).onFocusChanged { focused = it.isFocused },
                decorationBox = { inner ->
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(if (focused) Color.White else Color.White.copy(alpha = 0.1f))
                            .padding(horizontal = 22.dp, vertical = 12.dp),
                    ) {
                        if (code.isEmpty()) Text(stringResource(R.string.together_code_placeholder), style = MaterialTheme.typography.titleLarge, color = if (focused) Color(0xFF777777) else ArcColors.Subtle)
                        inner()
                    }
                },
            )
            Spacer(Modifier.width(14.dp))
            Button(
                onClick = { join() },
                enabled = ready && !joining,
                colors = ButtonDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.12f), contentColor = ArcColors.Foreground,
                    focusedContainerColor = Color.White, focusedContentColor = Color.Black,
                ),
            ) { Text(stringResource(R.string.together_join_action)) }
        }
        problem?.let { p ->
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(
                    when (p) {
                        JoinProblem.CodeNotFound -> R.string.together_code_not_found
                        JoinProblem.TooManyTries -> R.string.together_too_many_tries
                        JoinProblem.Failed -> R.string.together_join_failed
                    },
                ),
                style = MaterialTheme.typography.bodyMedium, color = ArcColors.Foreground,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
