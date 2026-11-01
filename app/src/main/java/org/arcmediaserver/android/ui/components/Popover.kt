package org.arcmediaserver.android.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import org.arcmediaserver.android.ui.theme.ArcColors

/** Where a [Popover] opens: beside its opener (a card's menu), above it (the player's lists) or below it (a library's Sort and Filters). */
enum class PopoverPlace { Side, Above, Below, Center }

/** The floating list's colour: a near-opaque grey over the page, as the Apple TV app's menus. */
val PopoverColor = Color(0xF52C2C34)

/**
 * The Apple TV app's floating menu: a rounded panel of choices (each name on the left, its icon on
 * the right), placed beside [anchor] (its top level with the opener's) or above it, kept on screen.
 * The focus stays inside until Back or a choice closes it. The TV web app's Popover is the same.
 */
@Composable
fun Popover(
    anchor: Rect?,
    onClose: () -> Unit,
    place: PopoverPlace = PopoverPlace.Side,
    title: String? = null,
    body: String? = null,
    width: Dp = 300.dp,
    shade: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onClose)
    val density = LocalDensity.current
    val margin = with(density) { 24.dp.roundToPx() }
    val gap = with(density) { 18.dp.roundToPx() }
    // The anchor is in root coordinates; this layer may not start at the root's corner.
    var origin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    Box(
        Modifier.fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() }
            .then(if (shade) Modifier.background(Color.Black.copy(alpha = 0.45f)) else Modifier),
    ) {
        Layout(
            content = {
                Column(
                    Modifier
                        .width(width)
                        .shadow(30.dp, RoundedCornerShape(18.dp))
                        .background(PopoverColor, RoundedCornerShape(18.dp))
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(18.dp))
                        .padding(7.dp)
                        .focusProperties { onExit = { cancelFocusChange() } }
                        .focusGroup(),
                ) {
                    if (title != null) {
                        Text(
                            title, style = MaterialTheme.typography.labelLarge, color = ArcColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 11.dp, end = 11.dp, top = 5.dp, bottom = 6.dp),
                        )
                    }
                    if (body != null) {
                        Text(body, style = MaterialTheme.typography.bodySmall, color = ArcColors.Muted, modifier = Modifier.padding(start = 11.dp, end = 11.dp, bottom = 6.dp))
                    }
                    content()
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) { measurables, constraints ->
            val panel = measurables.first().measure(Constraints(maxWidth = constraints.maxWidth - 2 * margin, maxHeight = constraints.maxHeight - 2 * margin))
            val screenW = constraints.maxWidth
            val screenH = constraints.maxHeight
            var x: Int
            var y: Int
            val anchor = anchor?.translate(-origin.x, -origin.y)
            when {
                anchor == null || place == PopoverPlace.Center -> {
                    x = (screenW - panel.width) / 2
                    y = (screenH - panel.height) / 2
                }
                place == PopoverPlace.Below -> {
                    x = anchor.right.toInt() - panel.width
                    y = anchor.bottom.toInt() + gap
                }
                place == PopoverPlace.Above -> {
                    x = anchor.right.toInt() - panel.width
                    y = anchor.top.toInt() - gap - panel.height
                }
                else -> {
                    // Beside the opener, on whichever side has room, its top level with the opener's.
                    x = anchor.right.toInt() + gap
                    if (x + panel.width > screenW - margin) x = anchor.left.toInt() - gap - panel.width
                    y = anchor.top.toInt()
                }
            }
            x = x.coerceIn(margin, (screenW - margin - panel.width).coerceAtLeast(margin))
            y = y.coerceIn(margin, (screenH - margin - panel.height).coerceAtLeast(margin))
            layout(screenW, screenH) { panel.place(x, y) }
        }
    }
}

/** A scrolling list of choices inside a [Popover], for long lists (languages). */
@Composable
fun PopoverList(maxHeight: Dp = 340.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState()), content = content)
}

/**
 * One choice: its name (and a second line) on the left, its icon on the right; white when focused.
 * [focus] is given to the choice that should have the focus when the list opens.
 */
@Composable
fun PopoverItem(
    label: String,
    icon: ImageVector? = null,
    detail: String? = null,
    focus: FocusRequester? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(if (detail != null) 46.dp else 38.dp).then(if (focus != null) Modifier.focusRequester(focus) else Modifier),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(11.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = ArcColors.Foreground.copy(alpha = 0.85f),
            focusedContainerColor = ArcColors.Foreground,
            focusedContentColor = ArcColors.Background,
        ),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (detail != null) {
                    Text(detail, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = LocalContentColor.current.copy(alpha = 0.6f))
                }
            }
            Spacer(Modifier.width(10.dp))
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) else Spacer(Modifier.size(16.dp))
        }
    }
}

/** A label and value line inside a [Popover] (the player's Info). */
@Composable
fun PopoverStat(label: String, value: String) {
    Row(Modifier.padding(horizontal = 11.dp, vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = ArcColors.Muted, modifier = Modifier.width(76.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, color = ArcColors.Foreground)
    }
}

/** Focuses [first] once the popover is on screen. */
@Composable
fun FocusOnOpen(first: FocusRequester) {
    LaunchedEffect(Unit) { first.requestWhenReady() }
}
