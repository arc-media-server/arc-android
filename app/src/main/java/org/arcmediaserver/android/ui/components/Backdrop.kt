package org.arcmediaserver.android.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import coil3.size.Dimension
import coil3.size.Size
import coil3.transform.Transformation
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A backdrop picture with its shading (darker at the bottom and on the left, into [background])
 * drawn into it once, when it loads, off the main thread, rather than over it on every frame.
 *
 * It's cropped to the screen's shape as `ContentScale.Crop` would (centred), so the shading lands
 * where it did when it was drawn on screen, and kept at the picture's own resolution. Once shaded
 * the picture is opaque, so it's kept in 16 bits a pixel (half the memory, on a 2 GB Chromecast),
 * with an ordered dither so the dark gradients don't band, and handed to the GPU straight away.
 */
class BackdropShade(background: Color) : Transformation() {
    private val bg = background
    override val cacheKey: String = "backdrop-shade-1-${background.toArgb()}"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val w = (size.width as? Dimension.Pixels)?.px ?: input.width
        val h = (size.height as? Dimension.Pixels)?.px ?: input.height
        val scale = max(w / input.width.toFloat(), h / input.height.toFloat())
        val cw = min(input.width, (w / scale).roundToInt()).coerceAtLeast(1)
        val ch = min(input.height, (h / scale).roundToInt()).coerceAtLeast(1)
        val left = (input.width - cw) / 2
        val top = (input.height - ch) / 2

        val shaded = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888)
        Canvas(shaded).apply {
            drawBitmap(input, Rect(left, top, left + cw, top + ch), Rect(0, 0, cw, ch), Paint(Paint.FILTER_BITMAP_FLAG))
            val w2 = cw.toFloat()
            val h2 = ch.toFloat()
            drawRect(0f, 0f, w2, h2, Paint().apply { shader = LinearGradient(0f, 0f, 0f, h2, BottomColors.map { it.toArgb() }.toIntArray(), BottomStops, Shader.TileMode.CLAMP) })
            drawRect(0f, 0f, w2, h2, Paint().apply { shader = LinearGradient(0f, 0f, w2, 0f, LeftColors.map { it.toArgb() }.toIntArray(), LeftStops, Shader.TileMode.CLAMP) })
        }
        val packed = dithered565(shaded)
        shaded.recycle()
        // On the GPU now, from this background thread, rather than on the first frame it's drawn.
        val onGpu = runCatching { packed.copy(Bitmap.Config.HARDWARE, false) }.getOrNull()
        return if (onGpu != null) {
            packed.recycle()
            onGpu
        } else {
            packed
        }
    }

    private val BottomColors = listOf(Color.Transparent, Color.Transparent, bg.copy(alpha = 0.75f), bg.copy(alpha = 0.95f))
    private val LeftColors = listOf(bg.copy(alpha = 0.7f), bg.copy(alpha = 0.25f), Color.Transparent)

    override fun equals(other: Any?) = other is BackdropShade && other.bg == bg
    override fun hashCode() = bg.hashCode()

    companion object {
        /** The shading drawn over a title's backdrop: [BackdropShade]'s, so both stay the same. */
        val BottomStops = floatArrayOf(0f, 0.45f, 0.78f, 1f)
        val LeftStops = floatArrayOf(0f, 0.4f, 0.6f)

        /**
         * [source] in RGB 565, each pixel nudged by a 4×4 ordered dither before its channels are
         * cut to 5 and 6 bits, so smooth dark gradients become fine noise rather than bands.
         * Worked in bands of rows to keep the extra memory small.
         */
        fun dithered565(source: Bitmap): Bitmap {
            val w = source.width
            val h = source.height
            // Packed here rather than by Android, whose conversion rounds on some versions and
            // truncates on others: the dither is made for cutting the low bits off.
            val packed = ShortArray(w * h)
            val band = 32
            val row = IntArray(w * band)
            var y0 = 0
            while (y0 < h) {
                val n = min(band, h - y0)
                source.getPixels(row, 0, w, 0, y0, w, n)
                for (dy in 0 until n) {
                    val bayerRow = ((y0 + dy) and 3) shl 2
                    val src = dy * w
                    val dst = (y0 + dy) * w
                    for (x in 0 until w) {
                        val p = row[src + x]
                        val d = Bayer[bayerRow or (x and 3)]
                        // 5-bit red and blue lose 3 bits (steps of 8), 6-bit green 2 (steps of 4).
                        val r = min(31, (((p shr 16) and 0xFF) + (d shr 1)) shr 3)
                        val g = min(63, (((p shr 8) and 0xFF) + (d shr 2)) shr 2)
                        val b = min(31, ((p and 0xFF) + (d shr 1)) shr 3)
                        packed[dst + x] = ((r shl 11) or (g shl 5) or b).toShort()
                    }
                }
                y0 += n
            }
            return Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565).apply {
                copyPixelsFromBuffer(java.nio.ShortBuffer.wrap(packed))
            }
        }

        private val Bayer = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)
    }
}

/**
 * A card's picture darkened towards the bottom (from [from] of the way down to [darkness] black at
 * the foot), drawn into it once when it loads, off the main thread, so the title or logo over it
 * reads; drawn over it on screen, it was a half-card gradient blended on every frame. Shaded in the
 * picture's own space: cards crop their (wider) pictures at the sides, so it lands the same.
 */
class BottomShade(private val from: Float = 0.5f, private val darkness: Float = 0.7f) : Transformation() {
    override val cacheKey: String = "bottom-shade-1-$from-$darkness"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val opaque = !input.hasAlpha()
        val out = input.copy(Bitmap.Config.ARGB_8888, true)
        val h = out.height.toFloat()
        Canvas(out).drawRect(
            0f, h * from, out.width.toFloat(), h,
            Paint().apply {
                shader = LinearGradient(0f, h * from, 0f, h, Color.Transparent.toArgb(), Color.Black.copy(alpha = darkness).toArgb(), Shader.TileMode.CLAMP)
            },
        )
        // Still opaque, so the card's placeholder under it can go (Artwork checks).
        if (opaque) out.setHasAlpha(false)
        val onGpu = runCatching { out.copy(Bitmap.Config.HARDWARE, false) }.getOrNull()
        return if (onGpu != null) {
            out.recycle()
            onGpu
        } else {
            out
        }
    }

    override fun equals(other: Any?) = other is BottomShade && other.from == from && other.darkness == darkness
    override fun hashCode() = 31 * from.hashCode() + darkness.hashCode()
}

/**
 * How many backdrops on screen are opaque and fill it, so Arc's root doesn't paint the page
 * colour underneath (on a Chromecast with Google TV every whole-screen fill costs frame time).
 * Null outside the app's root (tests, previews): nothing is reported.
 */
class BackdropCover {
    var count by mutableIntStateOf(0)
}

val LocalBackdropCover = staticCompositionLocalOf<BackdropCover?> { null }
