package org.arcmediaserver.android.ui.browse

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import org.arcmediaserver.android.ui.components.LocalImageUrl
import org.arcmediaserver.android.ui.theme.ArcColors
import kotlin.math.max

/**
 * Plex's "Ultra Blur": instead of fading to black, the screen behind a title's art is a soft wash
 * of the art's own colours, one from each corner, darkened so white text stays readable.
 */

/** The four corner colours of an image (top left, top right, bottom left, bottom right). */
data class CornerColors(val topLeft: Color, val topRight: Color, val bottomLeft: Color, val bottomRight: Color)

/**
 * Each corner's average colour (a quarter of the image each), darkened for the wash: a page of
 * the art's colours at about the brightness of a dimmed room, so white text still reads.
 */
fun cornerColors(bitmap: Bitmap): CornerColors {
    val w = bitmap.width
    val h = bitmap.height
    fun average(x0: Int, y0: Int, x1: Int, y1: Int): Color {
        var r = 0L
        var g = 0L
        var b = 0L
        var n = 0L
        for (y in y0 until max(y0 + 1, y1)) for (x in x0 until max(x0 + 1, x1)) {
            val p = bitmap.getPixel(x.coerceIn(0, w - 1), y.coerceIn(0, h - 1))
            r += (p shr 16) and 0xff
            g += (p shr 8) and 0xff
            b += p and 0xff
            n++
        }
        return darkened(Color(red = (r / n).toInt(), green = (g / n).toInt(), blue = (b / n).toInt()))
    }
    return CornerColors(
        average(0, 0, w / 2, h / 2),
        average(w / 2, 0, w, h / 2),
        average(0, h / 2, w / 2, h),
        average(w / 2, h / 2, w, h),
    )
}

/** A colour brought down to wash brightness: its hue kept, its light capped well below text. */
internal fun darkened(c: Color): Color {
    val light = maxOf(c.red, c.green, c.blue)
    val scale = if (light > 0.42f) 0.42f / light else 1f
    // Mixed a little towards the page, so very bright art doesn't glare.
    return lerp(Color(c.red * scale, c.green * scale, c.blue * scale), ArcColors.Background, 0.25f)
}

/** How the app gets a small copy of an image to take its colours from (tests give their own). */
val LocalArtworkBitmap = compositionLocalOf<(suspend (android.content.Context, String) -> Bitmap?)?> { null }

private suspend fun loadSmall(context: android.content.Context, url: String): Bitmap? {
    val request = ImageRequest.Builder(context).data(url).size(48, 27).allowHardware(false).build()
    return SingletonImageLoader.get(context).execute(request).image?.toBitmap()
}

/** The wash for [url]'s art, fading between titles; the page colour until the art's colours are known. */
@Composable
fun UltraBlur(url: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val resolve = LocalImageUrl.current
    val load = LocalArtworkBitmap.current ?: ::loadSmall
    var colors by remember { mutableStateOf<CornerColors?>(null) }
    LaunchedEffect(url) {
        if (url == null) return@LaunchedEffect
        val bitmap = runCatching { load(context, resolve(url)) }.getOrNull() ?: return@LaunchedEffect
        colors = cornerColors(bitmap)
    }
    val base = ArcColors.Background
    val spec = tween<Color>(700)
    val tl by animateColorAsState(colors?.topLeft ?: base, spec, label = "tl")
    val tr by animateColorAsState(colors?.topRight ?: base, spec, label = "tr")
    val bl by animateColorAsState(colors?.bottomLeft ?: base, spec, label = "bl")
    val br by animateColorAsState(colors?.bottomRight ?: base, spec, label = "br")
    Canvas(modifier.fillMaxSize()) {
        drawRect(base)
        val reach = size.maxDimension * 0.85f
        // One soft glow from each corner, overlapping in the middle, as Plex's wash looks.
        for ((color, at) in listOf(tl to Offset.Zero, tr to Offset(size.width, 0f), bl to Offset(0f, size.height), br to Offset(size.width, size.height))) {
            drawRect(Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center = at, radius = reach))
        }
    }
}
