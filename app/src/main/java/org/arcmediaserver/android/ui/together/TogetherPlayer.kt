package org.arcmediaserver.android.ui.together

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import org.arcmediaserver.android.R
import org.arcmediaserver.android.data.RoomEnd
import org.arcmediaserver.android.data.RoomSync
import org.arcmediaserver.android.data.WatchCode
import org.arcmediaserver.android.data.WatchParticipant
import org.arcmediaserver.android.data.WatchRoom
import org.arcmediaserver.android.ui.components.ArcIcons
import org.arcmediaserver.android.ui.components.PopoverColor
import org.arcmediaserver.android.ui.components.requestWhenReady
import org.arcmediaserver.android.ui.theme.ArcColors

/**
 * What the player shows for Watch Together, as the web player (components/WatchTogether.tsx):
 * the lobby until the room first plays, "Waiting for Sam…" while someone catches up, a short note
 * when someone else pauses, plays or moves the room, and a note when the title isn't available to
 * this profile or the room has ended. [lobby]: the room hasn't started yet. [serverNow] is the room's clock, for the wait's countdown.
 */
@Composable
fun TogetherOverlays(
    room: WatchRoom?,
    lobby: Boolean,
    ended: RoomEnd?,
    code: RoomCode?,
    serverNow: () -> Long,
    onStart: () -> Unit,
    onLeave: () -> Unit,
    onNewCode: () -> Unit,
    /** Invite People… (host), when the room can take invitations. */
    onInvite: (() -> Unit)? = null,
) {
    // A note when someone else changes the shared timeline: "Paused by Jo".
    var before by remember { mutableStateOf<WatchRoom?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val pausedBy = stringResource(R.string.together_paused_by, "%s")
    val playedBy = stringResource(R.string.together_played_by, "%s")
    val movedBy = stringResource(R.string.together_moved_by, "%s")
    LaunchedEffect(room) {
        val after = room ?: run { before = null; return@LaunchedEffect }
        val change = RoomSync.changeBy(before, after)
        before = after
        if (change != null) {
            notice = when (change.first) {
                RoomSync.Change.Paused -> pausedBy
                RoomSync.Change.Played -> playedBy
                RoomSync.Change.Moved -> movedBy
            }.replace("%s", change.second)
            delay(3_000)
            notice = null
        }
    }
    Box(Modifier.fillMaxSize()) {
        when {
            room != null && lobby -> TogetherLobby(room, code?.takeIf { it.roomId == room.id }, onStart, onLeave, onNewCode, onInvite)
            room != null && !room.available -> Pill(stringResource(R.string.together_unavailable), Modifier.align(Alignment.TopCenter))
            room?.state == "waiting" -> WaitingPill(room, serverNow, Modifier.align(Alignment.TopCenter))
            notice != null -> Pill(notice!!, Modifier.align(Alignment.TopCenter))
            room == null && ended != null -> EndedPill(ended, Modifier.align(Alignment.TopCenter))
        }
    }
}

/** A capsule near the top of the picture (the Apple TV design's notices). */
@Composable
private fun Pill(text: String, modifier: Modifier = Modifier, icon: Boolean = true) {
    Row(
        modifier.padding(top = 56.dp).background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(50))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(50))
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon) {
            Icon(ArcIcons.Users, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun EndedPill(ended: RoomEnd, modifier: Modifier) {
    var shown by remember(ended) { mutableStateOf(true) }
    LaunchedEffect(ended) { delay(6_000); shown = false }
    if (shown) Pill(stringResource(if (ended == RoomEnd.Ended) R.string.together_ended_by_host else R.string.together_lost), modifier)
}

/** "Waiting for Sam…" with the seconds left before the room carries on (at most ten). */
@Composable
private fun WaitingPill(room: WatchRoom, serverNow: () -> Long, modifier: Modifier) {
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(room.revision) { while (true) { delay(500); tick++ } }
    val slow = RoomSync.waitingFor(room)
    val seconds = room.waitUntilMs?.let { maxOf(0L, (it - serverNow() + 999) / 1000) }?.takeIf { tick >= 0 } ?: 0
    val text = if (slow.isNotEmpty()) stringResource(R.string.together_waiting_for, slow.joinToString(", ")) else stringResource(R.string.together_waiting)
    Pill(if (seconds > 0) stringResource(R.string.together_waiting_seconds, text, seconds.toInt()) else text, modifier)
}

/**
 * The lobby over the paused player, as the web's TogetherLobby and the Apple TV design: the title,
 * who's here (the host marked), the code to share (host), then Start for the host or "Waiting for
 * Sam to start…". Back leaves, as Leave does.
 */
@Composable
fun TogetherLobby(room: WatchRoom, code: RoomCode?, onStart: () -> Unit, onLeave: () -> Unit, onNewCode: () -> Unit, onInvite: (() -> Unit)? = null) {
    BackHandler(onBack = onLeave)
    val first = remember { FocusRequester() }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(460.dp).background(PopoverColor, RoundedCornerShape(24.dp))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(ArcIcons.Users, contentDescription = null, tint = ArcColors.Foreground, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(stringResource(R.string.together_title), style = MaterialTheme.typography.titleLarge, color = ArcColors.Foreground)
                    room.item?.title?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = ArcColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
            Text(
                pluralStringResource(R.plurals.together_in_room, room.participants.size, room.participants.size).uppercase(),
                style = MaterialTheme.typography.labelSmall, color = ArcColors.Muted,
            )
            Participants(room)
            if (room.hosting && code != null) {
                Column(Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp)).padding(14.dp)) {
                    Text(stringResource(R.string.together_code_hint), style = MaterialTheme.typography.bodySmall, color = ArcColors.Muted)
                    Spacer(Modifier.height(6.dp))
                    Text(WatchCode.format(code.code), style = MaterialTheme.typography.headlineMedium, color = ArcColors.Foreground)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (room.hosting) {
                    LobbyButton(stringResource(R.string.together_start), ArcIcons.Play, Modifier.focusRequester(first), filled = true, onClick = onStart)
                    if (onInvite != null) LobbyButton(stringResource(R.string.together_invite_people), ArcIcons.Users, onClick = onInvite)
                    if (code != null) LobbyButton(stringResource(R.string.together_new_code), ArcIcons.RotateCw, onClick = onNewCode)
                    LobbyButton(stringResource(R.string.together_leave), null, onClick = onLeave)
                } else {
                    Text(
                        stringResource(R.string.together_waiting_for_host, room.hostDisplayName),
                        style = MaterialTheme.typography.bodyMedium, color = ArcColors.Muted, modifier = Modifier.weight(1f),
                    )
                    LobbyButton(stringResource(R.string.together_leave), null, Modifier.focusRequester(first), onClick = onLeave)
                }
            }
        }
    }
    LaunchedEffect(room.hosting) { first.requestWhenReady() }
}

