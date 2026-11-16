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
 * Well-known films and a show, so the screenshots look like a real library. Titles, years, credits
 * and running times are real; the synopses are our own. The artwork isn't in git: run
 * `tools/fetch-screenshot-art.py` (it needs a TMDB API key) to download it from TMDB into
 * `src/test/resources/artwork/`. Without it, [art] draws a stand-in for each "art:<kind>:<title>".
 */
object Fixtures {
    private fun images(title: String) = Images(
        primary = "art:poster:$title",
        backdrop = "art:backdrop:$title",
        backdropFull = "art:backdrop:$title",
        logo = "art:logo:$title",
    )

    private fun movie(id: String, title: String, year: Int, minutes: Int, overview: String, genres: List<String>, rating: String, pos: Long = 0, played: Boolean = false) = Item(
        id = id, libraryId = 1, kind = "movie", title = title, year = year, runtimeMin = minutes, overview = overview, contentRating = rating,
        images = images(title), userData = UserData(pos, played), genres = genres,
    )

    val movies = listOf(
        movie("m1", "Dune: Part Two", 2024, 167, "Paul Atreides joins the Fremen of the deep desert and fights to avenge his family, while visions warn him where that path leads.", listOf("Science Fiction", "Adventure"), "12A", pos = 52 * 60_000),
        movie("m2", "Oppenheimer", 2023, 181, "The physicist who led the race to build the atomic bomb, and what it cost him once it was built.", listOf("Drama", "History"), "15"),
        movie("m3", "Everything Everywhere All at Once", 2022, 140, "A laundromat owner in the middle of a tax audit finds she can borrow skills from every other life she might have lived.", listOf("Action", "Adventure", "Science Fiction"), "15"),
        movie("m4", "Interstellar", 2014, 169, "With crops failing across the Earth, a former pilot leaves his family to search for a new home beyond a wormhole.", listOf("Adventure", "Drama", "Science Fiction"), "12A", played = true),
        movie("m5", "Spider-Man: Across the Spider-Verse", 2023, 140, "Miles Morales is pulled across the multiverse and finds the other Spider-People have rules he won't follow.", listOf("Animation", "Action", "Adventure"), "PG"),
        movie("m6", "Blade Runner 2049", 2017, 164, "A replicant hunter uncovers a buried secret that leads him to search for a blade runner missing for thirty years.", listOf("Science Fiction", "Drama"), "15"),
        movie("m7", "The Grand Budapest Hotel", 2014, 100, "A legendary concierge and his lobby boy are caught up in a stolen painting and a family fortune.", listOf("Comedy", "Drama"), "15"),
        movie("m8", "Mad Max: Fury Road", 2015, 121, "A warrior and a drifter flee across the wasteland with a tyrant's wives and his whole army behind them.", listOf("Action", "Adventure", "Science Fiction"), "15"),
    )

    /** Two more, so a library's grid fills out. */
    val moreMovies = listOf(
        movie("m9", "Arrival", 2016, 116, "A linguist is asked to talk to the visitors in one of twelve ships that have landed around the world.", listOf("Drama", "Science Fiction", "Mystery"), "12A"),
        movie("m10", "Parasite", 2019, 133, "A struggling family talk their way, one by one, into jobs with a wealthy household.", listOf("Comedy", "Thriller", "Drama"), "15"),
    )

    private val showImages = images("Severance")

    private val synopses = mapOf(
        "Good News About Hell" to "Mark's team at Lumon gets a new member, and she wants nothing more than to leave.",
        "Half Loop" to "Mark meets a stranger who knows more about his job than he does.",
        "In Perpetuity" to "The team visits the company's founder's wing, and Helly tries another way out.",
        "The You You Are" to "Irving finds a department he's never heard of, and Mark goes to a dinner party.",
        "The Grim Barbarity of Optics and Design" to "Two departments meet, and Mark's outie starts asking questions.",
        "Hide and Seek" to "Helly's escape attempts come to a head, and Cobel takes a risk.",
        "Defiant Jazz" to "A celebration on the severed floor turns up a way to reach the outside.",
        "What's for Dinner?" to "Three innies wake up in the outside world for one night.",
        "The We We Are" to "The innies have hours, at most, to tell someone what Lumon is.",
        "Hello, Ms. Cobel" to "Mark returns to a changed severed floor, and new faces in the office.",
        "Goodbye, Mrs. Selvig" to "Cobel looks for a way back in, and the team relearns its work.",
        "Who Is Alive?" to "The team is sent to find out what happened to a department.",
    )

    fun episode(s: Int, e: Int, title: String, minutes: Int, pos: Long = 0, played: Boolean = false) = Item(
        id = "ep:sv:$s:$e", libraryId = 2, kind = "episode", parentId = "sv", title = title, seriesTitle = "Severance",
        seasonNumber = s, episodeNumber = e, runtimeMin = minutes, userData = UserData(pos, played),
        overview = synopses[title] ?: "Mark and his team at Lumon keep finding more than the job lets them know.",
        images = Images(primary = "art:still:$title", seriesPrimary = showImages.primary, seriesBackdrop = showImages.backdrop, seriesLogo = showImages.logo),
    )

