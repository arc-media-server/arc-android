package org.arcmediaserver.android.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Scrolls only as far as needed to show the focused element, keeping [margin] clear beside it.
 * Compose for TV's default instead moves whatever has focus to a pivot a third of the way into the
 * list, which pushes headers and captions out of view on Arc's screens. Without the margin, coming
 * back to a row's first card lined it up with the very edge rather than the row's own padding.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun minimalBringIntoView(margin: Dp = 48.dp): BringIntoViewSpec {
    val px = with(LocalDensity.current) { margin.toPx() }
    return remember(px) {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
                val leading = offset - px
                val trailing = offset + size + px
                return when {
                    leading >= 0f && trailing <= containerSize -> 0f
                    leading < 0f || size + 2 * px > containerSize -> leading
                    else -> trailing - containerSize
                }
            }
        }
    }
}
