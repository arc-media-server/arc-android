package org.arcmediaserver.android.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import org.arcmediaserver.android.data.HomeRowSpec
import org.arcmediaserver.android.data.Images
import org.arcmediaserver.android.data.Item
import org.arcmediaserver.android.data.Library
import org.arcmediaserver.android.data.PlaybackPlan
import org.arcmediaserver.android.data.Profile
import org.arcmediaserver.android.data.RecentCard
import org.arcmediaserver.android.data.RowSource
import org.arcmediaserver.android.data.CardStyle
import org.arcmediaserver.android.data.Season
import org.arcmediaserver.android.data.SubtitleTrack
import org.arcmediaserver.android.data.AudioTrack
import org.arcmediaserver.android.data.Chapter
import org.arcmediaserver.android.data.Marker
import org.arcmediaserver.android.data.UserData
import org.arcmediaserver.android.data.MediaFile
import org.arcmediaserver.android.data.MediaStream
import org.arcmediaserver.android.data.Person
import org.arcmediaserver.android.data.SearchResults
import org.arcmediaserver.android.data.Trickplay
import org.arcmediaserver.android.data.TrickplayInfo
import org.arcmediaserver.android.data.cardFor
import org.arcmediaserver.android.ui.browse.BrowseFrame
import org.arcmediaserver.android.ui.browse.HomeRowState
import org.arcmediaserver.android.ui.components.LoadState
import kotlin.math.abs

/**
 * Made-up titles with generated artwork, so screenshots show what a library looks like without
 * anyone's real media or metadata. Artwork addresses are "art:<kind>:<title>"; [art] draws them.
 */
object Fixtures {
    private fun images(title: String) = Images(
        primary = "art:poster:$title",
        backdrop = "art:backdrop:$title",
        backdropFull = "art:backdrop:$title",
        // Some titles have a clear logo, as most matched ones do; the rest show their name.
        logo = if (title.length % 2 == 0) "art:logo:$title" else null,
    )

    private fun movie(id: String, title: String, year: Int, minutes: Int, overview: String, pos: Long = 0, played: Boolean = false, rating: String = "15") = Item(
        id = id, libraryId = 1, kind = "movie", title = title, year = year, runtimeMin = minutes, overview = overview, contentRating = rating,
        images = images(title), userData = UserData(pos, played), genres = listOf("Science Fiction", "Drama"),
    )

    val movies = listOf(
        movie("m1", "The Long Orbit", 2019, 132, "A salvage crew drifting past Neptune answers a distress call that was sent forty years ago, from their own ship.", pos = 52 * 60_000),
        movie("m2", "Paper Lanterns", 2021, 104, "Two sisters reopen their grandmother's lantern shop for one last festival night."),
        movie("m3", "Northbound", 2016, 118, "A snowplough driver and a runaway share the last road out before the pass closes.", played = true),
        movie("m4", "Glass Harbour", 2023, 141, "A harbour town's lighthouse keeper finds the sea has started giving things back."),
        movie("m5", "Signal Fire", 2018, 97, "Hikers lost on a ridge must decide whom to trust when the rescue doesn't come."),
        movie("m6", "Saltwater Kings", 2020, 126, "The rise and fall of a family of oyster farmers, told over three summers."),
        movie("m7", "Quiet Machines", 2022, 109, "An engineer teaches the factory robots to paint. The factory notices."),
        movie("m8", "Last Light on Vela", 2017, 150, "Colonists on a tidally locked world race the terminator line."),
    )

    private val showImages = images("Hollow Creek")

    fun episode(s: Int, e: Int, title: String, pos: Long = 0, played: Boolean = false, minutes: Int = 52) = Item(
        id = "ep:hc:$s:$e", libraryId = 2, kind = "episode", parentId = "hc", title = title, seriesTitle = "Hollow Creek",
        seasonNumber = s, episodeNumber = e, runtimeMin = minutes, userData = UserData(pos, played),
        overview = "The town meeting goes badly when the old mill's owner turns up with a plan.",
        images = Images(primary = "art:still:$title", seriesPrimary = showImages.primary, seriesBackdrop = showImages.backdrop, seriesLogo = "art:logo:Hollow Creek"),
    )

    val show = Item(
        id = "hc", libraryId = 2, kind = "series", title = "Hollow Creek", year = 2022, contentRating = "12",
        overview = "In a valley town where the river runs backwards one night a year, a returning deputy reopens the case that made her leave.",
        genres = listOf("Mystery", "Drama"), unwatchedCount = 13, images = showImages, rating = 8.1,
    )

