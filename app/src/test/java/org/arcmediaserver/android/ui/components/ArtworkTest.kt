package org.arcmediaserver.android.ui.components

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.annotation.ExperimentalCoilApi
import coil3.asImage
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoilApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class ArtworkTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `opaque artwork replaces its placeholder while transparent and missing art keep theirs`() {
        val handler = AsyncImagePreviewHandler { request ->
            Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply {
                if (request.data == "opaque") setHasAlpha(false)
            }.asImage()
        }
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true, LocalAsyncImagePreviewHandler provides handler) {
                androidx.compose.foundation.layout.Column {
                    Artwork("opaque", "Opaque art", Modifier.size(80.dp))
                    Artwork("transparent", "Transparent art", Modifier.size(80.dp))
                    Artwork(null, "Missing art", Modifier.size(80.dp))
                }
            }
        }
        compose.onNodeWithContentDescription("Opaque art").assertExists()
        compose.onNodeWithText("Opaque art").assertDoesNotExist()
        compose.onNodeWithText("Transparent art").assertExists()
        compose.onNodeWithText("Missing art").assertExists()
    }
}
