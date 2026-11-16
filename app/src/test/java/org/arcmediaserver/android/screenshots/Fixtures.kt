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
 * Real films and a cartoon series whose artwork is free to show: public-domain classics and the
 * Blender Studio open movies (Creative Commons), listed with their sources and licences in
 * `src/test/resources/artwork/SOURCES.md`. The synopses are our own. Artwork addresses are
 * "art:<kind>:<title>"; [art] loads them, or draws a stand-in when there's no file.
 */
object Fixtures {
    private fun images(title: String, logo: Boolean = false) = Images(
        primary = "art:poster:$title",
        backdrop = "art:backdrop:$title",
        backdropFull = "art:backdrop:$title",
        logo = if (logo) "art:logo:$title" else null,
    )

    private fun movie(id: String, title: String, year: Int, minutes: Int, overview: String, genres: List<String>, pos: Long = 0, played: Boolean = false, rating: String = "PG") = Item(
        id = id, libraryId = 1, kind = "movie", title = title, year = year, runtimeMin = minutes, overview = overview, contentRating = rating,
        images = images(title), userData = UserData(pos, played), genres = genres,
    )

    val movies = listOf(
        movie("m1", "Metropolis", 1927, 153, "In a towering city split between the planners above and the workers below, the son of its master follows a young teacher into the depths and finds a revolution being built in her image.", listOf("Science Fiction", "Drama"), pos = 52 * 60_000),
        movie("m2", "Big Buck Bunny", 2008, 10, "A gentle giant of a rabbit has his quiet morning spoiled by three bullying rodents, and decides it's time to get even.", listOf("Animation", "Comedy"), rating = "U"),
        movie("m3", "Sintel", 2010, 15, "A lone traveller crosses a frozen wilderness to find the baby dragon that was taken from her.", listOf("Animation", "Fantasy"), rating = "12"),
        movie("m4", "Night of the Living Dead", 1968, 96, "Strangers barricade themselves in a farmhouse as the recently dead rise and close in through the night.", listOf("Horror"), played = true, rating = "15"),
        movie("m5", "The General", 1926, 79, "When Union spies steal his beloved locomotive, a Southern engineer gives chase alone, across a whole railway and through the war.", listOf("Comedy", "Action"), rating = "U"),
        movie("m6", "Cosmos Laundromat", 2015, 12, "A sheep on a barren island, tired of living, meets a salesman who offers him every life he could want.", listOf("Animation", "Science Fiction"), rating = "12"),
        movie("m7", "The Cabinet of Dr. Caligari", 1920, 76, "A travelling showman and his sleepwalking attraction arrive at a town fair, and the murders begin.", listOf("Horror", "Mystery")),
        movie("m8", "Sprite Fright", 2021, 29, "A party of teenagers camping in the woods discovers that the forest's tiny guardians don't care for litter.", listOf("Animation", "Comedy", "Horror"), rating = "12"),
    )

    /** Two more, so a library's grid fills out. */
    val moreMovies = listOf(
        movie("m9", "Elephants Dream", 2006, 11, "Two men wander a vast, shifting machine; the older one insists he understands it, and the younger stops believing him.", listOf("Animation", "Science Fiction"), rating = "12"),
        movie("m10", "Sherlock Jr.", 1924, 45, "A cinema projectionist who longs to be a detective dozes off and walks into the film on the screen.", listOf("Comedy"), rating = "U"),
    )

    private val showImages = images("Superman", logo = true)

    private val synopses = mapOf(
        "The Mad Scientist" to "A scientist with an energy cannon threatens to wreck the city unless the Daily Planet stops printing about him.",
        "The Mechanical Monsters" to "Flying robots raid banks and jewellers across the city, and Lois follows one home.",
        "Billion Dollar Limited" to "Bandits go after a train carrying a fortune in gold to the mint, with Lois aboard.",
        "The Arctic Giant" to "A dinosaur frozen in the Arctic thaws out in a museum and walks into the city.",
        "The Bulleteers" to "A gang in a rocket car demands the city's treasury, or they'll keep smashing buildings.",
        "The Magnetic Telescope" to "An astronomer's machine pulls comets towards the Earth, and the police want it switched off.",
        "Electric Earthquake" to "A man who says Manhattan belongs to his people sets off quakes from under the river.",
        "Volcano" to "A volcano island erupts as the press gathers to watch, and Lois gets too close.",
        "Terror on the Midway" to "Escaped circus animals, a gorilla among them, run loose through the fairground.",
        "Japoteurs" to "Saboteurs plan to steal the world's largest bomber on its first flight, with Lois hidden on board.",
        "Showdown" to "A crook dressed as Superman goes on a robbery spree, and the real one sets things straight.",
        "Eleventh Hour" to "Clark Kent is held in Japan, slipping out at night to sabotage the shipyards.",
    )

    fun episode(s: Int, e: Int, title: String, pos: Long = 0, played: Boolean = false, minutes: Int = 9) = Item(
        id = "ep:sm:$s:$e", libraryId = 2, kind = "episode", parentId = "sm", title = title, seriesTitle = "Superman",
        seasonNumber = s, episodeNumber = e, runtimeMin = minutes, userData = UserData(pos, played),
        overview = synopses[title] ?: "Superman steps in when Metropolis faces a threat only he can stop.",
        images = Images(primary = "art:still:$title", seriesPrimary = showImages.primary, seriesBackdrop = showImages.backdrop, seriesLogo = showImages.logo),
    )

