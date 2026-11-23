package org.arcmediaserver.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.key
import org.arcmediaserver.android.data.ArcLog
import org.arcmediaserver.android.ui.ArcNavHost
import org.arcmediaserver.android.watchnext.DeepLink
import org.arcmediaserver.android.ui.theme.ArcTheme

class MainActivity : ComponentActivity() {
    private companion object {
        val processNavigationKey = java.util.UUID.randomUUID().toString()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) resumeFrom(intent)
        setContent {
            // Android can restore a task's navigation after process death. That is a fresh
            // launch, while recreation in this process and returning from Home keep their state.
            key(processNavigationKey) {
                ArcTheme { ArcNavHost() }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        resumeFrom(intent)
    }

    /** On screen: Arc's live updates keep what's shown current. */
    override fun onStart() {
        super.onStart()
        (application as ArcApplication).container.live.start()
    }

    /** Leaving the app: Google TV's Continue watching row shows where things were left; no live updates. */
    override fun onStop() {
        super.onStop()
        val container = (application as ArcApplication).container
        container.live.stop()
        container.syncWatchNext()
        container.deviceLogs.flush()
    }

    /** A title chosen in Google TV's Continue watching row: it plays once the app is ready. */
    private fun resumeFrom(intent: Intent?) {
        val id = DeepLink.itemToPlay(intent?.data) ?: return
        ArcLog.Android.info("resuming a title from the TV's home screen")
        (application as ArcApplication).container.pendingPlay.value = id
    }
}
