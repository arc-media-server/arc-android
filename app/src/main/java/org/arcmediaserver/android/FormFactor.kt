package org.arcmediaserver.android

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

/**
 * Which interface Arc shows: the TV one on Android TV and Google TV (a TV's UI mode, as the
 * system or the configuration reports it, or the leanback feature every Android TV device
 * declares), the phone and tablet one everywhere else. One app serves both, as Plex's does,
 * sharing sign-in, the API and playback.
 */
object FormFactor {
    fun isTv(context: Context): Boolean {
        val mode = (context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)?.currentModeType
        val configured = context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        return mode == Configuration.UI_MODE_TYPE_TELEVISION || configured == Configuration.UI_MODE_TYPE_TELEVISION ||
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }
}
