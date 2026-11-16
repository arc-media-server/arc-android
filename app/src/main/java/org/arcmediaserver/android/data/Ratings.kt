package org.arcmediaserver.android.data

/**
 * The profile's own rating, as the web's StarRating: five stars in halves, stored by Arc as
 * 1–10 (PUT /items/{id}/rating; 0 clears it).
 */
object Ratings {
    /** The choices a viewer picks from, best first: 5 stars (10) down to half a star (1). */
    val choices: List<Int> = (10 downTo 1).toList()

    /** "★★★★½" for 9: whole stars, then a half; empty for 0. */
    fun stars(rating: Int): String {
        val r = rating.coerceIn(0, 10)
        return "★".repeat(r / 2) + if (r % 2 == 1) "½" else ""
    }

    /** "4.5" or "4" for 9 or 8: the rating out of five, as the web says it. */
    fun outOfFive(rating: Int): String {
        val r = rating.coerceIn(0, 10)
        return if (r % 2 == 0) (r / 2).toString() else "${r / 2}.5"
    }
}
