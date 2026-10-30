package org.arcmediaserver.android.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Scrolls only as far as needed to show the focused element, keeping [margin] clear before it
 * and [after] after it (room for a card's caption, which isn't part of the focused card).
 * Compose for TV's default instead moves whatever has focus to a pivot a third of the way into the
 * list, which pushes headers and captions out of view on Arc's screens. Without the margin, coming
 * back to a row's first card lined it up with the very edge rather than the row's own padding.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun minimalBringIntoView(margin: Dp = 48.dp, after: Dp = margin): BringIntoViewSpec {
    val density = LocalDensity.current
    val px = with(density) { margin.toPx() }
    val afterPx = with(density) { after.toPx() }
    return remember(px, afterPx) {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
                val leading = offset - px
                val trailing = offset + size + afterPx
                return when {
                    leading >= 0f && trailing <= containerSize -> 0f
                    leading < 0f || size + px + afterPx > containerSize -> leading
                    else -> trailing - containerSize
                }
            }
        }
    }
}

/**
 * For a grid of cards with captions, as Plex's library: scrolls a whole row at a time, so the
 * screen always shows whole rows. Moving down past the bottom row brings the next row up into its
 * place; moving up past the top brings the row above in at the top. [top] is the grid's padding
 * above the first row, [below] what follows each card (its caption and the gap to the next row).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rowsBringIntoView(top: Dp, below: Dp): BringIntoViewSpec {
    val density = LocalDensity.current
    val topPx = with(density) { top.toPx() }
    val belowPx = with(density) { below.toPx() }
    return remember(topPx, belowPx) {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = when {
                // Above the top: this row goes to the top.
                offset < topPx -> offset - topPx
                // Its caption past the bottom: this row goes where the bottom row was, one row down
                // from the top, so the row above it shows whole too.
                offset + size + belowPx > containerSize -> offset - (topPx + size + belowPx)
                else -> 0f
            }
        }
    }
}
