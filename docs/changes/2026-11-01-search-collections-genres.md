# Search finds collections and genres (1 November)

From the to-do list (arc-android#2, "Matching the web"); the TV web app has the same in arc-tv#5.
Search now shows the two kinds of result the web's Search already does, from the same
`GET /api/v1/search` answer (`collections`, `genres`; both in the v1 contract):

- **Collections:** a row of collection posters after People; OK opens the collection.
- **Genres:** glass pills last ("Drama  Movies · 12"); OK opens that library on its titles,
  filtered to the genre, as the web's genre links. Back from there returns to Search's results.

Details: `Section.Library` carries the genre; a library opened on a genre doesn't change the sort
and filters remembered for it. The sidebar still marks the library as selected.

Tests: `SearchTest` checks the order (Top, People, Collections, Episodes, Genres); screenshot
`tv-search-collections-genres`.

Logging: none added; Search's existing failure log covers the request.