    val seasons = listOf(
        Season(1, "Season 1", episodes = listOf(
            episode(1, 1, "The River Turns", played = true),
            episode(1, 2, "Low Water", played = true),
            episode(1, 3, "Mill Road", pos = 21 * 60_000),
            episode(1, 4, "Fog Signals"),
            episode(1, 5, "The Backwards Night"),
            episode(1, 6, "Undertow"),
        )),
        Season(2, "Season 2", episodes = listOf(episode(2, 1, "Thaw"), episode(2, 2, "Headwaters"), episode(2, 3, "Silt"))),
        Season(0, "Specials", episodes = listOf(episode(0, 1, "Making the Valley"))),
    )

    val libraries = listOf(Library(1, "Movies", "movies", 8), Library(2, "TV Shows", "shows", 5), Library(3, "Home Videos", "videos", 12))

    val profiles = listOf(
        Profile("p1", "Jonathan", "violet", isAdmin = true),
        Profile("p2", "Sam", "teal", hasPin = true),
        Profile("p3", "Kids", "amber"),
        Profile("p4", "Guest", "slate"),
    )

    val frame = BrowseFrame(libraries, profiles[0], canSwitchProfile = true)

    val homeRows: List<HomeRowState> = listOf(
        HomeRowState(
            HomeRowSpec("continue", RowSource.Continue(true), CardStyle.Thumb),
            LoadState.Ready(listOf(cardFor(movies[0]), cardFor(seasons[0].episodes[2]), cardFor(episode(3, 4, "The Ferry", pos = 10 * 60_000).copy(seriesTitle = "Brightwater", images = images("Brightwater").copy(primary = "art:still:The Ferry"))), cardFor(movies[3].copy(userData = UserData(80 * 60_000))))),
        ),
        HomeRowState(
            HomeRowSpec("recent:1", RowSource.Recent(libraries[0], false), CardStyle.Poster, seasonPosters = true),
            LoadState.Ready(movies.drop(1).map { cardFor(it) }),
        ),
        HomeRowState(
            HomeRowSpec("recent:2", RowSource.Recent(libraries[1], false), CardStyle.Poster, seasonPosters = true),
            LoadState.Ready(listOf(cardFor(RecentCard("season", show, seasonNumber = 2, episodes = 3, seasonPoster = "art:poster:Hollow Creek S2")))),
        ),
    )

    val plan = PlaybackPlan(
        method = "remux", reasons = listOf("MKV file"), url = "/api/transcode/s/index.m3u8", hls = true, sessionId = "s",
        offsetMs = 0, durationMs = 52 * 60_000L, fileId = 1, audioStream = 1,
        audioTracks = listOf(AudioTrack(1, "eng", codec = "eac3", channels = 6, default = true), AudioTrack(2, "fra", codec = "aac", channels = 2)),
        subtitleTracks = listOf(SubtitleTrack("s:3", "eng"), SubtitleTrack("s:4", "eng", forced = true)),
        chapters = listOf(Chapter(0, 300_000), Chapter(300_000, 1_200_000), Chapter(1_200_000, 2_400_000), Chapter(2_400_000, 3_120_000)),
        markers = listOf(Marker("intro", 60_000, 150_000), Marker("credits", 3_000_000, 3_120_000)),
        sourceKbps = 9000,
    )

    /** A movie as GET /items/{id} has it: on the watchlist, with one file and its tracks (for Media Info). */
    val movieWithFile = movies[0].copy(
        userData = UserData(52 * 60_000, watchlisted = true),
        files = listOf(
            MediaFile(
                id = 11, durationMs = 132 * 60_000L, container = "mkv", size = 18_400_000_000, bitrate = 18_600_000,
                path = "/media/Movies/The Long Orbit (2019)/The Long Orbit (2019) - 2160p.mkv",
                streams = listOf(
                    MediaStream(0, "video", "hevc", "Main 10", width = 3840, height = 1600, aspectRatio = 2.4, frameRate = 23.976, bitDepth = 10, colorPrimaries = "bt2020", colorTransfer = "smpte2084", pixFmt = "yuv420p10le", bitrate = 15_200_000),
                    MediaStream(1, "audio", "truehd", "Dolby TrueHD + Dolby Atmos", language = "eng", channels = 8),
                    MediaStream(2, "audio", "ac3", language = "eng", channels = 6, bitrate = 640_000, title = "Commentary"),
                    MediaStream(3, "audio", "eac3", language = "fra", channels = 6, bitrate = 768_000),
                    MediaStream(4, "subtitle", "subrip", language = "eng"),
                    MediaStream(5, "subtitle", "hdmv_pgs_subtitle", language = "eng", forced = true),
                    MediaStream(6, "subtitle", "subrip", language = "spa"),
                ),
            ),
        ),
    )

