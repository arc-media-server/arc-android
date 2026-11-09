package org.arcmediaserver.android.data

/** The Watchlist's pills, as the Apple TV app's: what's shown and in which order. */
object WatchlistView {
    enum class Show { All, Movies, Shows }

    /** Date Added is Arc's own order (newest first); the others sort here. */
    enum class Sort { DateAdded, Title, ReleaseDate }

    fun shown(items: List<Item>, show: Show, sort: Sort): List<Item> {
        val kept = when (show) {
            Show.All -> items
            Show.Movies -> items.filter { it.kind == "movie" }
            Show.Shows -> items.filter { it.isSeries }
        }
        return when (sort) {
            Sort.DateAdded -> kept
            Sort.Title -> kept.sortedBy { sortTitle(it.title) }
            Sort.ReleaseDate -> kept.sortedWith(compareByDescending<Item> { it.releaseDate ?: it.year?.toString().orEmpty() }.thenBy { sortTitle(it.title) })
        }
    }

    /** A title without a leading "The", "A" or "An", as libraries sort them. */
    fun sortTitle(title: String): String = title.lowercase().removePrefix("the ").removePrefix("a ").removePrefix("an ")
}
