package org.arcmediaserver.android.data

import coil3.intercept.Interceptor
import coil3.request.ImageResult
import coil3.size.Dimension
import coil3.size.Scale
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Asks Arc for each image at the size it's drawn at (`?w=` and `?h=`, at least that tall), so a 1080p TV gets a 1920-wide hero
 * rather than a 4K original, and cards get card-sized pictures. Arc keeps one copy per width and
 * never enlarges; the height matters when a picture is cropped into a box taller in shape than
 * it. A server without resizing ignores the parameters and sends the original.
 */
class ArtworkWidth : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val width = (chain.size.width as? Dimension.Pixels)?.px ?: return chain.proceed()
        // The height only matters when the picture fills (crops to) its box; fitted inside it
        // (logos), the width is already enough.
        val height = (chain.size.height as? Dimension.Pixels)?.px?.takeIf { chain.request.scale == Scale.FILL } ?: 0
        val url = chain.request.data as? String ?: return chain.proceed()
        val sized = withWidth(url, width, height) ?: return chain.proceed()
        return chain.withRequest(chain.request.newBuilder().data(sized).build()).proceed()
    }

    companion object {
        private val ARTWORK = Regex("^/api/(v1/)?(items/[^/]+/images/[a-z_]+|collections/[^/]+/images/[a-z_]+|people/[^/]+/image|series/[^/]+/seasons/[0-9]+/image)$")

        /** [url] with `w=[width]` (and `h=[height]` when known) when it's Arc artwork without one; null otherwise. */
        fun withWidth(url: String, width: Int, height: Int = 0): String? {
            if (width <= 0) return null
            val parsed = url.toHttpUrlOrNull() ?: return null
            if (!ARTWORK.matches(parsed.encodedPath) || parsed.queryParameter("w") != null) return null
            val sized = parsed.newBuilder().addQueryParameter("w", width.toString())
            if (height > 0) sized.addQueryParameter("h", height.toString())
            return sized.build().toString()
        }
    }
}
