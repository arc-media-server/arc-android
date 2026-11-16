# Add to Playlist from the TV

Playlists already showed and played on the TV, but titles could only be added to them on the web.
The web's card and title menus have **Add to Playlist…**; the TV now has it too.

## What changed

- **Where:** in every card's menu (hold OK) and a title's ⋯ menu, after Play Next and Add to
  Queue. It's offered for films, episodes and shows, which is what a playlist can hold.
- **The list:**
  - a centred list titled Add to Playlist, saying what's being added ("Add “Heat” to:", or "Add
    every episode of “Hollow Creek” to:" for a show);
  - then the profile's playlists, each with how many titles it holds;
  - smart playlists aren't listed, since they fill themselves (as the web's picker).
- **Choosing one:**
  - adds the title with `POST /api/v1/playlists/{id}/items` (in the v1 contract);
  - the list closes and the focus goes back to the card or ⋯;
  - a note at the bottom says "Added “Heat” to Movie Night", or that it couldn't, the same note
    Play Next uses.
- **No playlists to add to:** the list says to make one on the web first, with Close. If the
  playlists don't load, it offers Try Again.
- **Code:**
  - the app's card menu host (`ui/CardMenuHost.kt`) owns the list, so both menus share it;
  - `CardMenu.addToPlaylist` opens it, and `PlaylistPicker` (`ui/details/TitleMenus.kt`) draws
    it;
  - `PlaylistInfo` reads `smart_filter`.

## Decisions

- **No new playlists from the TV:** the web's picker can also make one by name. Typing a name with
  a remote is slow, and playlists have been made on the web so far (1 November), so the TV adds
  to existing ones only. To log on arc-media-server#80.

## Checks

- Merged main and claude/star-rating (#34) in (the ⋯ menu holds both: queue items, Add to
  Playlist…, …, Play Version…, Rate…, Media Info). `./gradlew assembleDebug testDebugUnitTest
  recordRoborazziDebug`: passed (185 tests, 1 skipped (the live-server test), 0 failed).
- `tv-add-to-playlist` lists Movie Night and Short List, not the smart Unwatched 4K;
  `tv-add-to-playlist-none` shows the note pointing to the web.
- New tests:
  - `ArcApiTest`: smart playlists aren't editable, and the POST and its answer.
- New screenshots: `tv-add-to-playlist` and `tv-add-to-playlist-none`.
- Not tried on a TV or emulator.

## Logging

Warnings, with the exception type only (no ids or names):
- when the playlists don't load for the list ("playlists failed to load for Add to Playlist");
- when adding fails ("adding to a playlist failed").
