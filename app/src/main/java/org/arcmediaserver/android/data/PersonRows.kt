package org.arcmediaserver.android.data

/**
 * Someone's page in rows, as the Apple TV app's PersonView: the films and the shows they act in,
 * then what they made behind the camera (directing, writing, creating). Each row is newest first,
 * and a title shows once per row.
 */
object PersonRows {
    enum class Kind { Movies, Shows, Crew }

    data class Row(val kind: Kind, val credits: List<PersonCredit>)

    private fun acts(c: PersonCredit) = c.roles.isEmpty() || "actor" in c.roles

    private fun newestFirst(list: List<PersonCredit>) =
        list.distinctBy { it.item.id }.sortedWith(compareByDescending<PersonCredit> { it.item.year ?: 0 }.thenBy { it.item.title.lowercase() })

    fun rows(page: PersonPage): List<Row> = buildList {
        val movies = newestFirst(page.credits.filter { acts(it) && !it.item.isSeries })
        val shows = newestFirst(page.credits.filter { acts(it) && it.item.isSeries })
        val crew = newestFirst(page.credits.filter { c -> c.roles.any { it != "actor" } })
        if (movies.isNotEmpty()) add(Row(Kind.Movies, movies))
        if (shows.isNotEmpty()) add(Row(Kind.Shows, shows))
        if (crew.isNotEmpty()) add(Row(Kind.Crew, crew))
    }

    /** How many different titles they're in here. */
    fun titleCount(page: PersonPage): Int = page.credits.distinctBy { it.item.id }.size

    /** The role their summary leads with: what they're known for, else what most of their credits are. */
    fun mainRole(page: PersonPage): String? =
        page.person.knownFor?.takeIf { it.isNotBlank() }
            ?: page.credits.flatMap { it.roles }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

    /** Their part in a title for the focused line: the character they play, else their crew roles. */
    fun part(credit: PersonCredit, row: Kind): Part =
        if (row != Kind.Crew && !credit.character.isNullOrBlank()) Part.Character(credit.character)
        else Part.Roles(credit.roles.filter { if (row == Kind.Crew) it != "actor" else true })

    sealed interface Part {
        data class Character(val name: String) : Part
        data class Roles(val roles: List<String>) : Part
    }
}
