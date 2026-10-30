package org.arcmediaserver.android.ui.browse

import android.graphics.Bitmap
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
    fun `each corner takes its own quarter's colour, darkened for the wash`() {
        val bmp = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        for (y in 0 until 4) for (x in 0 until 4) {
            bmp.setPixel(x, y, when {
                x < 2 && y < 2 -> 0xffff0000.toInt() // red, top left
                y < 2 -> 0xff00ff00.toInt() // green, top right
                x < 2 -> 0xff0000ff.toInt() // blue, bottom left
                else -> 0xff202020.toInt() // dark grey, bottom right
            })
        }
        val c = cornerColors(bmp)
        assertTrue("top left is red", c.topLeft.red > c.topLeft.green * 3 && c.topLeft.red > c.topLeft.blue * 3)
        assertTrue("top right is green", c.topRight.green > c.topRight.red * 3)
        assertTrue("bottom left is blue", c.bottomLeft.blue > c.bottomLeft.red * 3)
        // Bright colours are capped well below white text; dark ones stay dark.
        for (col in listOf(c.topLeft, c.topRight, c.bottomLeft)) assertTrue(maxOf(col.red, col.green, col.blue) < 0.45f)
        assertEquals(c.bottomRight.red, c.bottomRight.blue, 0.02f)
    }
}
