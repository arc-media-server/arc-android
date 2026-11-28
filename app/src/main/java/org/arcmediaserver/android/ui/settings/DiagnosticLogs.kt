package org.arcmediaserver.android.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.arcmediaserver.android.AppContainer
import org.arcmediaserver.android.R
import org.arcmediaserver.android.data.ApiException
import org.arcmediaserver.android.ui.components.Popover
import org.arcmediaserver.android.ui.components.PopoverItem
import org.arcmediaserver.android.ui.components.PopoverList
import org.arcmediaserver.android.ui.components.requestWhenReady

internal suspend fun sendDiagnosticLogs(container: AppContainer): Int {
    val api = container.api() ?: return R.string.diagnostics_failed
    val payload = container.deviceLogs.upload() ?: return R.string.diagnostics_empty
    return try {
        api.sendDeviceLogs(payload)
        R.string.diagnostics_sent
    } catch (e: CancellationException) {
        throw e
    } catch (e: ApiException) {
        if (e.status == 404) R.string.diagnostics_unsupported else R.string.diagnostics_failed
    } catch (_: Exception) {
        R.string.diagnostics_failed
    }
}

@Composable
internal fun CrashLogPrompt(container: AppContainer) {
    var show by remember { mutableStateOf(container.deviceLogs.pendingCrash()) }
    if (!show) return
    var sending by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val close = {
        container.deviceLogs.setCrash(false)
        show = false
    }
    Popover(anchor = null, onClose = close, title = stringResource(result ?: R.string.diagnostics_crash), width = 480.dp) {
        PopoverList {
            if (result != R.string.diagnostics_sent) {
                PopoverItem(stringResource(R.string.diagnostics_send), null, focus = focus) {
                    if (!sending) scope.launch {
                        sending = true
                        result = sendDiagnosticLogs(container)
                        sending = false
                        if (result == R.string.diagnostics_sent) container.deviceLogs.setCrash(false)
                    }
                }
            }
            PopoverItem(stringResource(android.R.string.cancel), null, onClick = close)
        }
    }
    LaunchedEffect(Unit) { focus.requestWhenReady() }
}
