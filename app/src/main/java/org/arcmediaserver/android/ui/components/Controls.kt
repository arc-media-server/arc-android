package org.arcmediaserver.android.ui.components

import org.arcmediaserver.android.ui.theme.ArcColors
import org.arcmediaserver.android.data.MediaStream
import org.arcmediaserver.android.R
import androidx.tv.material3.Text
import androidx.tv.material3.MaterialTheme
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
        modifier = modifier.requiredSize(size).onFocusChanged { if (it.isFocused) onLabel(label) },
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

/** Mono, Stereo, 5.1, 7.1 or "N channels". */
@Composable
fun channelsName(n: Int): String? = when (n) {
    0 -> null
    1 -> stringResource(R.string.player_mono)
    2 -> stringResource(R.string.player_stereo)
    6 -> "5.1"
    8 -> "7.1"
    else -> stringResource(R.string.player_channels, n)
}

/** Dolby Vision, HDR10+, HDR10 or HLG (web/src/lib/format.ts sourceFormats). */
fun hdrLabels(v: MediaStream): List<String> = buildList {
    if (v.dolbyVision) add("Dolby Vision")
    if (v.hdr10Plus) add("HDR10+")
    if (isEmpty() && v.colorTransfer == "smpte2084") add("HDR10")
    if (v.colorTransfer == "arib-std-b67") add("HLG")
}


/**
 * A title's labels, as the web's title page shows them: its age rating, then for one version the
 * picture (4K, Dolby Vision…) and the main sound (TrueHD Atmos 7.1), or how many versions there are.
 */
@Composable
fun formatLabels(item: org.arcmediaserver.android.data.Item): List<String> = buildList {
    item.contentRating?.takeIf { it.isNotBlank() }?.let(::add)
    val files = item.files.filter { !it.missing }
    if (files.size > 1) {
        add(androidx.compose.ui.res.pluralStringResource(org.arcmediaserver.android.R.plurals.media_versions, files.size, files.size))
        return@buildList
    }
    val file = files.firstOrNull() ?: return@buildList
    val video = file.streams.firstOrNull { it.kind == "video" }
    val audio = file.streams.firstOrNull { it.kind == "audio" }
    video?.let { v -> resolutionName(v.width, v.height)?.let(::add); addAll(hdrLabels(v)) }
    audio?.let { a -> listOfNotNull(codecName(a.codec, a.profile), channelsName(a.channels)).joinToString(" ").takeIf { it.isNotBlank() }?.let(::add) }
}

/** Labels as small outlined badges, as the web's. */
@Composable
fun FormatBadges(labels: List<String>, modifier: Modifier = Modifier) {
    if (labels.isEmpty()) return
    androidx.compose.foundation.layout.Row(modifier, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
        for (label in labels) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = ArcColors.Foreground.copy(alpha = 0.85f),
                maxLines = 1,
                modifier = Modifier
                    .border(1.dp, ArcColors.Foreground.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
    }
}

/**
 * A title's badges, as the Apple TV app's: the age rating in an outlined box, then the file's quality
 * as capsules: the resolution filled white, then outlined capsules split by a line ("DV | HDR10",
 * "DD+ | 5.1", "Atmos"). A title with several versions says how many instead.
 */
@Composable
fun TitleBadges(item: org.arcmediaserver.android.data.Item, modifier: Modifier = Modifier, showRating: Boolean = true) {
    val files = item.files.filter { !it.missing }
    androidx.compose.foundation.layout.Row(modifier, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        if (showRating) item.contentRating?.takeIf { it.isNotBlank() }?.let { AgeRating(it) }
        if (files.size > 1) {
            Capsule(listOf(androidx.compose.ui.res.pluralStringResource(org.arcmediaserver.android.R.plurals.media_versions, files.size, files.size)), filled = false)
            return@Row
        }
        org.arcmediaserver.android.playback.Quality.capsules(files.firstOrNull()).forEach { Capsule(it.parts, it.filled) }
    }
}

/** A title's age rating ("12A", "PG-13") in its outlined box. */
@Composable
fun AgeRating(rating: String) {
    Text(
        rating, style = MaterialTheme.typography.labelMedium, color = ArcColors.Foreground.copy(alpha = 0.85f), maxLines = 1,
        modifier = Modifier.border(1.dp, ArcColors.Foreground.copy(alpha = 0.5f), RoundedCornerShape(3.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

@Composable
private fun Capsule(parts: List<String>, filled: Boolean) {
    val shape = RoundedCornerShape(50)
    val base = if (filled) Modifier.background(ArcColors.Foreground, shape) else Modifier.border(1.dp, ArcColors.Foreground.copy(alpha = 0.55f), shape)
    androidx.compose.foundation.layout.Row(base.height(androidx.compose.foundation.layout.IntrinsicSize.Min).padding(horizontal = 8.dp, vertical = 1.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        parts.forEachIndexed { i, p ->
            if (i > 0) androidx.compose.foundation.layout.Box(Modifier.padding(horizontal = 6.dp).width(1.dp).fillMaxHeight().background(ArcColors.Foreground.copy(alpha = 0.45f)))
            Text(p, style = MaterialTheme.typography.labelMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = if (filled) ArcColors.Background else ArcColors.Foreground.copy(alpha = 0.9f), maxLines = 1)
        }
    }
}
