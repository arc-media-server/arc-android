package org.arcmediaserver.android.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The icons the web app uses (Lucide, ISC licence), drawn the same way: 24-unit strokes 2 wide with
 * round ends. Kept here rather than pulling in an icon library for a dozen shapes.
 */
object ArcIcons {
    private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float) =
        "M${x + r},${y}h${w - 2 * r}a$r,$r 0 0 1 $r,${r}v${h - 2 * r}a$r,$r 0 0 1 -$r,${r}h${-(w - 2 * r)}a$r,$r 0 0 1 -$r,-${r}v${-(h - 2 * r)}a$r,$r 0 0 1 $r,-${r}z"

    private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r},${cy}a$r,$r 0 1 0 ${2 * r},0a$r,$r 0 1 0 ${-2 * r},0"

    private fun icon(name: String, vararg paths: String, filled: Boolean = false): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        for (p in paths) {
            b.addPath(
                pathData = addPathNodes(p),
                fill = if (filled) SolidColor(Color.White) else null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return b.build()
    }

    // Navigation (web/src/components/Sidebar.tsx, web/src/lib/kinds.ts).
    val Home = icon(
        "home",
        "M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8",
        "M3 10a2 2 0 0 1 .709-1.528l7-5.999a2 2 0 0 1 2.582 0l7 5.999A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z",
    )
    val Film = icon(
        "film", rect(3f, 3f, 18f, 18f, 2f),
        "M7 3v18", "M3 7.5h4", "M3 12h18", "M3 16.5h4", "M17 3v18", "M17 7.5h4", "M17 16.5h4",
    )
    val Tv = icon("tv", rect(2f, 7f, 20f, 15f, 2f), "M17 2 12 7 7 2")
    val Clapperboard = icon(
        "clapperboard",
        "M20.2 6 3 11l-.9-2.4c-.3-1.1.3-2.2 1.3-2.5l13.5-4c1.1-.3 2.2.3 2.5 1.3Z",
        "m6.2 5.3 3.1 3.9", "m12.4 3.4 3.1 4",
        "M3 11h18v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2Z",
    )
    val SignOut = icon("log-out", "M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4", "m16 17 5-5-5-5", "M21 12H9")

    // Player (web/src/components/Player.tsx).
    val Play = icon("play", "M6 3 20 12 6 21 6 3z", filled = true)
    val Pause = icon("pause", rect(14f, 4f, 4f, 16f, 1f), rect(6f, 4f, 4f, 16f, 1f), filled = true)
    val SkipBack = icon("skip-back", "M19 20 9 12 19 4 19 20z", "M5 19V5", filled = true)
    val SkipForward = icon("skip-forward", "M5 4 15 12 5 20 5 4z", "M19 5v14", filled = true)
    val RotateCcw = icon("rotate-ccw", "M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8", "M3 3v5h5")
    val RotateCw = icon("rotate-cw", "M21 12a9 9 0 1 1-9-9c2.52 0 4.93 1 6.74 2.74L21 8", "M21 3v5h-5")
    val Languages = icon("languages", "m5 8 6 6", "m4 14 6-6 2-3", "M2 5h12", "M7 2h1", "m22 22-5-10-5 10", "M14 18h6")
    val Captions = icon("captions", rect(3f, 5f, 18f, 14f, 2f), "M7 15h4M15 15h2M7 11h2M13 11h4")
    val Sliders = icon(
        "sliders-horizontal",
        "M21 4h-7", "M10 4H3", "M21 12h-9", "M8 12H3", "M21 20h-5", "M12 20H3", "M14 2v4", "M8 10v4", "M16 18v4",
    )
    val ListOrdered = icon("list-ordered", "M10 12h11", "M10 18h11", "M10 6h11", "M4 10h2", "M4 6h1v4", "M6 18H4c0-1 2-2 2-3s-1-1.5-2-1")
    val Gauge = icon("gauge", "m12 14 4-4", "M3.34 19a10 10 0 1 1 17.32 0")
    val ListStart = icon("list-start", "M16 12H3", "M16 18H3", "M10 6H3", "M21 18V8a2 2 0 0 0-2-2h-5", "m16 8-2-2 2-2")
    val Folder = icon("folder", "M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z")
    val ListVideo = icon("list-video", "M12 12H3", "M16 6H3", "M12 18H3", "m16 12 5 3-5 3v-6Z")
    val Shuffle = icon(
        "shuffle", "m18 14 4 4-4 4", "m18 2 4 4-4 4", "M2 18h1.973a4 4 0 0 0 3.3-1.7l5.454-8.6a4 4 0 0 1 3.3-1.7H22",
        "M2 6h1.972a4 4 0 0 1 3.6 2.2", "M22 18h-6.041a4 4 0 0 1-3.3-1.8l-.359-.45",
    )
    val Info = icon("info", circle(12f, 12f, 10f), "M12 16v-4", "M12 8h.01")
    val Close = icon("x", "M18 6 6 18", "m6 6 12 12")

    // Search and a title's actions (web/src/components/ItemActions.tsx).
    val Search = icon("search", circle(11f, 11f, 8f), "m21 21-4.3-4.3")
    val CircleCheck = icon("circle-check", circle(12f, 12f, 10f), "m9 12 2 2 4-4")
    val Bookmark = icon("bookmark", "m19 21-7-4-7 4V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v16z")
    val BookmarkCheck = icon("bookmark-check", "m19 21-7-4-7 4V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2Z", "m9 10 2 2 4-4")
    val More = icon("ellipsis", circle(12f, 12f, 1f), circle(19f, 12f, 1f), circle(5f, 12f, 1f))

    // Elsewhere.
    val Check = icon("check", "M20 6 9 17l-5-5")
    val Settings = icon(
        "settings",
        "M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z",
        circle(12f, 12f, 3f),
    )
    val Lock = icon("lock", rect(3f, 11f, 18f, 11f, 2f), "M7 11V7a5 5 0 0 1 10 0v4")
    val ChevronLeft = icon("chevron-left", "m15 18-6-6 6-6")
    val Delete = icon("delete", "M10 5a2 2 0 0 0-1.344.519l-6.328 5.74a1 1 0 0 0 0 1.481l6.328 5.741A2 2 0 0 0 10 19h10a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2z", "m12 9 6 6", "m18 9-6 6")
}
