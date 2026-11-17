package org.arcmediaserver.android.ui.browse

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.arcmediaserver.android.R
import org.arcmediaserver.android.data.Facet
import org.arcmediaserver.android.data.Facets
import org.arcmediaserver.android.ui.components.channelsName
import org.arcmediaserver.android.ui.components.codecName
import org.arcmediaserver.android.ui.components.ArcIcons
import org.arcmediaserver.android.ui.components.requestWhenReady
import org.arcmediaserver.android.ui.components.Popover
import org.arcmediaserver.android.ui.components.PopoverItem
import org.arcmediaserver.android.ui.components.PopoverPlace
import org.arcmediaserver.android.ui.theme.ArcColors

/** The sorts a library offers, in the web's order (Rotten Tomatoes waits while rating sources are paused). */
val LibrarySorts = listOf("title", "added", "watched", "release", "year", "rating", "runtime", "shuffle")

@Composable
fun sortLabel(sort: String): String = stringResource(
    when (sort) {
        "added" -> R.string.sort_added
        "watched" -> R.string.sort_watched
        "release" -> R.string.sort_release
        "year" -> R.string.sort_year
        "rating" -> R.string.sort_rating
        "runtime" -> R.string.sort_runtime
        "shuffle" -> R.string.sort_shuffle
        else -> R.string.sort_title
    },
)

/** A sort's two directions, its usual one first (web sortDirections). */
@Composable
private fun directions(sort: String): Pair<String, String> {
    fun s(a: Int, b: Int) = a to b
    val (a, b) = when (sort) {
        "title" -> s(R.string.direction_a_to_z, R.string.direction_z_to_a)
        "rating" -> s(R.string.direction_highest, R.string.direction_lowest)
        "runtime" -> s(R.string.direction_longest, R.string.direction_shortest)
        "watched" -> s(R.string.direction_most_recent, R.string.direction_least_recent)
        else -> s(R.string.direction_newest, R.string.direction_oldest)
    }
    return stringResource(a) to stringResource(b)
}

/** Sort by, then which way (none for Shuffle). Choosing applies it; the panel stays open. */
@Composable
fun SortPanel(query: LibraryQuery, onChoose: (LibraryQuery) -> Unit, onClose: () -> Unit, anchor: androidx.compose.ui.geometry.Rect? = null) {
    val first = remember { FocusRequester() }
    // The Apple TV app's floating list, under the Sort button (owner, 1 November).
    Popover(anchor, onClose = onClose, place = PopoverPlace.Below, width = 300.dp) {
        LazyColumn {
            item { Heading(stringResource(R.string.library_sort_by)) }
            items(LibrarySorts, key = { it }) { sort ->
                MenuItem(if (query.sort == sort) ArcIcons.Check else null, sortLabel(sort), if (query.sort == sort) Modifier.focusRequester(first) else Modifier) {
                    if (sort != query.sort) onChoose(query.copy(sort = sort, reverse = false))
                }
            }
            if (query.sort != "shuffle") {
                item {
                    val (usual, other) = directions(query.sort)
                    Spacer(Modifier.height(10.dp))
                    Heading(stringResource(R.string.library_order))
                    MenuItem(if (!query.reverse) ArcIcons.Check else null, usual) { if (query.reverse) onChoose(query.copy(reverse = false)) }
                    MenuItem(if (query.reverse) ArcIcons.Check else null, other) { if (!query.reverse) onChoose(query.copy(reverse = true)) }
                }
            }
        }
    }
    LaunchedEffect(Unit) { first.requestWhenReady() }
}

private enum class FilterPage { Main, Genre, Decade, Quality, WatchState, Hdr, Video, Audio, Channels }

/**
 * Whether to offer a video or audio filter, as the web's Filters panel: when the library has more
 * than one value (one value would filter nothing out), or one is already chosen.
 */
fun offered(values: List<Facet>, chosen: Any?): Boolean = values.size > 1 || chosen != null

/** Audio channel counts, most first (7.1 before 5.1 before Stereo), as the web lists them. */
fun channelChoices(values: List<Facet>): List<Int> = values.mapNotNull { it.value.toIntOrNull() }.distinct().sortedDescending()

/**
 * The web's library filters that suit a remote: unwatched only, a genre, decade or quality, a
 * watch status, and the video and audio filters the library has (HDR, video format, audio format,
 * channels). Each choice applies at once; Clear Filters turns them all off.
 */