    val show = Item(
        id = "sv", libraryId = 2, kind = "series", title = "Severance", year = 2022, contentRating = "15",
        overview = "At Lumon Industries, some employees have their memories split in two: the people who go to work never know their lives outside, and the other way round.",
        genres = listOf("Drama", "Mystery", "Science Fiction"), unwatchedCount = 13, images = showImages, rating = 8.4,
    )

    val seasons = listOf(
        Season(1, "Season 1", episodes = listOf(
            episode(1, 1, "Good News About Hell", 59, played = true),
            episode(1, 2, "Half Loop", 57, played = true),
            episode(1, 3, "In Perpetuity", 60, pos = 21 * 60_000),
            episode(1, 4, "The You You Are", 50),
            episode(1, 5, "The Grim Barbarity of Optics and Design", 47),
            episode(1, 6, "Hide and Seek", 44),
            episode(1, 7, "Defiant Jazz", 53),
            episode(1, 8, "What's for Dinner?", 50),
            episode(1, 9, "The We We Are", 44),
        )),
        Season(2, "Season 2", episodes = listOf(episode(2, 1, "Hello, Ms. Cobel", 54), episode(2, 2, "Goodbye, Mrs. Selvig", 49), episode(2, 3, "Who Is Alive?", 57))),
    )

    val libraries = listOf(Library(1, "Movies", "movies", 8), Library(2, "TV Shows", "shows", 5), Library(3, "Home Videos", "videos", 12))

    val profiles = listOf(
        Profile("p1", "Alex", "violet", isAdmin = true),
        Profile("p2", "Sam", "teal", hasPin = true),
        Profile("p3", "Kids", "amber"),
        Profile("p4", "Guest", "slate"),
    )

    val frame = BrowseFrame(libraries, profiles[0], canSwitchProfile = true)

    val homeRows: List<HomeRowState> = listOf(
        HomeRowState(
            HomeRowSpec("continue", RowSource.Continue(true), CardStyle.Thumb),
            LoadState.Ready(listOf(cardFor(movies[0]), cardFor(seasons[0].episodes[2]), cardFor(movies[2].copy(userData = UserData(96 * 60_000))), cardFor(movies[3].copy(userData = UserData(80 * 60_000))))),
        ),
        HomeRowState(
            HomeRowSpec("recent:1", RowSource.Recent(libraries[0], false), CardStyle.Poster, seasonPosters = true),
            LoadState.Ready(movies.drop(1).map { cardFor(it) }),
        ),
        HomeRowState(
            HomeRowSpec("recent:2", RowSource.Recent(libraries[1], false), CardStyle.Poster, seasonPosters = true),
            LoadState.Ready(listOf(cardFor(RecentCard("season", show, seasonNumber = 2, episodes = 3, seasonPoster = "art:poster:Severance Season 2")))),
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
        userData = UserData(52 * 60_000, watchlisted = true),
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
        people = listOf(Person(1, "Timothée Chalamet", "art:person:Timothée Chalamet", 2, "actor"), Person(2, "Denis Villeneuve", "art:person:Denis Villeneuve", 3, "director"), Person(3, "Zendaya", "art:person:Zendaya", 2, "actor")),
    )

    /** Timeline thumbnails: 5x5 tiles of 320x180 to a sheet, one every 10 seconds. */
    val trickplay = Trickplay(
        TrickplayInfo(intervalMs = 10_000, width = 320, height = 180, columns = 5, rows = 5, count = 312, sheets = 13),
        sheets = (0 until 13).map { "art:sheet:$it" },
    )

    /**
     * Artwork for an "art:" address: the downloaded file `artwork/<kind>/<title-slug>.png` or
     * `.jpg` when there is one (see the note on [Fixtures]); otherwise a stand-in, a gradient in a
     * colour from the title.
     */
    fun art(url: String): Bitmap {
        val (kind, title) = url.removePrefix("art:").split(":", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        val slug = title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        for (ext in listOf("png", "jpg")) {
            Fixtures::class.java.getResourceAsStream("/artwork/$kind/$slug.$ext")?.use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream)?.let { return it }
            }
        }
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
            val still = Fixtures::class.java.getResourceAsStream("/artwork/still/in-perpetuity.jpg")?.use { android.graphics.BitmapFactory.decodeStream(it) }
            val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; textSize = 34f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
            for (i in 0 until 25) {
                val x = (i % 5) * 320f
                val y = (i / 5) * 180f
                val hue = (title.toIntOrNull() ?: 0) * 25f + i * 9f
                val a = android.graphics.Color.HSVToColor(floatArrayOf(hue % 360, 0.5f, 0.6f))
                val b = android.graphics.Color.HSVToColor(floatArrayOf((hue + 60) % 360, 0.6f, 0.2f))
                canvas.drawRect(x, y, x + 320, y + 180, Paint().apply { shader = LinearGradient(x, y, x + 320, y + 180, a, b, Shader.TileMode.CLAMP) })
                // The episode's still, panned a little further across for each tile.
                still?.let { st ->
                    val sw = st.width * 0.7f
                    val sx = (st.width - sw) * (i / 24f)
                    canvas.drawBitmap(st, android.graphics.Rect(sx.toInt(), 0, (sx + sw).toInt(), (sw * 9 / 16).toInt().coerceAtMost(st.height)), android.graphics.RectF(x, y, x + 320, y + 180), null)
                } ?: canvas.drawText("${i * 10}s", x + 16, y + 44, label)
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
