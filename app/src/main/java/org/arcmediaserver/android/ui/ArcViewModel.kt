package org.arcmediaserver.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.arcmediaserver.android.AppContainer
import org.arcmediaserver.android.ArcApplication

/** A ViewModel made with the app's container, scoped to the screen's back stack entry. */
@Composable
inline fun <reified VM : ViewModel> arcViewModel(crossinline create: (AppContainer) -> VM): VM {
    val container = (LocalContext.current.applicationContext as ArcApplication).container
    return viewModel(factory = viewModelFactory { initializer { create(container) } })
}
