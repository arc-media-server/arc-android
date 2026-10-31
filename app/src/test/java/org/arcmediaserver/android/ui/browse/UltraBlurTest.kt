package org.arcmediaserver.android.ui.browse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UltraBlurTest {
    @Test
    fun `Arc's palette becomes the wash, darkened the same way`() {
        val c = cornerColors(org.arcmediaserver.android.data.PaletteColors("#163064", "#ff8040", "#172955", "#643354"))!!
        assertTrue("top right stays warm", c.topRight.red > c.topRight.blue)
        assertTrue("capped for text", maxOf(c.topRight.red, c.topRight.green, c.topRight.blue) < 0.45f)
        // A colour Arc can't have sent means no palette, so the app's own sampling is used.
        assertEquals(null, cornerColors(org.arcmediaserver.android.data.PaletteColors("#163064", "nope", "#172955", "#643354")))
    }
}