    val searchResults = SearchResults(
        movies = movies.take(3),
        shows = listOf(show),
        episodes = listOf(seasons[0].episodes[3], seasons[1].episodes[0]),
        people = listOf(Person(1, "Mara Ellison", count = 3, knownFor = "actor"), Person(2, "Theo Lund", count = 2, knownFor = "director"), Person(3, "June Okafor", "art:poster:June Okafor", 1)),
    )

    /** Timeline thumbnails: 5x5 tiles of 320x180 to a sheet, one every 10 seconds. */
    val trickplay = Trickplay(
        TrickplayInfo(intervalMs = 10_000, width = 320, height = 180, columns = 5, rows = 5, count = 312, sheets = 13),
        sheets = (0 until 13).map { "art:sheet:$it" },
    )

    /** Draws artwork for an "art:" address: a gradient in a colour from the title, and the title. */
    fun art(url: String): Bitmap {
        val (kind, title) = url.removePrefix("art:").split(":", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        if (kind == "logo") {
            // A clear logo: the title set in heavy serif capitals on transparency.
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                textSize = 120f
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                letterSpacing = 0.08f
                setShadowLayer(12f, 0f, 4f, 0x99000000.toInt())
            }
            val text = title.uppercase()
            val w = paint.measureText(text).toInt() + 40
            val bmp = Bitmap.createBitmap(w, 170, Bitmap.Config.ARGB_8888)
            Canvas(bmp).drawText(text, 20f, 135f, paint)
            return bmp
        }
        if (kind == "sheet") {
            // A sheet of timeline thumbnails: each tile its own frame of a made-up scene.
            val bmp = Bitmap.createBitmap(5 * 320, 5 * 180, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; textSize = 34f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
            for (i in 0 until 25) {
                val x = (i % 5) * 320f
                val y = (i / 5) * 180f
                val hue = (title.toIntOrNull() ?: 0) * 25f + i * 9f
                val a = android.graphics.Color.HSVToColor(floatArrayOf(hue % 360, 0.5f, 0.6f))
                val b = android.graphics.Color.HSVToColor(floatArrayOf((hue + 60) % 360, 0.6f, 0.2f))
                canvas.drawRect(x, y, x + 320, y + 180, Paint().apply { shader = LinearGradient(x, y, x + 320, y + 180, a, b, Shader.TileMode.CLAMP) })
                canvas.drawCircle(x + 70 + i * 7, y + 120, 46f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66FFFFFF })
                canvas.drawText("Mill Road", x + 16, y + 44, label)
            }
            return bmp
        }
        val (w, h) = when (kind) {
            "poster" -> 400 to 600
            "still" -> 640 to 360
            else -> 1280 to 720
        }
        val hue = (abs(title.hashCode()) % 360).toFloat()
        val a = android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.55f, 0.55f))
        val b = android.graphics.Color.HSVToColor(floatArrayOf((hue + 40) % 360, 0.65f, 0.16f))
        val c = android.graphics.Color.HSVToColor(floatArrayOf((hue + 200) % 360, 0.45f, 0.85f))
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply { shader = LinearGradient(0f, 0f, w * 0.3f, h.toFloat(), a, b, Shader.TileMode.CLAMP) })
        canvas.drawCircle(w * 0.72f, h * 0.3f, h * 0.45f, Paint().apply { shader = RadialGradient(w * 0.72f, h * 0.3f, h * 0.45f, c, 0x00000000, Shader.TileMode.CLAMP); alpha = 150 })
        canvas.drawRect(0f, h * 0.72f, w.toFloat(), h.toFloat(), Paint().apply { shader = LinearGradient(0f, h * 0.72f, 0f, h.toFloat(), 0x00000000, 0x99000000.toInt(), Shader.TileMode.CLAMP) })
        if (kind == "poster") {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; textSize = 44f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
            var y = h - 120f
            title.uppercase().split(" ").chunked(2).take(3).forEach { line ->
                canvas.drawText(line.joinToString(" "), 32f, y, paint)
                y += 52f
            }
        }
        return bmp
    }
}
