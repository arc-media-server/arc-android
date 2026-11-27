package org.arcmediaserver.android.data

import coil3.intercept.Interceptor
import coil3.request.ImageResult
import coil3.size.Dimension
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Asks Arc for each image at the width it's drawn at (`?w=`), so a 1080p TV gets a 1920-wide hero
 * rather than a 4K original, and cards get card-sized pictures. Arc keeps one copy per width and
 * never enlarges; a server without resizing ignores the parameter and sends the original.
 */
class ArtworkWidth : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val width = (chain.size.width as? Dimension.Pixels)?.px ?: return chain.proceed()
        val url = chain.request.data as? String ?: return chain.proceed()
        val sized = withWidth(url, width) ?: return chain.proceed()
        return chain.withRequest(chain.request.newBuilder().data(sized).build()).proceed()
    }

    companion object {
        private val ARTWORK = Regex("^/api/(v1/)?(items/[^/]+/images/[a-z_]+|collections/[^/]+/images/[a-z_]+|people/[^/]+/image|series/[^/]+/seasons/[0-9]+/image)$")

        /** [url] with `w=[width]` when it's Arc artwork without one; null otherwise. */
        fun withWidth(url: String, width: Int): String? {
            if (width <= 0) return null
            val parsed = url.toHttpUrlOrNull() ?: return null
            if (!ARTWORK.matches(parsed.encodedPath) || parsed.queryParameter("w") != null) return null
            return parsed.newBuilder().addQueryParameter("w", width.toString()).build().toString()
        }
    }
}
