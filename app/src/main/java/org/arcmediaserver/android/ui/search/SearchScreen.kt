package org.arcmediaserver.android.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.Color
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
            state.text.isBlank() -> Column(Modifier.fillMaxWidth().padding(top = 90.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(ArcIcons.Search, contentDescription = null, tint = ArcColors.Muted.copy(alpha = 0.6f), modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.search_hint), style = MaterialTheme.typography.bodyLarge, color = ArcColors.Muted)
            }
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
        textStyle = MaterialTheme.typography.titleLarge.copy(color = if (focused) Color.Black else ArcColors.Foreground),
        cursorBrush = SolidColor(ArcColors.AccentStrong),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            keyboard?.hide()
            focusManager.moveFocus(FocusDirection.Down)
        }),
        modifier = modifier.widthIn(max = 600.dp).fillMaxWidth().onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Row(
                // The Apple TV app's glass pill, white when focused (owner, 1 November).
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (focused) Color.White else Color.White.copy(alpha = 0.1f))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(ArcIcons.Search, contentDescription = null, tint = if (focused) Color(0xFF444444) else ArcColors.Muted, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(14.dp))
                Box(Modifier.weight(1f)) {
                    if (state.text.isEmpty()) Text(stringResource(R.string.search_placeholder), style = MaterialTheme.typography.titleLarge, color = if (focused) Color(0xFF777777) else ArcColors.Subtle)
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
                // Top Results: wide artwork cards with the title's logo, as the Apple TV app's.
                val top = section.group == SearchGroup.Top
                MediaCardView(card, landscape = top || section.group == SearchGroup.Episodes, backdrop = top, width = if (top) 200.dp else null, onClick = { onOpen(card.item) })
            }
        }
    }
}

@Composable
private fun groupTitle(group: SearchGroup): String = stringResource(
    when (group) {
        SearchGroup.Top -> R.string.search_top
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
