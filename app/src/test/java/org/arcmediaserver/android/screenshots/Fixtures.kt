package org.arcmediaserver.android.screenshots

import coil3.request.transformations
import coil3.asImage
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import org.arcmediaserver.android.data.ArcJson
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
 * The shared screenshot library's films, show and profiles (arc-media-server's
 * docs/SCREENSHOTS.md), as Arc answered for Jordan: recorded into
 * `src/test/resources/screenshot-library/` by [LiveScreenshotTest.recordFixtures]. Titles,
 * descriptions, cast and progress are TMDB's and Arc's, not ours. Artwork isn't kept: each picture
 * is an "art:<kind>:<title>" address that [art] draws a stand-in for.
 */
object Fixtures {
    private fun json(name: String): String =
        Fixtures::class.java.getResourceAsStream("/screenshot-library/$name.json")!!.use { it.readBytes().decodeToString() }

    private fun images(title: String) = Images(
        primary = "art:poster:$title",
        backdrop = "art:backdrop:$title",
        backdropFull = "art:backdrop:$title",
        logo = "art:logo:$title",
    )

    private fun people(credits: List<org.arcmediaserver.android.data.Credit>) = credits.map { c -> c.copy(image = c.image?.let { "art:person:${c.name}" }) }

    private fun film(item: Item) = item.copy(images = images(item.title), credits = people(item.credits))

    /** The films, the ones part-way through first (Dune: Part Two leading), then by title. */
    private val films: List<Item> = ArcJson.decodeFromString(org.arcmediaserver.android.data.ItemPage.serializer(), json("movies")).items
        .sortedBy { if (it.title == "Dune: Part Two") -1L else -(it.userData?.positionMs ?: 0) }
        .map(::film)

    val movies = films.take(8)

    /** Two more, so a library's grid fills out. */
    val moreMovies = films.drop(8).take(2)

    /** Dune: Part Two as GET /items/{id} has it: its cast and crew. */
    val dune: Item = film(ArcJson.decodeFromString(Item.serializer(), json("dune")))

    private val recordedShow = ArcJson.decodeFromString(org.arcmediaserver.android.data.SeasonsResponse.serializer(), json("seasons"))

    val show: Item = ArcJson.decodeFromString(Item.serializer(), json("show")).let { it.copy(images = images(it.title), credits = people(it.credits)) }

    private fun episodeArt(e: Item) = e.copy(
        seriesTitle = show.title,
        images = Images(primary = "art:still:${e.title}", seriesPrimary = show.images?.primary, seriesBackdrop = show.images?.backdrop, seriesLogo = show.images?.logo),
    )

    /**
     * Seasons 1 and 2. The library has Season 1's files; Season 2 is Arc's listing of the episodes
     * it has no file for (Show Missing Episodes), which the tests treat as on disk.
     */
    val seasons: List<Season> = recordedShow.seasons.filter { it.seasonNumber > 0 }.map { s -> s.copy(episodes = s.episodes.map { episodeArt(it).copy(available = true) }) }

    /** The show's specials (Season 0), as Arc lists them. */
    val specials: List<Item> = recordedShow.seasons.filter { it.seasonNumber == 0 }.flatMap { it.episodes }.map { episodeArt(it).copy(available = true) }

    /** Timothée Chalamet's page, as GET /people/{id} has it. */
    val person: org.arcmediaserver.android.data.PersonPage = ArcJson.decodeFromString(org.arcmediaserver.android.data.PersonPage.serializer(), json("person")).let { page ->
        page.copy(
            person = page.person.copy(image = page.person.image?.let { "art:person:${page.person.name}" }),
            credits = page.credits.map { c -> c.copy(item = c.item.copy(images = images(c.item.title))) },
        )
    }

    val libraries = listOf(Library(1, "Movies", "movies", films.size), Library(2, "TV Shows", "shows", 5), Library(3, "Home Videos", "videos", 12))

    /** The library's profiles; Alex is given a PIN here, for the PIN screens. */
    val profiles: List<Profile> = ArcJson.decodeFromString(kotlinx.serialization.builtins.ListSerializer(Profile.serializer()), json("profiles"))
        .map { if (it.name == "Alex") it.copy(hasPin = true) else it }

    val frame = BrowseFrame(libraries, profiles[0], canSwitchProfile = true)

