package org.arcmediaserver.android.ui.person

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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

/** Someone and the titles they're in here, as a poster grid (web/src/pages/Person.tsx, simplified). */
@Composable
fun PersonScreen(page: PersonPage, onOpen: (Item) -> Unit) {
    val first = remember { FocusRequester() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 56.dp, top = 36.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(page.person.image, label = page.person.name.take(1), modifier = Modifier.size(84.dp).clip(CircleShape))
            Spacer(Modifier.width(20.dp))
            Column {
                Text(page.person.name, style = MaterialTheme.typography.displaySmall)
                Text(pluralStringResource(R.plurals.person_titles, page.credits.size, page.credits.size), style = MaterialTheme.typography.titleMedium, color = ArcColors.Muted)
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(112.dp),
            contentPadding = PaddingValues(start = 56.dp, end = 48.dp, top = 16.dp, bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            itemsIndexed(page.credits, key = { _, c -> c.item.id }) { i, c ->
                val mod = Modifier.fillMaxWidth()
                MediaCardView(cardFor(c.item), landscape = false, onClick = { onOpen(c.item) }, modifier = if (i == 0) mod.focusRequester(first) else mod)
            }
        }
    }
    LaunchedEffect(page.person.id) { first.requestWhenReady() }
}
