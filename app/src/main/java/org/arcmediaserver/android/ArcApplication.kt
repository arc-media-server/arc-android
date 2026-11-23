package org.arcmediaserver.android

import android.app.Application
import org.arcmediaserver.android.data.DeviceLogs
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade

open class ArcApplication : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = createContainer()
        val logs = container.deviceLogs
        logs.record(DeviceLogs.Level.info, DeviceLogs.Event.app_started)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous != null) Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { logs.setCrash(true) }
            previous.uncaughtException(thread, error)
        }
    }

    /** The app's container; the live screenshot test gives it a session kept without the Keystore. */
    protected open fun createContainer(): AppContainer = AppContainer(this)

    /** Artwork comes from Arc with the device token (and may be redirected to the provider). */
    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { container.mediaClient })) }
        .crossfade(true)
        .build()
}
