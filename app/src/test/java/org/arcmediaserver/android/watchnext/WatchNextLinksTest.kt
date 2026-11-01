package org.arcmediaserver.android.watchnext

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The row's links into the app and to its artwork: only what they're for gets through. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class WatchNextLinksTest {
    @Test
    fun `a resume link carries the title and nothing else is taken for one`() {
        assertEquals("m1", DeepLink.itemToPlay(DeepLink.play("m1")))
        assertNull(DeepLink.itemToPlay(Uri.parse("arc://play/")))
        assertNull(DeepLink.itemToPlay(Uri.parse("arc://other/m1")))
        assertNull(DeepLink.itemToPlay(Uri.parse("https://play/m1")))
        assertNull(DeepLink.itemToPlay(null))
    }

    @Test
    fun `artwork addresses round-trip, and only pictures are given out`() {
        val path = "/api/items/m1/images/backdrop?v=abc123"
        val uri = ArtworkProvider.uri(path)
        assertEquals("content", uri.scheme)
        assertEquals(ArtworkProvider.AUTHORITY, uri.authority)
        assertEquals(path, ArtworkProvider.arcPath(uri))
        assertEquals("/api/v1/collections/c1/images/primary", ArtworkProvider.arcPath(ArtworkProvider.uri("/api/v1/collections/c1/images/primary")))
        for (bad in listOf("/api/items/m1", "/api/items/m1/stream", "/api/auth/status", "/api/items/../auth/images/primary", "/api/items/m1/images/primary/../../x")) {
            assertNull(bad, ArtworkProvider.arcPath(Uri.parse("content://${ArtworkProvider.AUTHORITY}$bad")))
        }
        assertNull(ArtworkProvider.arcPath(Uri.parse("content://other.authority/api/items/m1/images/primary")))
    }
}
