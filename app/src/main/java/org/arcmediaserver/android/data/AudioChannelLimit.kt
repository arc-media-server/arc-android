package org.arcmediaserver.android.data

/**
 * Channel counts from the anticipated media output routes, rather than decoder/codec names.
 * Empty counts mean arbitrary/unknown support, not stereo. Duplicated output must fit every route.
 * Media3's limit may narrow an established route limit but its defaults never establish one.
 */
internal fun outputChannelLimit(routes: List<List<Int>>, playerLimit: Int?): Int? {
    if (routes.isEmpty()) return null
    val limits = routes.map { counts -> counts.filter { it > 0 }.maxOrNull() ?: return null }
    val routeLimit = minOf(limits.minOrNull() ?: return null, 32)
    return playerLimit?.takeIf { it in 1..32 }?.let { minOf(routeLimit, it) } ?: routeLimit
}
