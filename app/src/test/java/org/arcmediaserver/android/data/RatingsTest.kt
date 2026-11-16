package org.arcmediaserver.android.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RatingsTest {
    @Test
    fun `stars in halves, as Arc stores them out of ten`() {
        assertEquals("★★★★★", Ratings.stars(10))
        assertEquals("★★★½", Ratings.stars(7))
        assertEquals("½", Ratings.stars(1))
        assertEquals("", Ratings.stars(0))
        assertEquals("★★★★★", Ratings.stars(12)) // out of range: kept to five
    }

    @Test
    fun `out of five as the web says it`() {
        assertEquals("3.5", Ratings.outOfFive(7))
        assertEquals("4", Ratings.outOfFive(8))
        assertEquals("0.5", Ratings.outOfFive(1))
    }

    @Test
    fun `the choices run from five stars down to half a star`() {
        assertEquals(10, Ratings.choices.first())
        assertEquals(1, Ratings.choices.last())
        assertEquals(10, Ratings.choices.size)
    }

    @Test
    fun `a title's own rating is kept beside its other choices`() {
        val movie = Item(id = "m", kind = "movie", title = "M", userData = UserData(positionMs = 60_000, watchlisted = true))
        assertEquals(0, movie.myRating)
        val rated = movie.withRating(9)
        assertEquals(9, rated.myRating)
        assertEquals(true, rated.onWatchlist)
        assertEquals(60_000L, rated.resumeMs)
    }
}
