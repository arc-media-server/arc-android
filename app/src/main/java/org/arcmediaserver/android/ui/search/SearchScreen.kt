package org.arcmediaserver.android.ui.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import org.arcmediaserver.android.R
import org.arcmediaserver.android.data.Item
import org.arcmediaserver.android.data.Person
import org.arcmediaserver.android.ui.arcViewModel
import org.arcmediaserver.android.ui.components.ArcIcons
import org.arcmediaserver.android.ui.components.Artwork
import org.arcmediaserver.android.ui.components.MediaCardView
import org.arcmediaserver.android.ui.components.Spinner
import org.arcmediaserver.android.ui.components.requestWhenReady
import org.arcmediaserver.android.ui.components.text
import org.arcmediaserver.android.ui.theme.ArcColors

@Composable
fun SearchRoute(onOpen: (Item) -> Unit, onPerson: (Person) -> Unit) {
    val vm = arcViewModel { SearchViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    SearchScreen(state, onType = vm::type, onOpen = onOpen, onPerson = onPerson)
}

/**
 * Search, as Google TV's and Plex's: a field at the top (the TV's keyboard and voice input come
 * with it) and results that follow the typing, grouped as on the web.
 */
@Composable
fun SearchScreen(state: SearchUiState, onType: (String) -> Unit, onOpen: (Item) -> Unit, onPerson: (Person) -> Unit, focusField: Boolean = true) {
    val field = remember { FocusRequester() }
    Column(Modifier.fillMaxSize().padding(top = 36.dp)) {
        SearchField(state, onType, Modifier.padding(horizontal = 48.dp).focusRequester(field))
        Spacer(Modifier.height(12.dp))
        when {
            state.error != null -> Message(stringResource(R.string.search_failed) + " " + state.error.text())
            state.query.isNotEmpty() && state.sections.isEmpty() && !state.searching -> Message(stringResource(R.string.search_empty, state.query))
            state.text.isBlank() -> Message(stringResource(R.string.search_hint))
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 48.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                items(state.sections, key = { it.group }) { section -> SectionRow(section, onOpen, onPerson) }
            }
        }
    }
    if (focusField) LaunchedEffect(Unit) { field.requestWhenReady() }
}

@Composable
private fun SearchField(state: SearchUiState, onType: (String) -> Unit, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    BasicTextField(
        value = state.text,
        onValueChange = onType,
        singleLine = true,
        textStyle = MaterialTheme.typography.titleLarge.copy(color = ArcColors.Foreground),
        cursorBrush = SolidColor(ArcColors.Accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            keyboard?.hide()
            focusManager.moveFocus(FocusDirection.Down)
        }),
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Row(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(ArcColors.Surface2)
                    .border(BorderStroke(2.dp, if (focused) ArcColors.Foreground else ArcColors.Surface3), RoundedCornerShape(12.dp))
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(ArcIcons.Search, contentDescription = null, tint = ArcColors.Muted, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(14.dp))
                Box(Modifier.weight(1f)) {
                    if (state.text.isEmpty()) Text(stringResource(R.string.search_placeholder), style = MaterialTheme.typography.titleLarge, color = ArcColors.Subtle)
                    inner()
                }
                if (state.searching) Spinner(size = 22.dp)
            }
        },
    )
}

@Composable
private fun Message(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = ArcColors.Muted, modifier = Modifier.padding(start = 48.dp, top = 24.dp))
}

@Composable
private fun SectionRow(section: SearchSection, onOpen: (Item) -> Unit, onPerson: (Person) -> Unit) {
    org.arcmediaserver.android.ui.components.Shelf(groupTitle(section.group)) {
        if (section.group == SearchGroup.People) {
            items(section.people, key = { it.id }) { PersonCard(it) { onPerson(it) } }
        } else {
            items(section.cards, key = { it.key }) { card ->
                MediaCardView(card, landscape = section.group == SearchGroup.Episodes, onClick = { onOpen(card.item) })
            }
        }
    }
}

@Composable
private fun groupTitle(group: SearchGroup): String = stringResource(
    when (group) {
        SearchGroup.Movies -> R.string.search_movies
        SearchGroup.Shows -> R.string.search_shows
        SearchGroup.People -> R.string.search_people
        SearchGroup.Episodes -> R.string.search_episodes
    },
)

/** Someone in the libraries: a round photo, their name and how many titles they're in. */
@Composable
fun PersonCard(person: Person, onClick: () -> Unit) = org.arcmediaserver.android.ui.components.PersonCard(
    person.name,
    person.image,
    if (person.count > 0) pluralStringResource(R.plurals.person_titles, person.count, person.count) else null,
    onClick,
)
