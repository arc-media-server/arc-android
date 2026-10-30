package org.arcmediaserver.android.ui.details

import kotlinx.coroutines.test.runTest
import org.arcmediaserver.android.data.Item
import org.arcmediaserver.android.data.UserData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class TitleActionsTest {
    private val movie = Item(id = "m", kind = "movie", title = "M", userData = UserData(positionMs = 60_000))

    @Test
    fun `shows the change at once and keeps it when Arc saves it`() = runTest {
        val shown = mutableListOf<Item>()
        val ok = optimistic(movie, movie.withWatched(true), { shown += it }) { }
        assertTrue(ok)
        assertEquals(1, shown.size)
        assertTrue(shown.last().watched)
        assertEquals(0L, shown.last().resumeMs)
    }

    @Test
    fun `puts it back when saving fails`() = runTest {
        val shown = mutableListOf<Item>()
        val ok = optimistic(movie, movie.withWatchlist(true), { shown += it }) { throw IOException("offline") }
        assertFalse(ok)
        assertEquals(listOf(true, false), shown.map { it.onWatchlist })
        assertEquals(movie, shown.last())
    }

    @Test
    fun `a show is watched when nothing is left, and marking it so clears the count`() {
        val show = Item(id = "s", kind = "series", title = "S", unwatchedCount = 4)
        assertFalse(show.watched)
        assertTrue(show.withWatched(true).watched)
        assertEquals(0, show.withWatched(true).unwatchedCount)
        assertFalse(show.withWatched(true).withWatched(false).watched)
        assertTrue(show.canWatchlist)
        assertFalse(Item(id = "e", kind = "episode", title = "E").canWatchlist)
    }
}
