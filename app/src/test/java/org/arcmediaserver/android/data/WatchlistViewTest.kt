package org.arcmediaserver.android.data

import org.arcmediaserver.android.data.WatchlistView.Show
import org.arcmediaserver.android.data.WatchlistView.Sort
import org.junit.Assert.assertEquals
import org.junit.Test

class WatchlistViewTest {
    private val items = listOf(
        Item(id = "1", kind = "movie", title = "The Zebra", year = 2001),
        Item(id = "2", kind = "series", title = "Apple Grove", year = 2020, releaseDate = "2020-05-01"),
        Item(id = "3", kind = "movie", title = "Mango", year = 2010, releaseDate = "2010-01-02"),
    )

    @Test
    fun `date added keeps Arc's order and the filter keeps films or shows`() {
        assertEquals(listOf("1", "2", "3"), WatchlistView.shown(items, Show.All, Sort.DateAdded).map { it.id })
        assertEquals(listOf("1", "3"), WatchlistView.shown(items, Show.Movies, Sort.DateAdded).map { it.id })
        assertEquals(listOf("2"), WatchlistView.shown(items, Show.Shows, Sort.DateAdded).map { it.id })
    }

    @Test
    fun `title ignores a leading The and release date is newest first`() {
        assertEquals(listOf("2", "3", "1"), WatchlistView.shown(items, Show.All, Sort.Title).map { it.id })
        assertEquals(listOf("2", "3", "1"), WatchlistView.shown(items, Show.All, Sort.ReleaseDate).map { it.id })
    }
}
