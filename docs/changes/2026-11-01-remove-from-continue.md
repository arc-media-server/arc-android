# Remove from Continue Watching (1 November)

Holding OK on a card in Home's Continue Watching row now offers **Remove from Continue
Watching** in its menu, as the web's card ⋯ menu does (arc-android#2; the TV web app has the same
in arc-tv#3). It calls `DELETE /api/v1/home/continue/{id}` (already in the v1 contract), which
takes the title out of Continue Watching and Up Next without marking it watched.

- The card leaves the row (and the hero) at once; if Arc refuses, it comes back and a note says so.
- Only that row's cards offer it (`LocalInContinueRow`), as on the web.
- What was removed is kept per session in `AppContainer.offContinue` and cleared with the profile.

Tests: screenshots `tv-card-menu-continue` and `tv-home-continue-removed`.

Logging: a failed removal logs one warning (`removing from Continue Watching failed: <exception type>`),
no ids or URLs; ArcApi's request logging covers the HTTP side.
