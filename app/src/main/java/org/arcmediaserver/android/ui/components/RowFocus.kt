package org.arcmediaserver.android.ui.components

import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Which card a row of cards lands on when focus enters it, from above, below or coming back: the
 * card last focused in it, else [preferred] (a show's next episode), else its first card, as Plex's
 * and Leanback's rows. Compose's own choice is the card nearest the one left, which is a different
 * card whenever rows' cards differ in width (and the right-most one moving up). Only cards on
 * screen are chosen, so entering never scrolls the row sideways.
 */
class RowFocus internal constructor(private val state: LazyListState, private val last: androidx.compose.runtime.MutableState<String?>) {
    private val cards = HashMap<String, FocusRequester>()

    /** Goes on the row (the LazyRow given [state]). */
    fun modifier(preferred: () -> String? = { null }): Modifier = Modifier.focusProperties {
        onEnter = {
            val visible = state.layoutInfo.visibleItemsInfo.map { it.key.toString() }.filter { it in cards }
            val target = last.value?.takeIf { it in visible }
                ?: preferred()?.takeIf { it in visible }
                ?: visible.firstOrNull()
            if (target != null) cards[target]?.requestFocus()
        }
    }.focusGroup()

    /** Wraps one card: [key] is the card's key in the row. */
    @Composable
    fun Card(key: Any, content: @Composable () -> Unit) {
        val id = key.toString()
        val requester = remember(id) { FocusRequester().also { cards[id] = it } }
        Box(Modifier.focusRequester(requester).onFocusChanged { if (it.hasFocus) last.value = id }) { content() }
    }

    /**
     * Wraps every card a row's content adds. A card without a key gets one by its place, so the
     * row can tell its cards apart.
     */
    fun wrap(inner: LazyListScope): LazyListScope = object : LazyListScope {
        private var next = 0

        override fun item(key: Any?, contentType: Any?, content: @Composable LazyItemScope.() -> Unit) {
            val k = key ?: "#${next}"
            next++
            inner.item(k, contentType) { Card(k) { content() } }
        }

        override fun items(count: Int, key: ((Int) -> Any)?, contentType: (Int) -> Any?, itemContent: @Composable LazyItemScope.(Int) -> Unit) {
            val base = next
            next += count
            val keyOf: (Int) -> Any = { i -> key?.invoke(i) ?: "#${base + i}" }
            inner.items(count, keyOf, contentType) { i -> Card(keyOf(i)) { itemContent(i) } }
        }
    }
}

/** A [RowFocus] for the row scrolled by [state]; it remembers the last card while scrolled away. */
@Composable
fun rememberRowFocus(state: LazyListState): RowFocus {
    val last = rememberSaveable { mutableStateOf<String?>(null) }
    return remember(state) { RowFocus(state, last) }
}

/**
 * Scrolls a page of rows so the row focus enters lands at the foot of the screen, moving down or
 * back up, with the rows above it in view: arc-kodi's rows (arc-kodi#55) and Plex's. The rows'
 * cards don't scroll the page themselves (`ScrollByShelf`); each row asks with [landsAtFoot].
 */
class FootScroll internal constructor(private val state: LazyListState, private val scope: CoroutineScope, private val gapPx: Float) {
    internal var page: LayoutCoordinates? = null

    /** Moves the page so [row] ends [gapPx] above its foot; [toTop] scrolls right back up instead. */
    fun land(row: LayoutCoordinates, toTop: Boolean = false) {
        val page = page?.takeIf { it.isAttached } ?: return
        if (!row.isAttached) return
        scope.launch {
            if (toTop) {
                state.animateScrollToItem(0)
                return@launch
            }
            val bounds = page.localBoundingBoxOf(row, clipBounds = false)
            val room = page.size.height - gapPx
            // A row taller than the screen (a title's About section) shows from its top instead.
            val delta = if (bounds.height > room) bounds.top else bounds.bottom - room
            if (abs(delta) >= 1f) state.animateScrollBy(delta, tween(FootScrollMs))
        }
    }
}

/** A [FootScroll] for the page scrolled by [state]; [gap] is what stays below the focused row. */
@Composable
fun rememberFootScroll(state: LazyListState, gap: Dp): FootScroll {
    val scope = rememberCoroutineScope()
    val gapPx = with(LocalDensity.current) { gap.toPx() }
    return remember(state, gapPx) { FootScroll(state, scope, gapPx) }
}

/** Goes on the page (the LazyColumn) a [FootScroll] moves. */
fun Modifier.footScrollPage(foot: FootScroll): Modifier = onGloballyPositioned { foot.page = it }

/** Goes on a row of the page: focus entering it lands it at the foot ([toTop]: back at the top). */
@Composable
fun Modifier.landsAtFoot(foot: FootScroll, toTop: Boolean = false): Modifier {
    // Plain holders: these change during layout and focus, and nothing redraws from them.
    val state = remember { LandState() }
    return onGloballyPositioned {
        state.at = it
        // A row made just now for focus to move into (below the screen) is focused before it
        // has been placed: it lands once it has.
        if (state.pending) {
            state.pending = false
            foot.land(it, toTop)
        }
    }.onFocusChanged {
        if (it.hasFocus && !state.had) {
            val row = state.at?.takeIf { c -> c.isAttached }
            if (row != null) foot.land(row, toTop) else state.pending = true
        }
        state.had = it.hasFocus
    }
}

private class LandState {
    var at: LayoutCoordinates? = null
    var had = false
    var pending = false
}

/** Kodi's rows take 260 ms to scroll (arc-kodi skin/script-arc-home.xml). */
private const val FootScrollMs = 260
