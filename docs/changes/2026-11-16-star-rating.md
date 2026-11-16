# Your own star rating

The web lets each profile rate films, shows and episodes with five stars in halves (the web's
StarRating). Arc keeps it per profile (`user_data.rating`, 1–10). The TV couldn't see or set it.

## What changed

- **Where:** a title's ⋯ menu gains **Rate…**, after Play Version…, as Plex's TV app has Rate in
  its menu. It shows the stars given so far (★★★½). This is on film, show and episode pages.
- **The rating list:**
  - a centred list titled Your Rating, from ★★★★★ (5 of 5) down to ½ (0.5 of 5);
  - the current rating is ticked and has the focus;
  - **Clear Rating** shows when there's one to clear.
- **Saving:** choosing saves it with `PUT /api/v1/items/{id}/rating` (in the v1 contract). It shows
  at once and goes back if Arc can't save it, with "Couldn't save your rating. Try again." under
  the title's buttons, as Watched and Watchlist do.
- **Code:** `data/Ratings.kt` has the stars and wording; `UserData.rating` reads Arc's value.
- **Icon:** a star joins `ArcIcons` (Lucide's).

## Decisions

- **Title pages only:** not in the cards' hold-OK menu. Ratings belong with the title's details,
  and the card menu stays short.
- **No stars on the page:** the web shows stars beside the crew. The TV keeps them in the menu so
  the page's buttons stay as they are.

## Checks

- Merged main in (the title-page header conflict kept main's layout with the rating failure
  message added). `./gradlew assembleDebug testDebugUnitTest recordRoborazziDebug`: passed (182
  tests, 1 skipped (the live-server test), 0 failed). `tv-movie-more-menu-rate` shows Rate… with
  ★★★½; `tv-movie-rating` shows the ★ and ½ glyphs and 3½ ticked and focused.
- New tests:
  - `RatingsTest`: stars, wording, choices, and a rating kept beside watched/watchlist;
  - `ArcApiTest`: the PUT's body, and `rating` read back.
- New screenshots: `tv-movie-more-menu-rate` and `tv-movie-rating`.
- Not tried on a TV or emulator.

## Logging

A failed save or clear is logged as a warning ("saving a rating failed"), with no ids. ArcApi
logs the route on errors as before.
