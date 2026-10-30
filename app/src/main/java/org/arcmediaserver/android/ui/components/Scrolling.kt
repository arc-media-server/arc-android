package org.arcmediaserver.android.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec

/**
 * Scrolls only as far as needed to show the focused element. Compose for TV's default instead
 * moves whatever has focus to a pivot a third of the way into the list, which pushes headers and
 * captions out of view on Arc's screens.
 */
@OptIn(ExperimentalFoundationApi::class)
val MinimalBringIntoView = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val trailing = offset + size
        return when {
            offset >= 0f && trailing <= containerSize -> 0f
            offset < 0f || size > containerSize -> offset
            else -> trailing - containerSize
        }
    }
}
