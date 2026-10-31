package org.arcmediaserver.android.playback

import android.graphics.Color
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import org.arcmediaserver.android.data.SubtitleStyle

/**
 * How text subtitles look, from the profile's subtitle style (set on the web, as the web player
 * draws them: web Player.tsx subtitleLook): white or yellow; a translucent box (the default), a
 * solid one, or none with a dark outline. Android's own caption settings (a solid black box by
 * default) aren't used, as on Plex. ASS/SSA drawn by libass keep their own styling.
 */
@OptIn(UnstableApi::class)
fun captionStyle(style: SubtitleStyle): CaptionStyleCompat {
    val text = if (style.color == "yellow") 0xFFFFE14D.toInt() else Color.WHITE
    val (background, edge) = when (style.background) {
        "solid" -> Color.BLACK to CaptionStyleCompat.EDGE_TYPE_NONE
        "none" -> Color.TRANSPARENT to CaptionStyleCompat.EDGE_TYPE_OUTLINE
        else -> 0xA6000000.toInt() to CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
    }
    return CaptionStyleCompat(text, background, Color.TRANSPARENT, edge, Color.BLACK, null)
}

/** Text height as a share of the picture's (Media3's default, 0.0533, is "medium"). */
fun subtitleTextFraction(style: SubtitleStyle): Float = when (style.size) {
    "small" -> 0.045f
    "large" -> 0.065f
    "huge" -> 0.08f
    else -> 0.0533f
}
