package org.arcmediaserver.android.ui.person

import androidx.compose.foundation.background
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.arcmediaserver.android.data.PersonCredit
import org.arcmediaserver.android.data.PersonRows
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.arcmediaserver.android.AppContainer
import org.arcmediaserver.android.R
import org.arcmediaserver.android.data.ArcLog
import org.arcmediaserver.android.data.Item
import org.arcmediaserver.android.data.PersonPage
import org.arcmediaserver.android.data.cardFor
import org.arcmediaserver.android.ui.arcViewModel
import org.arcmediaserver.android.ui.components.Artwork
import org.arcmediaserver.android.ui.components.LoadState
import org.arcmediaserver.android.ui.components.Loaded
import org.arcmediaserver.android.ui.components.MediaCardView
import org.arcmediaserver.android.ui.components.requestWhenReady
import org.arcmediaserver.android.ui.components.toUiError
import org.arcmediaserver.android.ui.theme.ArcColors

class PersonViewModel(private val container: AppContainer, private val personId: Long) : ViewModel() {
    private val _state = MutableStateFlow<LoadState<PersonPage>>(LoadState.Loading)
    val state: StateFlow<LoadState<PersonPage>> = _state

    init {
        load()
    }

    fun load() {
        val api = container.api() ?: return
        _state.value = LoadState.Loading
        viewModelScope.launch {
            _state.value = try {
                LoadState.Ready(api.person(personId))
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                ArcLog.Android.warn("person failed to load: ${e.javaClass.simpleName}")
                LoadState.Failed(e.toUiError())
            }
        }
    }
}

@Composable
fun PersonRoute(personId: Long, onOpen: (Item) -> Unit) {
    val vm = arcViewModel { PersonViewModel(it, personId) }
    val state by vm.state.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().background(ArcColors.Background)) {
        Loaded(state, onRetry = vm::load) { PersonScreen(it, onOpen) }
    }
}

/**
 * Someone's page, artwork-led as the Apple TV app's PersonView: their photo in a large circle beside
 * their name and "Acting · 14 titles in your libraries"; a line naming the focused title and their
 * part in it; rows of the films and shows they act in and what they made behind the camera, each
 * newest first. The background is the focused title's backdrop, faded at the left and bottom.
 */
@Composable
fun PersonScreen(page: PersonPage, onOpen: (Item) -> Unit) {
    val first = remember { FocusRequester() }
    val rows = remember(page) { PersonRows.rows(page) }
    var focused by remember(page) { mutableStateOf(rows.firstOrNull()?.let { it.credits.first() to it.kind }) }
    val bg = ArcColors.Background
    Box(Modifier.fillMaxSize()) {
        val backdrop = focused?.first?.item?.let { it.images?.backdrop ?: it.images?.seriesBackdrop }
        if (backdrop != null) {
            Crossfade(backdrop, label = "backdrop") { url ->
                Artwork(url, label = "", modifier = Modifier.fillMaxSize())
            }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to bg.copy(alpha = 0.35f), 0.45f to bg.copy(alpha = 0.55f), 1f to bg.copy(alpha = 0.97f))))
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to bg.copy(alpha = 0.9f), 0.5f to bg.copy(alpha = 0.4f), 0.8f to Color.Transparent)))
        }
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(start = 48.dp, top = 40.dp, end = 48.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(page.person.image, label = initials(page.person.name), modifier = Modifier.size(150.dp).clip(CircleShape))
                Spacer(Modifier.width(28.dp))
                Column {
                    Text(page.person.name, style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Text(summary(page), style = MaterialTheme.typography.titleMedium, color = ArcColors.Foreground.copy(alpha = 0.8f))
                    Spacer(Modifier.height(10.dp))
                    Text(focused?.let { (c, kind) -> partLine(c, kind) }.orEmpty(), style = MaterialTheme.typography.bodyLarge, color = ArcColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 48.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                itemsIndexed(rows, key = { _, r -> r.kind.name }) { r, row ->
                    Column {
                        Text(rowTitle(row.kind), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 48.dp, bottom = 10.dp))
                        LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            itemsIndexed(row.credits, key = { _, c -> c.item.id }) { i, c ->
                                val mod = Modifier.onFocusChanged { if (it.isFocused) focused = c to row.kind }
                                MediaCardView(
                                    cardFor(c.item), landscape = false, onClick = { onOpen(c.item) }, width = 130.dp,
                                    modifier = if (r == 0 && i == 0) mod.focusRequester(first) else mod,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(page.person.id) { if (rows.isNotEmpty()) first.requestWhenReady() }
}

private fun initials(name: String) = name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }

@Composable
private fun roleName(role: String): String? = when (role) {
    "actor" -> stringResource(R.string.person_role_actor)
    "director" -> stringResource(R.string.person_role_director)
    "writer" -> stringResource(R.string.person_role_writer)
    "creator" -> stringResource(R.string.person_role_creator)
    else -> null
}

/** "Acting · 14 titles in your libraries". */
@Composable
private fun summary(page: PersonPage): String {
    val count = PersonRows.titleCount(page)
    val titles = pluralStringResource(R.plurals.person_in_libraries, count, count)
    val known = when (PersonRows.mainRole(page)) {
        "actor" -> stringResource(R.string.person_known_actor)
        "director" -> stringResource(R.string.person_known_director)
        "writer" -> stringResource(R.string.person_known_writer)
        "creator" -> stringResource(R.string.person_known_creator)
        else -> null
    }
    return if (known != null) stringResource(R.string.person_summary, known, titles) else titles
}

/** "Heat (1995) · as Neil McCauley" or "Heat (1995) · Director". */
@Composable
private fun partLine(c: PersonCredit, kind: PersonRows.Kind): String {
    val year = c.item.year
    val title = if (year != null && year > 0) stringResource(R.string.person_title_year, c.item.title, year) else c.item.title
    val part = when (val p = PersonRows.part(c, kind)) {
        is PersonRows.Part.Character -> stringResource(R.string.person_part_as, p.name)
        is PersonRows.Part.Roles -> p.roles.mapNotNull { roleName(it) }.joinToString(", ").ifEmpty { null }
    }
    return listOfNotNull(title, part).joinToString(" · ")
}

@Composable
private fun rowTitle(kind: PersonRows.Kind): String = stringResource(
    when (kind) {
        PersonRows.Kind.Movies -> R.string.person_row_movies
        PersonRows.Kind.Shows -> R.string.person_row_shows
        PersonRows.Kind.Crew -> R.string.person_row_crew
    },
)
