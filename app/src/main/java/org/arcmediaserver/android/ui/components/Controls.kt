package org.arcmediaserver.android.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface

/**
 * A round icon button, as the player's controls and a title's Watched / Watchlist / More: white
 * when focused; its [label] is shown by the screen (passed to [onLabel] while it has the focus).
 */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    containerColor: Color = Color.Transparent,
    /** Null: the button's content colour (white, black when focused). */
    iconTint: Color? = null,
    onLabel: (String?) -> Unit = {},
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(size).onFocusChanged { if (it.isFocused) onLabel(label) },
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = containerColor,
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = iconTint ?: androidx.tv.material3.LocalContentColor.current, modifier = Modifier.size(size / 2))
        }
    }
}

private val codecNames = mapOf(
    "aac" to "AAC", "ac3" to "Dolby Digital", "eac3" to "Dolby Digital Plus", "truehd" to "Dolby TrueHD", "dts" to "DTS",
    "flac" to "FLAC", "mp3" to "MP3", "opus" to "Opus", "vorbis" to "Vorbis", "h264" to "H.264", "hevc" to "HEVC", "h265" to "HEVC",
    "av1" to "AV1", "vp9" to "VP9", "vp8" to "VP8", "mpeg2video" to "MPEG-2", "mpeg4" to "MPEG-4",
    "subrip" to "SRT", "ass" to "ASS", "webvtt" to "WebVTT", "mov_text" to "MP4 Text", "hdmv_pgs_subtitle" to "PGS", "dvd_subtitle" to "VobSub",
)

/** A codec as people know it (web/src/lib/format.ts codec), with Atmos and DTS-HD from the profile. */
fun codecName(codec: String?, profile: String? = null): String? {
    if (codec.isNullOrBlank()) return null
    val p = profile?.lowercase().orEmpty()
    when {
        codec == "truehd" && p == "dolby truehd + dolby atmos" -> return "TrueHD Atmos"
        codec == "eac3" && p == "dolby digital plus + dolby atmos" -> return "Dolby Digital+ Atmos"
        codec == "dts" && p.startsWith("dts-hd ma + dts:x") -> return "DTS-HD MA · DTS:X"
        codec == "dts" && p == "dts-hd ma" -> return "DTS-HD MA"
        codec == "dts" && p == "dts-hd hra" -> return "DTS-HD HRA"
    }
    return codecNames[codec] ?: codec.uppercase()
}

/** 4K, 1080p, 720p or SD (web/src/lib/format.ts resolution). */
fun resolutionName(width: Int, height: Int): String? = when {
    width <= 0 || height <= 0 -> null
    width >= 3200 || height >= 1600 -> "4K"
    width >= 1800 || height >= 1000 -> "1080p"
    width >= 1200 || height >= 700 -> "720p"
    else -> "SD"
}
