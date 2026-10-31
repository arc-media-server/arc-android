package org.arcmediaserver.android.ui.browse

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import org.arcmediaserver.android.data.Facets
import org.arcmediaserver.android.ui.components.ArcIcons
import org.arcmediaserver.android.ui.components.requestWhenReady
import org.arcmediaserver.android.ui.details.MenuItem
import org.arcmediaserver.android.ui.details.Overlay
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
fun SortPanel(query: LibraryQuery, onChoose: (LibraryQuery) -> Unit, onClose: () -> Unit) {
    val first = remember { FocusRequester() }
    Overlay(420.dp, onBack = onClose) {
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

private enum class FilterPage { Main, Genre, Decade, Quality }

/**
 * The web's library filters that suit a remote: unwatched only, and a genre, decade or quality
 * (from what the library has). Each choice applies at once; Clear Filters turns them all off.
 */
@Composable
fun FilterPanel(query: LibraryQuery, facets: Facets, onChoose: (LibraryQuery) -> Unit, onClose: () -> Unit) {
    var page by remember { mutableStateOf(FilterPage.Main) }
    val first = remember(page) { FocusRequester() }
    val back = { if (page == FilterPage.Main) onClose() else page = FilterPage.Main }
    Overlay(460.dp, onBack = back) {
        LazyColumn {
            when (page) {
                FilterPage.Main -> {
                    item { Heading(stringResource(R.string.library_filters)) }
                    item {
                        MenuItem(if (query.unwatched) ArcIcons.Check else null, stringResource(R.string.filter_unwatched), Modifier.focusRequester(first)) {
                            onChoose(query.copy(unwatched = !query.unwatched))
                        }
                    }
                    if (facets.genres.isNotEmpty()) item { Choice(stringResource(R.string.filter_genre), query.genre ?: stringResource(R.string.filter_any)) { page = FilterPage.Genre } }
                    if (facets.decades.isNotEmpty()) item { Choice(stringResource(R.string.filter_decade), query.decade?.let { stringResource(R.string.filter_decade_value, it) } ?: stringResource(R.string.filter_any)) { page = FilterPage.Decade } }
                    if (facets.resolutions.isNotEmpty()) item { Choice(stringResource(R.string.filter_quality), query.resolution?.let { qualityName(it) } ?: stringResource(R.string.filter_any)) { page = FilterPage.Quality } }
                    if (query.filterCount > 0) {
                        item {
                            Spacer(Modifier.height(10.dp))
                            MenuItem(ArcIcons.Close, stringResource(R.string.filter_clear)) { onChoose(query.copy(unwatched = false, genre = null, decade = null, resolution = null)) }
                        }
                    }
                }
                FilterPage.Genre -> {
                    item { Heading(stringResource(R.string.filter_genre)) }
                    item { MenuItem(if (query.genre == null) ArcIcons.Check else null, stringResource(R.string.filter_any), Modifier.focusRequester(first)) { onChoose(query.copy(genre = null)); page = FilterPage.Main } }
                    items(facets.genres, key = { it.value }) { g -> MenuItem(if (query.genre == g.value) ArcIcons.Check else null, g.value) { onChoose(query.copy(genre = g.value)); page = FilterPage.Main } }
                }
                FilterPage.Decade -> {
                    item { Heading(stringResource(R.string.filter_decade)) }
                    item { MenuItem(if (query.decade == null) ArcIcons.Check else null, stringResource(R.string.filter_any), Modifier.focusRequester(first)) { onChoose(query.copy(decade = null)); page = FilterPage.Main } }
                    items(facets.decades.mapNotNull { it.value.toIntOrNull() }.sortedDescending(), key = { it }) { d ->
                        MenuItem(if (query.decade == d) ArcIcons.Check else null, stringResource(R.string.filter_decade_value, d)) { onChoose(query.copy(decade = d)); page = FilterPage.Main }
                    }
                }
                FilterPage.Quality -> {
                    item { Heading(stringResource(R.string.filter_quality)) }
                    item { MenuItem(if (query.resolution == null) ArcIcons.Check else null, stringResource(R.string.filter_any), Modifier.focusRequester(first)) { onChoose(query.copy(resolution = null)); page = FilterPage.Main } }
                    items(facets.resolutions, key = { it.value }) { r -> MenuItem(if (query.resolution == r.value) ArcIcons.Check else null, qualityName(r.value)) { onChoose(query.copy(resolution = r.value)); page = FilterPage.Main } }
                }
            }
        }
    }
    LaunchedEffect(page) { first.requestWhenReady() }
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
    Text(text, style = MaterialTheme.typography.titleMedium, color = ArcColors.Muted, modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 6.dp))

/** A filter that opens its own list, showing what it's set to. */
@Composable
private fun Choice(label: String, value: String, onClick: () -> Unit) =
    MenuItem(null, "$label: $value", onClick = onClick)
