# Moving down to the season tabs shows the episodes

**What changed:** on a show's page, moving down from Play to the season tabs (or up from the
episodes) now scrolls the page far enough to show the season's episodes and their descriptions,
not just the tabs. Before, the page only scrolled the tabs themselves into view, so the episodes
sat below the bottom of the screen until you moved down again.

**How:** the seasons block (tabs and episodes) asks to be brought into view when the tabs get
focus, a frame after the tab's own request so it wins (a newer scroll cancels an older one, as on
Home). The page's scrolling is still the minimal kind: it moves only as far as needed, so as much
of the header stays as fits.

**Decisions:** as Plex for Android TV, where reaching the seasons shows their episodes.

**Checks:** `./gradlew assembleDebug testDebugUnitTest recordRoborazziDebug`: 286 tests, 0
failures. New screenshot test `showSeasonTabs` (Down from Play), in
[2026-11-22-season-tabs-scroll/](2026-11-22-season-tabs-scroll/). Not tried on a real TV.

**Logging:** none needed (a scroll).
