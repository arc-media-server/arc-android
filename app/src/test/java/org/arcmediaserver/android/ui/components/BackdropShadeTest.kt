package org.arcmediaserver.android.ui.components

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import coil3.size.Size
import kotlinx.coroutines.test.runTest
import org.arcmediaserver.android.ui.theme.ArcColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class BackdropShadeTest {
    private fun flat(w: Int, h: Int, color: Int) = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    private fun channels(p: Int) = intArrayOf((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF)

    /** The average colour of a block of [b], channel by channel. */
    private fun mean(b: Bitmap, x: Int, y: Int, size: Int): DoubleArray {
        val sum = DoubleArray(3)
        for (j in y until y + size) for (i in x until x + size) channels(b.getPixel(i, j)).forEachIndexed { c, v -> sum[c] += v.toDouble() }
        return DoubleArray(3) { sum[it] / (size * size) }
    }

    @Test
    fun `a backdrop is cropped to the screen's shape, from its middle, at its own resolution`() = runTest {
        // A 4:3 picture, black with a white band at the top and bottom that the crop cuts off.
        val source = flat(400, 300, android.graphics.Color.BLACK)
        for (y in 0 until 37) for (x in 0 until 400) { source.setPixel(x, y, android.graphics.Color.WHITE); source.setPixel(x, 299 - y, android.graphics.Color.WHITE) }
        val out = BackdropShade(ArcColors.Background).transform(source, Size(1920, 1080))
        assertEquals(400, out.width)
        assertEquals(225, out.height)
        // The white bands are gone: the top middle is still the picture's black.
        assertTrue(channels(out.getPixel(300, 2)).all { it < 16 })
    }

    @Test
    fun `the shading is drawn in, the bottom and left turn to the page colour, the top right stays`() = runTest {
        val grey = Color(0xFF808080)
        val out = BackdropShade(ArcColors.Background).transform(flat(320, 180, grey.toArgb()), Size(1920, 1080))
        val topRight = mean(out, 280, 4, 8)
        // 16-bit colour reads back a few levels off.
        topRight.forEach { assertEquals(128.0, it, 6.0) }
        val bottomLeft = mean(out, 0, 172, 8)
        val page = channels(ArcColors.Background.toArgb())
        bottomLeft.forEachIndexed { c, v -> assertTrue("bottom left is close to the page colour: $v", abs(v - page[c]) < 20) }
    }

    @Test
    fun `16-bit backdrops keep their colour on average, without bands`() {
        // A dark colour between 16-bit steps: cut down plainly, every pixel would lose it.
        val dark = android.graphics.Color.rgb(13, 14, 19)
        val packed = BackdropShade.dithered565(flat(64, 64, dark))
        assertEquals(Bitmap.Config.RGB_565, packed.config)
        val m = mean(packed, 0, 0, 64)
        channels(dark).forEachIndexed { c, v -> assertEquals("channel $c", v.toDouble(), m[c], 2.0) }
    }
}
