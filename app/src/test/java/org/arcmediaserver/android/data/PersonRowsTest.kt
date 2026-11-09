package org.arcmediaserver.android.data

import org.arcmediaserver.android.data.PersonRows.Kind
import org.junit.Assert.assertEquals
import org.junit.Test

class PersonRowsTest {
    private fun item(id: String, kind: String, year: Int, title: String = id) = Item(id = id, kind = kind, title = title, year = year)

    private val page = PersonPage(
        person = Person(1, "Mara Ellison", knownFor = "actor"),
        credits = listOf(
            PersonCredit(item("a", "movie", 2001), listOf("actor"), "Nell"),
            PersonCredit(item("b", "movie", 2019), listOf("actor", "director"), "Ada"),
            PersonCredit(item("b", "movie", 2019), listOf("actor"), "Ada"),
            PersonCredit(item("s", "series", 2015), listOf("actor"), "Sam"),
            PersonCredit(item("c", "movie", 2010), listOf("writer")),
        ),
    )

    @Test
    fun `films, then shows, then crew, newest first and once each`() {
        val rows = PersonRows.rows(page)
        assertEquals(listOf(Kind.Movies, Kind.Shows, Kind.Crew), rows.map { it.kind })
        assertEquals(listOf("b", "a"), rows[0].credits.map { it.item.id })
        assertEquals(listOf("s"), rows[1].credits.map { it.item.id })
        assertEquals(listOf("b", "c"), rows[2].credits.map { it.item.id })
    }

    @Test
    fun `empty rows are left out`() {
        val onlyCrew = PersonPage(Person(2, "Theo Lund"), listOf(PersonCredit(item("c", "movie", 2010), listOf("director"))))
        assertEquals(listOf(Kind.Crew), PersonRows.rows(onlyCrew).map { it.kind })
        assertEquals(emptyList<PersonRows.Row>(), PersonRows.rows(PersonPage(Person(3, "Nobody"))))
    }

    @Test
    fun `count and main role`() {
        assertEquals(4, PersonRows.titleCount(page))
        assertEquals("actor", PersonRows.mainRole(page))
        assertEquals("director", PersonRows.mainRole(PersonPage(Person(2, "Theo"), listOf(PersonCredit(item("c", "movie", 1), listOf("director"))))))
    }

    @Test
    fun `their part is the character in acting rows and the crew roles in Crew`() {
        val both = page.credits[1]
        assertEquals(PersonRows.Part.Character("Ada"), PersonRows.part(both, Kind.Movies))
        assertEquals(PersonRows.Part.Roles(listOf("director")), PersonRows.part(both, Kind.Crew))
    }
}
