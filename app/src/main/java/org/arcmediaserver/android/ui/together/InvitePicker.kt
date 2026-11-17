package org.arcmediaserver.android.ui.together

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.arcmediaserver.android.AppContainer
import org.arcmediaserver.android.R
import org.arcmediaserver.android.data.Item
import org.arcmediaserver.android.data.ListedRoom
import org.arcmediaserver.android.data.WatchPerson
import org.arcmediaserver.android.ui.components.ArcIcons
import org.arcmediaserver.android.ui.components.Popover
import org.arcmediaserver.android.ui.components.PopoverItem
import org.arcmediaserver.android.ui.components.PopoverList
import org.arcmediaserver.android.ui.components.PopoverPlace
import org.arcmediaserver.android.ui.components.Spinner
import org.arcmediaserver.android.ui.components.requestWhenReady

/** Who the picker invites for: a new room for a title, or more people to the room this TV hosts. */
sealed interface InviteTarget {
    data class NewRoom(val item: Item, val startMs: Long) : InviteTarget
    data class MoreTo(val itemId: String?) : InviteTarget
}

/**
 * The web's invite dialog (components/WatchTogether.tsx InviteDialog) as a TV menu: the people this
 * profile may invite, each ticked or not with OK ("Can't watch this title" can't be chosen), then
 * Done. For a new room, Done makes it, waiting on everyone's Home row, and offers Join Now; Use a
 * Code Instead starts a room with a code, as before. Inside a room, Done invites more people.
 */
@Composable
fun InvitePicker(container: AppContainer, target: InviteTarget, onUseCode: (() -> Unit)?, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var people by remember { mutableStateOf<List<WatchPerson>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var loads by remember { mutableIntStateOf(0) }
    var chosen by remember { mutableStateOf(setOf<String>()) }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<Int?>(null) }
    var made by remember { mutableStateOf<ListedRoom?>(null) }
    val itemId = when (target) {
        is InviteTarget.NewRoom -> target.item.id
        is InviteTarget.MoreTo -> target.itemId
    }
    LaunchedEffect(loads) {
        failed = false
        val list = container.together.people(itemId)
        if (list == null) failed = true else people = list
    }
    val room = made
    if (room != null) {
        // Made: the room waits on Home; Join Now goes in at once (and opens the lobby).
        RoomMade(container, room, onClose)
        return
    }
    val first = remember { FocusRequester() }
    val title = (target as? InviteTarget.NewRoom)?.item?.title
    val body = listOfNotNull(
        if (title != null) stringResource(R.string.together_invite_body, title) else stringResource(R.string.together_invite_more_body),
        problem?.let { stringResource(it) },
    ).joinToString("\n")
    Popover(anchor = null, place = PopoverPlace.Center, onClose = onClose, title = stringResource(R.string.together_title), body = body, width = 380.dp) {
        val list = people
        when {
            failed -> {
                PopoverItem(stringResource(R.string.together_people_failed), ArcIcons.RotateCw, focus = first) { loads++ }
            }
            list == null -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { Spinner(size = 28.dp) }
            list.isEmpty() -> androidx.tv.material3.Text(
                stringResource(R.string.together_nobody_to_invite),
                style = androidx.tv.material3.MaterialTheme.typography.bodySmall,
                color = org.arcmediaserver.android.ui.theme.ArcColors.Muted,
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            )
            else -> PopoverList(maxHeight = 300.dp) {
                list.forEachIndexed { i, p ->
                    val on = p.id in chosen
                    PopoverItem(
                        p.name,
                        icon = if (on) ArcIcons.Check else null,
                        detail = stringResource(
                            when {
                                !p.canWatch -> R.string.together_cant_watch
                                p.friend -> R.string.together_friend
                                else -> R.string.together_household
                            },
                        ),
                        focus = if (i == 0) first else null,
                    ) {
                        if (p.canWatch) chosen = if (on) chosen - p.id else chosen + p.id
                    }
                }
            }
        }
        if (chosen.isNotEmpty()) {
            PopoverItem(stringResource(if (busy) R.string.together_inviting else R.string.together_done), ArcIcons.Users) {
                if (busy) return@PopoverItem
                busy = true
                problem = null
                scope.launch {
                    when (target) {
                        is InviteTarget.NewRoom -> {
                            val (result, room) = container.together.invite(target.item, target.startMs, chosen.toList())
                            if (room != null) made = room else problem = problemFor(result)
                        }
                        is InviteTarget.MoreTo -> {
                            val result = container.together.inviteMore(chosen.toList())
                            if (result == InviteResult.Done) onClose() else problem = problemFor(result)
                        }
                    }
                    busy = false
                }
            }
        }
        if (onUseCode != null) {
            PopoverItem(stringResource(R.string.together_use_code), ArcIcons.Play, focus = if (list.isNullOrEmpty() && !failed) first else null) { onUseCode() }
        }
    }
    LaunchedEffect(people == null, failed) { if (people != null || failed) first.requestWhenReady() }
}

private fun problemFor(result: InviteResult): Int? = when (result) {
    InviteResult.Done -> null
    InviteResult.TooManyRooms -> R.string.together_too_many_rooms
    InviteResult.Failed -> R.string.together_invite_failed
}

/** "Your room for Dune is ready. It's on Home under Watch Together." with Join Now and Not Now. */
@Composable
private fun RoomMade(container: AppContainer, room: ListedRoom, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    var failed by remember { mutableStateOf(false) }
    val body = listOfNotNull(
        stringResource(R.string.together_room_made, room.item.title),
        if (failed) stringResource(R.string.together_join_failed) else null,
    ).joinToString("\n")
    Popover(anchor = null, place = PopoverPlace.Center, onClose = onClose, title = stringResource(R.string.together_title), body = body, width = 380.dp) {
        PopoverItem(stringResource(R.string.together_join_now), ArcIcons.Users, focus = first) {
            scope.launch { if (container.together.accept(room.id)) onClose() else failed = true }
        }
        PopoverItem(stringResource(R.string.together_not_now), null) { onClose() }
    }
    LaunchedEffect(Unit) { first.requestWhenReady() }
}