    val homeRows: List<HomeRowState> = listOf(
        HomeRowState(
            HomeRowSpec("continue", RowSource.Continue(true), CardStyle.Backdrop),
            LoadState.Ready(listOf(cardFor(movies[0]), cardFor(seasons[0].episodes[2]), cardFor(movies[2].copy(userData = UserData(96 * 60_000))), cardFor(movies[3].copy(userData = UserData(80 * 60_000))))),
        ),
        HomeRowState(
            HomeRowSpec("recent:1", RowSource.Recent(libraries[0], false), CardStyle.Poster, seasonPosters = true),
            LoadState.Ready(movies.drop(1).map { cardFor(it) }),
        ),
        HomeRowState(
            HomeRowSpec("recent:2", RowSource.Recent(libraries[1], false), CardStyle.Poster, seasonPosters = true),
            LoadState.Ready(listOf(cardFor(RecentCard("season", show, seasonNumber = 1, episodes = seasons[0].episodes.size, seasonPoster = "art:poster:${show.title} Season 1")))),
        ),
    )

    val plan = PlaybackPlan(
        method = "remux", reasons = listOf("MKV file"), url = "/api/transcode/s/index.m3u8", hls = true, sessionId = "s",
        offsetMs = 0, durationMs = 60 * 60_000L, fileId = 1, audioStream = 1,
        audioTracks = listOf(AudioTrack(1, "eng", codec = "eac3", channels = 6, default = true), AudioTrack(2, "fra", codec = "aac", channels = 2)),
        subtitleTracks = listOf(SubtitleTrack("s:3", "eng"), SubtitleTrack("s:4", "eng", forced = true)),
        chapters = listOf(Chapter(0, 300_000), Chapter(300_000, 1_200_000), Chapter(1_200_000, 2_400_000), Chapter(2_400_000, 3_600_000)),
        markers = listOf(Marker("intro", 150_000, 230_000), Marker("credits", 3_480_000, 3_600_000)),
        sourceKbps = 9000,
    )

    /** A movie as GET /items/{id} has it: on the watchlist, with one file and its tracks (for Media Info). */
    val movieWithFile = movies[0].copy(
        userData = UserData(movies[0].userData?.positionMs ?: 0, watchlisted = true),
        files = listOf(
            MediaFile(
                id = 11, durationMs = 167 * 60_000L, container = "mkv", size = 61_200_000_000, bitrate = 18_600_000,
                path = "/media/Movies/Dune Part Two (2024)/Dune Part Two (2024) - 2160p.mkv",
                streams = listOf(
                    MediaStream(0, "video", "hevc", "Main 10", width = 3840, height = 1608, aspectRatio = 2.39, frameRate = 23.976, bitDepth = 10, colorPrimaries = "bt2020", colorTransfer = "smpte2084", pixFmt = "yuv420p10le", bitrate = 15_200_000),
                    MediaStream(1, "audio", "truehd", "Dolby TrueHD + Dolby Atmos", language = "eng", channels = 8),
                    MediaStream(2, "audio", "ac3", language = "eng", channels = 6, bitrate = 640_000),
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
        people = dune.credits.filter { it.image != null }.take(3).mapIndexed { i, c -> Person(c.personId, c.name, c.image, i + 2, c.role) },
    )

    /** Timeline thumbnails: 5x5 tiles of 320x180 to a sheet, one every 10 seconds. */
    val trickplay = Trickplay(
        TrickplayInfo(intervalMs = 10_000, width = 320, height = 180, columns = 5, rows = 5, count = 312, sheets = 13),
        sheets = (0 until 13).map { "art:sheet:$it" },
    )

    /** A stand-in for an "art:<kind>:<title>" address: a gradient in a colour from the title. */
    /**
     * A fixture picture for an image request, through the request's transformations as Coil would
     * (a backdrop's shading), at the 1080p screen size, so screenshots show what the TV draws.
     */
    fun image(request: coil3.request.ImageRequest): coil3.Image {
        var bitmap = art(request.data.toString())
        for (t in request.transformations) {
            bitmap = kotlinx.coroutines.runBlocking { t.transform(bitmap, coil3.size.Size(1920, 1080)) }
        }
        return bitmap.asImage()
    }

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
                canvas.drawText("${i * 10}s", x + 16, y + 44, label)
            }
            return bmp
        }
        val (w, h) = when (kind) {
            "poster", "person" -> 400 to 600
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
