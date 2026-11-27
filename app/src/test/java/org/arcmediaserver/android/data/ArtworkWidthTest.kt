package org.arcmediaserver.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtworkWidthTest {
    private val base = "http://arc.test:8199"

    @Test
    fun `Arc artwork is asked for at the drawn width`() {
        assertEquals("$base/api/v1/items/m1/images/backdrop_full?w=1920", ArtworkWidth.withWidth("$base/api/v1/items/m1/images/backdrop_full", 1920))
        assertEquals("$base/api/v1/items/m1/images/primary?v=abc&w=260", ArtworkWidth.withWidth("$base/api/v1/items/m1/images/primary?v=abc", 260))
        assertEquals("$base/api/v1/series/s1/seasons/2/image?w=300", ArtworkWidth.withWidth("$base/api/v1/series/s1/seasons/2/image", 300))
        assertEquals("$base/api/v1/people/p1/image?w=120", ArtworkWidth.withWidth("$base/api/v1/people/p1/image", 120))
        assertEquals("$base/api/v1/collections/c1/images/backdrop?w=780", ArtworkWidth.withWidth("$base/api/v1/collections/c1/images/backdrop", 780))
    }

    @Test
    fun `other addresses, a width already chosen and no width are left alone`() {
        assertNull(ArtworkWidth.withWidth("https://image.tmdb.org/t/p/original/b.jpg", 1920))
        assertNull(ArtworkWidth.withWidth("$base/api/v1/items/m1/stream", 1920))
        assertNull(ArtworkWidth.withWidth("$base/api/v1/items/m1/images/primary?w=480", 1920))
        assertNull(ArtworkWidth.withWidth("$base/api/v1/items/m1/images/primary", 0))
        assertNull(ArtworkWidth.withWidth("not a url", 100))
    }
}