    val show = Item(
        id = "sm", libraryId = 2, kind = "series", title = "Superman", year = 1941, contentRating = "U",
        overview = "The Fleischer and Famous Studios cartoons: mad scientists, giant robots and runaway trains, and the Man of Steel in glorious Technicolor.",
        genres = listOf("Animation", "Action"), unwatchedCount = 13, images = showImages, rating = 7.9,
    )

    val seasons = listOf(
        Season(1, "Season 1", episodes = listOf(
            episode(1, 1, "The Mad Scientist", played = true),
            episode(1, 2, "The Mechanical Monsters", played = true),
            episode(1, 3, "Billion Dollar Limited", pos = 4 * 60_000),
            episode(1, 4, "The Arctic Giant"),
            episode(1, 5, "The Bulleteers"),
            episode(1, 6, "The Magnetic Telescope"),
            episode(1, 7, "Electric Earthquake"),
            episode(1, 8, "Volcano"),
            episode(1, 9, "Terror on the Midway"),
        )),
        Season(2, "Season 2", episodes = listOf(episode(2, 1, "Japoteurs"), episode(2, 2, "Showdown"), episode(2, 3, "Eleventh Hour"))),
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
            LoadState.Ready(listOf(cardFor(movies[0]), cardFor(seasons[0].episodes[2]), cardFor(movies[2].copy(userData = UserData(9 * 60_000))), cardFor(movies[3].copy(userData = UserData(80 * 60_000))))),
        ),
        HomeRowState(
            HomeRowSpec("recent:1", RowSource.Recent(libraries[0], false), CardStyle.Poster, seasonPosters = true),
            LoadState.Ready(movies.drop(1).map { cardFor(it) }),
        ),
        HomeRowState(
            HomeRowSpec("recent:2", RowSource.Recent(libraries[1], false), CardStyle.Poster, seasonPosters = true),
            LoadState.Ready(listOf(cardFor(RecentCard("season", show, seasonNumber = 2, episodes = 3, seasonPoster = "art:poster:Superman")))),
        ),
    )

    val plan = PlaybackPlan(
        method = "remux", reasons = listOf("MKV file"), url = "/api/transcode/s/index.m3u8", hls = true, sessionId = "s",
        offsetMs = 0, durationMs = 9 * 60_000L, fileId = 1, audioStream = 1,
        audioTracks = listOf(AudioTrack(1, "eng", codec = "eac3", channels = 6, default = true), AudioTrack(2, "fra", codec = "aac", channels = 2)),
        subtitleTracks = listOf(SubtitleTrack("s:3", "eng"), SubtitleTrack("s:4", "eng", forced = true)),
        chapters = listOf(Chapter(0, 60_000), Chapter(60_000, 240_000), Chapter(240_000, 420_000), Chapter(420_000, 540_000)),
        markers = listOf(Marker("intro", 0, 45_000), Marker("credits", 510_000, 540_000)),
        sourceKbps = 9000,
    )

    /** A movie as GET /items/{id} has it: on the watchlist, with one file and its tracks (for Media Info). */
    val movieWithFile = movies[0].copy(
        userData = UserData(52 * 60_000, watchlisted = true),
        files = listOf(
            MediaFile(
                id = 11, durationMs = 153 * 60_000L, container = "mkv", size = 21_400_000_000, bitrate = 18_600_000,
                path = "/media/Movies/Metropolis (1927)/Metropolis (1927) - 2160p.mkv",
                streams = listOf(
                    MediaStream(0, "video", "hevc", "Main 10", width = 2880, height = 2160, aspectRatio = 1.33, frameRate = 23.976, bitDepth = 10, colorPrimaries = "bt2020", colorTransfer = "smpte2084", pixFmt = "yuv420p10le", bitrate = 15_200_000),
                    MediaStream(1, "audio", "truehd", "Dolby TrueHD", language = "eng", channels = 6, title = "2010 orchestral score"),
                    MediaStream(2, "audio", "ac3", language = "eng", channels = 2, bitrate = 192_000, title = "Commentary"),
                    MediaStream(3, "audio", "flac", language = "zxx", channels = 2, title = "1927 score, piano"),
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
        people = listOf(Person(1, "Buster Keaton", count = 2, knownFor = "actor"), Person(2, "Fritz Lang", count = 1, knownFor = "director"), Person(3, "Thea von Harbou", count = 1, knownFor = "writer")),
    )

    /** Timeline thumbnails: 5x5 tiles of 320x180 to a sheet, one every 10 seconds. */
    val trickplay = Trickplay(
        TrickplayInfo(intervalMs = 10_000, width = 320, height = 180, columns = 5, rows = 5, count = 312, sheets = 13),
        sheets = (0 until 13).map { "art:sheet:$it" },
    )

    /**
     * Artwork for an "art:" address. Titles with open-licence artwork (Creative Commons or public
     * domain, listed in `src/test/resources/artwork/SOURCES.md`) use it, from
     * `artwork/<kind>/<title-slug>.jpg`; the rest get a gradient in a colour from the title.
     */
    fun art(url: String): Bitmap {
        val (kind, title) = url.removePrefix("art:").split(":", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        val slug = title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        Fixtures::class.java.getResourceAsStream("/artwork/$kind/$slug.jpg")?.use { stream ->
            android.graphics.BitmapFactory.decodeStream(stream)?.let { return it }
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
            val still = Fixtures::class.java.getResourceAsStream("/artwork/still/billion-dollar-limited.jpg")?.use { android.graphics.BitmapFactory.decodeStream(it) }
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
