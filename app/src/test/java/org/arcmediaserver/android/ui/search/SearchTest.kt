package org.arcmediaserver.android.ui.search

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.arcmediaserver.android.data.ApiException
import org.arcmediaserver.android.data.ArcLog
import org.arcmediaserver.android.data.Item
import org.arcmediaserver.android.data.Person
import org.arcmediaserver.android.data.SearchResults
import org.arcmediaserver.android.ui.components.UiError
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchTest {
    private fun item(id: String, kind: String) = Item(id = id, kind = kind, title = id)
    private val results = SearchResults(
        movies = listOf(item("m", "movie")),
        shows = emptyList(),
        episodes = listOf(item("e", "episode")),
        people = listOf(Person(7, "Ada")),
    )

    @Test
    fun `waits for typing to pause and searches only the latest words`() = runTest {
        val calls = mutableListOf<String>()
        val typed = flow {
            emit("f"); delay(100); emit("fi"); delay(100); emit("fir ") // a pause after "fir"
            delay(400); emit("")
        }
        val out = searchOutcomes(typed, 300, ArcLog.Print) { q -> calls += q; results }.toList()
        assertEquals(listOf("fir"), calls)
        assertEquals(SearchOutcome.Started("fir"), out[0])
        assertEquals(listOf(SearchGroup.Movies, SearchGroup.People, SearchGroup.Episodes), (out[1] as SearchOutcome.Found).sections.map { it.group })
        assertEquals(SearchOutcome.Cleared, out[2])
    }

    @Test
    fun `a newer query cancels a search still on its way`() = runTest {
        val finished = mutableListOf<String>()
        val typed = flow { emit("alien"); delay(500); emit("aliens") }
        val out = searchOutcomes(typed, 300, ArcLog.Print) { q -> delay(1_000); finished += q; results }.toList()
        assertEquals(listOf("aliens"), finished)
        assertEquals(listOf("alien", "aliens"), out.filterIsInstance<SearchOutcome.Started>().map { it.query })
        assertEquals(1, out.count { it is SearchOutcome.Found })
    }

    @Test
    fun `a failed search says why`() = runTest {
        val out = searchOutcomes(flow { emit("x") }, 300, ArcLog.Print) { throw ApiException(500, "database is busy") }.toList()
        assertEquals(SearchOutcome.Failed("x", UiError.Server("database is busy", 500)), out.last())
    }

    @Test
    fun `groups follow the web's order and empty groups are left out`() {
        val sections = searchSections(results.copy(shows = listOf(item("s", "series"))))
        assertEquals(listOf(SearchGroup.Movies, SearchGroup.Shows, SearchGroup.People, SearchGroup.Episodes), sections.map { it.group })
        assertEquals("s", sections[1].cards.single().item.id)
        assertEquals(emptyList<SearchSection>(), searchSections(SearchResults()))
    }
}