/** Who's in the room: names, the host and "(you)", and whether each is ready. */
@Composable
fun Participants(room: WatchRoom) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        room.participants.forEach { p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (p.id == room.selfId) stringResource(R.string.together_you, p.name) else p.name,
                    style = MaterialTheme.typography.bodyLarge, color = ArcColors.Foreground, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (p.id == room.hostId) {
                    Text(stringResource(R.string.together_host), style = MaterialTheme.typography.labelSmall, color = ArcColors.Muted)
                    Spacer(Modifier.width(10.dp))
                }
                Text(statusOf(p), style = MaterialTheme.typography.labelMedium, color = ArcColors.Muted)
            }
        }
        room.invited.forEach { inv ->
            Row {
                Text(inv.name, style = MaterialTheme.typography.bodyLarge, color = ArcColors.Muted, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.together_status_invited), style = MaterialTheme.typography.labelMedium, color = ArcColors.Muted)
            }
        }
    }
}

@Composable
fun statusOf(p: WatchParticipant): String = stringResource(
    when {
        !p.available -> R.string.together_status_unavailable
        p.state == "playing" -> R.string.together_status_watching
        p.ready -> R.string.together_status_ready
        else -> R.string.together_status_loading
    },
)

@Composable
private fun LobbyButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, modifier: Modifier = Modifier, filled: Boolean = false, onClick: () -> Unit) {
    androidx.tv.material3.Button(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        colors = androidx.tv.material3.ButtonDefaults.colors(
            containerColor = if (filled) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f), contentColor = ArcColors.Foreground,
            focusedContainerColor = Color.White, focusedContentColor = Color.Black,
        ),
        scale = androidx.tv.material3.ButtonDefaults.scale(focusedScale = 1.04f),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}
