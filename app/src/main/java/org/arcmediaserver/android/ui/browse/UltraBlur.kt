package org.arcmediaserver.android.ui.browse

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import org.arcmediaserver.android.ui.theme.ArcColors

/**
 * Ultra Blur, as Plex's: instead of fading to black, the screen behind a title's art is a soft
 * wash of the art's own colours, one from each corner, darkened so white text stays readable. Arc
 * works the colours out (docs/ARTWORK-PALETTES.md in the server repo); the app draws the wash.
 */

/** The four corner colours of an image (top left, top right, bottom left, bottom right). */
data class CornerColors(val topLeft: Color, val topRight: Color, val bottomLeft: Color, val bottomRight: Color)

/** A colour brought down to wash brightness: its hue kept, its light capped well below text. */
internal fun darkened(c: Color): Color {
    val light = maxOf(c.red, c.green, c.blue)
    val scale = if (light > 0.42f) 0.42f / light else 1f
    // Mixed a little towards the page, so very bright art doesn't glare.
    return lerp(Color(c.red * scale, c.green * scale, c.blue * scale), ArcColors.Background, 0.25f)
}

/** Arc's palette colours ("#rrggbb"), darkened for the wash as the app's own sampling is. */
fun cornerColors(p: org.arcmediaserver.android.data.PaletteColors): CornerColors? {
    fun parse(hex: String) = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()?.let(::darkened)
    return CornerColors(parse(p.topLeft) ?: return null, parse(p.topRight) ?: return null, parse(p.bottomLeft) ?: return null, parse(p.bottomRight) ?: return null)
}

/**
 * The wash for a title's art, fading between titles. Its colours are Arc's palette for
 * [paletteId] (GET /items/{id}/artwork/palette, kept for the session). Without one (an older
 * Arc, or art Arc can't take colours from) it's the plain page, so the art fades to dark as
 * before (owner: no colours worked out on the TV). A stale answer for a title focused before is
 * never applied: the effect is cancelled when the focus moves.
 */
@Composable
fun UltraBlur(paletteId: String?, modifier: Modifier = Modifier) {
    val container = (LocalContext.current.applicationContext as? org.arcmediaserver.android.ArcApplication)?.container
    var colors by remember { mutableStateOf<CornerColors?>(null) }
    LaunchedEffect(paletteId) {
        colors = paletteId?.let { id -> container?.let { serverPalette(it, id) } }
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

/** Arc's palette for a title: kept for the session; null when Arc has none (or can't be asked now). */
private suspend fun serverPalette(container: org.arcmediaserver.android.AppContainer, id: String): CornerColors? {
    container.palettes[id]?.let { known -> return known.orElse(null)?.let(::cornerColors) }
    val api = container.api() ?: return null
    return try {
        val palette = api.artworkPalette(id)?.colors
        // Arc's answer (colours, or none for this title) is kept; a failure to ask isn't.
        container.palettes[id] = java.util.Optional.ofNullable(palette)
        palette?.let(::cornerColors)
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        null
    }
}
