package org.arcmediaserver.android.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
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
import org.arcmediaserver.android.data.CollectionPage
import org.arcmediaserver.android.data.Item
import org.arcmediaserver.android.ui.arcViewModel
import org.arcmediaserver.android.ui.components.LoadState
import org.arcmediaserver.android.ui.components.Loaded
import org.arcmediaserver.android.ui.components.requestWhenReady
import org.arcmediaserver.android.ui.components.toUiError
import org.arcmediaserver.android.ui.theme.ArcColors

class CollectionViewModel(private val container: AppContainer, private val id: String) : ViewModel() {
    private val _state = MutableStateFlow<LoadState<CollectionPage>>(LoadState.Loading)
    val state: StateFlow<LoadState<CollectionPage>> = _state

    init {
        load()
    }

    fun load() {
        val api = container.api() ?: return
        _state.value = LoadState.Loading
        viewModelScope.launch {
            _state.value = try {
                LoadState.Ready(api.collection(id))
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                ArcLog.Android.warn("collection failed to load: ${e.javaClass.simpleName}")
                LoadState.Failed(e.toUiError())
            }
        }
    }
}

/** A collection's page: its name, how many titles, and the titles in the library's grid. */
@Composable
fun CollectionRoute(id: String, onOpen: (Item) -> Unit) {
    val vm = arcViewModel { CollectionViewModel(it, id) }
    val state by vm.state.collectAsStateWithLifecycle()
    CollectionScreen(state, onOpen, onRetry = vm::load)
}

@Composable
fun CollectionScreen(state: LoadState<CollectionPage>, onOpen: (Item) -> Unit, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().background(ArcColors.Background)) {
        Loaded(state, onRetry) { page ->
            val first = remember { FocusRequester() }
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.padding(start = 48.dp, top = 28.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
                    Text(page.collection.title, style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.width(14.dp))
                    Text(pluralStringResource(R.plurals.library_count, page.items.size, page.items.size), style = MaterialTheme.typography.titleMedium, color = ArcColors.Muted, modifier = Modifier.padding(bottom = 6.dp))
                }
                TitleGrid(page.items, landscape = page.collection.kind == "folder", first, onOpen)
            }
            LaunchedEffect(page.collection.id) { first.requestWhenReady() }
        }
    }
}