@Composable
fun FilterPanel(query: LibraryQuery, facets: Facets, onChoose: (LibraryQuery) -> Unit, onClose: () -> Unit, anchor: androidx.compose.ui.geometry.Rect? = null) {
    var page by remember { mutableStateOf(FilterPage.Main) }
    val first = remember(page) { FocusRequester() }
    val back = { if (page == FilterPage.Main) onClose() else page = FilterPage.Main }
    val any = stringResource(R.string.filter_any)
    // A page of choices: Any first, then each value; choosing goes back to the main list.
    fun <T> LazyListScope.choices(heading: Int, values: List<T>, chosen: T?, name: @Composable (T) -> String, choose: (T?) -> LibraryQuery) {
        item { Heading(stringResource(heading)) }
        item { MenuItem(if (chosen == null) ArcIcons.Check else null, any, Modifier.focusRequester(first)) { onChoose(choose(null)); page = FilterPage.Main } }
        items(values.size) { i ->
            val v = values[i]
            MenuItem(if (chosen == v) ArcIcons.Check else null, name(v)) { onChoose(choose(v)); page = FilterPage.Main }
        }
    }
    Popover(anchor, onClose = back, place = PopoverPlace.Below, width = 320.dp) {
        LazyColumn(Modifier.heightIn(max = 420.dp)) {
            when (page) {
                FilterPage.Main -> {
                    item { Heading(stringResource(R.string.library_filters)) }
                    item {
                        MenuItem(if (query.unwatched) ArcIcons.Check else null, stringResource(R.string.filter_unwatched), Modifier.focusRequester(first)) {
                            onChoose(query.withUnwatched(!query.unwatched))
                        }
                    }
                    item { Choice(stringResource(R.string.filter_watch_state), query.watchState?.let { watchStateName(it) } ?: any) { page = FilterPage.WatchState } }
                    if (facets.genres.isNotEmpty()) item { Choice(stringResource(R.string.filter_genre), query.genre ?: any) { page = FilterPage.Genre } }
                    if (facets.decades.isNotEmpty()) item { Choice(stringResource(R.string.filter_decade), query.decade?.let { stringResource(R.string.filter_decade_value, it) } ?: any) { page = FilterPage.Decade } }
                    if (facets.resolutions.isNotEmpty()) item { Choice(stringResource(R.string.filter_quality), query.resolution?.let { qualityName(it) } ?: any) { page = FilterPage.Quality } }
                    if (offered(facets.dynamicRanges, query.dynamicRange)) item { Choice(stringResource(R.string.filter_hdr), query.dynamicRange?.let { dynamicRangeName(it) } ?: any) { page = FilterPage.Hdr } }
                    if (offered(facets.videoCodecs, query.videoCodec)) item { Choice(stringResource(R.string.filter_video_format), query.videoCodec?.let { codecName(it).orEmpty() } ?: any) { page = FilterPage.Video } }
                    if (offered(facets.audioCodecs, query.audioCodec)) item { Choice(stringResource(R.string.filter_audio_format), query.audioCodec?.let { codecName(it).orEmpty() } ?: any) { page = FilterPage.Audio } }
                    if (offered(facets.audioChannels, query.audioChannels)) item { Choice(stringResource(R.string.filter_channels), query.audioChannels?.let { channelsName(it) } ?: any) { page = FilterPage.Channels } }
                    if (query.filterCount > 0) {
                        item {
                            Spacer(Modifier.height(10.dp))
                            MenuItem(ArcIcons.Close, stringResource(R.string.filter_clear)) { onChoose(query.cleared()) }
                        }
                    }
                }
                FilterPage.Genre -> choices(R.string.filter_genre, facets.genres.map { it.value }, query.genre, { it }) { query.copy(genre = it) }
                FilterPage.Decade -> choices(
                    R.string.filter_decade, facets.decades.mapNotNull { it.value.toIntOrNull() }.sortedDescending(), query.decade,
                    { stringResource(R.string.filter_decade_value, it) },
                ) { query.copy(decade = it) }
                FilterPage.Quality -> choices(R.string.filter_quality, facets.resolutions.map { it.value }, query.resolution, { qualityName(it) }) { query.copy(resolution = it) }
                FilterPage.WatchState -> choices(R.string.filter_watch_state, listOf("played", "unstarted"), query.watchState, { watchStateName(it) }) { query.withWatchState(it) }
                // A value already chosen stays in its list, even when the library no longer has it.
                FilterPage.Hdr -> choices(R.string.filter_hdr, (facets.dynamicRanges.map { it.value } + listOfNotNull(query.dynamicRange)).distinct(), query.dynamicRange, { dynamicRangeName(it) }) { query.copy(dynamicRange = it) }
                FilterPage.Video -> choices(R.string.filter_video_format, (facets.videoCodecs.map { it.value } + listOfNotNull(query.videoCodec)).distinct(), query.videoCodec, { codecName(it).orEmpty() }) { query.copy(videoCodec = it) }
                FilterPage.Audio -> choices(R.string.filter_audio_format, (facets.audioCodecs.map { it.value } + listOfNotNull(query.audioCodec)).distinct(), query.audioCodec, { codecName(it).orEmpty() }) { query.copy(audioCodec = it) }
                FilterPage.Channels -> choices(R.string.filter_channels, (channelChoices(facets.audioChannels) + listOfNotNull(query.audioChannels)).distinct(), query.audioChannels, { channelsName(it).orEmpty() }) { query.copy(audioChannels = it) }
            }
        }
    }
    LaunchedEffect(page) { first.requestWhenReady() }
}

/** Watched or Not started (the web's watch status names). */
@Composable
private fun watchStateName(value: String) = stringResource(if (value == "played") R.string.filter_watched else R.string.filter_not_started)

/** Dolby Vision, HDR10+, HLG, HDR10, SDR, or "Not known yet" (the web's dynamicRangeLabel). */
@Composable
fun dynamicRangeName(value: String): String = when (value) {
    "dolby_vision" -> "Dolby Vision"
    "hdr10_plus" -> "HDR10+"
    "hlg" -> "HLG"
    "hdr10" -> "HDR10"
    "sdr" -> "SDR"
    "unknown" -> stringResource(R.string.filter_hdr_unknown)
    else -> value
}

private fun qualityName(value: String) = when (value) {
    "4k" -> "4K"
    "1080" -> "1080p"
    "720" -> "720p"
    "sd" -> "SD"
    else -> value
}

@Composable
private fun Heading(text: String) =
    Text(text, style = MaterialTheme.typography.labelLarge, color = ArcColors.Muted, modifier = Modifier.padding(start = 11.dp, top = 5.dp, bottom = 6.dp))

/** A choice in the list: its name on the left, a tick (or its icon) on the right. */
@Composable
private fun MenuItem(icon: androidx.compose.ui.graphics.vector.ImageVector?, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) =
    PopoverItem(label, icon, modifier = modifier, onClick = onClick)

/** A filter that opens its own list, showing what it's set to. */
@Composable
private fun Choice(label: String, value: String, onClick: () -> Unit) =
    MenuItem(null, "$label: $value", onClick = onClick)
