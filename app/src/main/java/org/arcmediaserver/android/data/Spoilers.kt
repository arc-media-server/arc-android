package org.arcmediaserver.android.data

/**
 * Hide Spoilers, as the web's `useSpoilerGuard`: while the profile's `hide_spoilers` is on, an
 * episode not yet started (not watched, no resume point) keeps its picture and description back.
 */
fun Item.spoilerHidden(hideSpoilers: Boolean): Boolean =
    hideSpoilers && isEpisode && userData?.played != true && resumeMs <= 0

/** An episode picture to show, and whether to blur it (its own still, when the show has no background). */
data class EpisodeArt(val url: String?, val blur: Boolean)

/**
 * The picture for an episode's card ([thumb]: its still, or what stands in for it). While its
 * spoilers are hidden: the show's background instead, else the still blurred, as the web's cards.
 */
fun episodeArt(item: Item, thumb: String?, hideSpoilers: Boolean): EpisodeArt {
    if (!item.spoilerHidden(hideSpoilers)) return EpisodeArt(thumb, blur = false)
    val show = item.images?.seriesBackdrop
    if (show != null) return EpisodeArt(show, blur = false)
    return EpisodeArt(thumb, blur = thumb != null && thumb == item.images?.primary)
}
