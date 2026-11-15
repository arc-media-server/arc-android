# Mark a season watched or unwatched

A show's page can now mark a whole season watched or unwatched, as the web's show page and
Plex's season menu do. It was one of the gaps in the feature-parity review.

- **How:** OK on a season's tab opens a small menu, titled with the season, with Mark Season as
  Watched (or Unwatched when every episode is already watched). A menu on the tab rather than a
  button after the tabs, because moving along the tabs switches season: a trailing button could
  only be reached by passing the last season.
- **What it sends:** `PUT /api/v1/items/{show}/played` with `season_number`, already part of
  `/api/v1`. The season is the show's display order, as the web.
- **Feedback:** the ticks change at once and resume points clear; the page then refreshes so
  Play and the show's unwatched count follow. If Arc can't save it, the ticks go back and a
  short note shows beside the tabs for five seconds.

Logging: one info line when a season is marked (episode count only), and a warning with the
exception type when it fails. No ids, titles or URLs.

Validation: `./gradlew assembleDebug testDebugUnitTest recordRoborazziDebug` passed (165 tests,
1 skipped, 0 failed), including three new tests: the request body (show id and season number),
when a season counts as watched, and that marking changes only that season and clears its resume
points. The new `tv-show-season-menu` screenshot was checked; no existing screenshot changed.
`LiveServerTest` wasn't run: it doesn't exercise this call, whose body is covered by the unit
test and whose server side is covered by the server's own tests. The build fetched Maven Central
through Google's copy of it (maven-central.storage-download.googleapis.com) because Maven
Central was rate-limiting this environment. Not tried on a real TV or emulator.
