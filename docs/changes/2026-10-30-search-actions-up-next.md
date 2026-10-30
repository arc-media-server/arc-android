# Search, title actions, Up Next and the player's remembered choices

The TV app's second slice: things the web app had that the TV lacked (listed under "Limits" in
`2026-10-29-android-tv-app.md`). The web app is the reference for behaviour, not code.

## What changed

- **Search.** A Search item at the top of the sidebar, as on Google TV and in Plex. The screen is a
  text field (the TV's own keyboard and its voice input come with a standard field) with results
  that follow the typing: `GET /search` once typing pauses for 300 ms (clearing the field is
  immediate), and a newer query cancels one still on its way, so only the latest results show.
  Results are grouped in the web's order (`web/src/pages/Search.tsx`): Movies, Shows, People,
  Episodes; empty groups are left out. Movies and shows use Home's poster cards, episodes its
  landscape cards; a result opens the existing details page. A person opens a simple person page
  (`GET /people/{id}`): photo, name and a poster grid of their titles here.
- **Title actions.** Movie, show and episode pages get Plex's round buttons after Play: Watched
  (`PUT /items/{id}/played`; a show marks all its episodes), Watchlist (`PUT /items/{id}/watchlist`,
  movies and shows only: the API doesn't take episodes) and ⋯ (More). The focused button's name
  shows below the row. The ⋯ menu is a TV dialog (dimmed page, focus kept inside, Back closes it)
  with Mark as Watched/Unwatched, Add to/Remove from Watchlist and Media Info. Changes show at once
  and are put back if Arc refuses or can't be reached, with "Couldn't change …" under the buttons for
  a few seconds; a show refreshes afterwards so its episodes' ticks and Play target follow.
- **Media Info** is laid out as the web's "About This File" (`SourceFormats.tsx`): per file, labelled
  rows for Video (resolution, codec, HDR10/HDR10+/Dolby Vision/HLG; size and aspect; frame rate,
  bitrate, bit depth, DV profile; colour details), Audio (language, codec with Atmos/DTS-HD from
  the profile, channels, bitrate, title), Subtitles (language, format, forced) and File (container,
  size, bitrate), then the path. Values Arc doesn't know, and rows with nothing in them, are left
  out. Several versions get an "About This Version" heading each.
- **Up Next countdown**, as the web player's: when the credits start (from the skip markers) and
  again when the episode ends, a card at the bottom right reads "Next episode in 10s" with the
  episode's still, title and number, Play Now (focused) and Cancel. Play Now (or reaching 0) plays
  it; Cancel lets the credits play. The credits' Play Next button steps aside while the card shows.
  The profile's **Auto-Play** (`auto_play` in `/me/prefs`; absent means on, as the web reads it) is
  respected the way the web does: off, no next episode is fetched, so there is no Next button, no
  Play Next in the credits and no countdown, and the player closes at the end.
- **Remembered track choices**, as the web player: choosing an audio track saves its language as
  `audio_language` (Arc then picks that language by itself when a title starts, so the app sends no
  `audio_stream` on the first decision, as before); choosing subtitles saves `subtitle_language`
  (`PATCH /me/prefs`, only that field). Off forgets the language only in the default subtitle mode,
  and forced tracks aren't a language choice (`rememberedSubtitleLanguage`). The first subtitles
  now also follow the profile's `subtitle_mode` (Plex's modes: manual = forced only, foreign = only
  when the audio is another language, always).
- **Timeline thumbnails.** While scrubbing, the picture at the scrub point shows above the timeline
  (240 dp wide), from `GET /items/{id}/trickplay?file=` (the same tile lookup as the web's `Thumb`:
  one per `interval_ms`, `columns`×`rows` to a sheet, clamped to `count`). A title without them (the
  library has them off, or they aren't made yet: 404) simply shows none; asking moves the file up
  Arc's queue, as on the web.
- Reuse: the player's round control button became `RoundIconButton` (components), used by the
  player and the title actions; codec and resolution names moved to components for the player and
  Media Info; language codes in any form ("fra", "fre", "fr-CA") now read as names in both.

## Decisions

- **Cancel at the very end closes the player**, back to the title's page (as Plex does). The web
  player stays on the last frame instead; a TV with nothing playing and no controls is a dead end.
- **Search order follows the web** (Movies, Shows, People, Episodes) rather than putting people last.
- **Watched/Watchlist are icon buttons with their name below when focused** (Plex for Android TV's
  row) rather than labelled buttons, to keep the row to one line beside Resume and Play from
  Beginning.
- Debounce 300 ms: quick enough to feel live, slow enough not to search every letter typed on a
  remote.

## Skipped, and why

- **Play Next / Add to Queue:** the web's queue lives in the web player; `/api/v1` has no queue
  for apps (only playlists), so the menu leaves them out.
- **Watchlist on episodes:** `PUT /items/{id}/watchlist` takes movies and shows only.
- **Collections and genres in search results** (the API returns them): no collection or genre page
  on the TV yet to open them in.
- **Person page** is deliberately simple (photo, name, poster grid): no biography, roles or
  department filters.
- **Saving a preference that fails** is logged, not shown (the web shows an error notice); the
  choice still applies to this playback.
- No LiveServerTest run and no emulator or TV this time (owner's slim scope): everything above is
  checked by unit and screenshot tests only, not on a device. D-pad feel of the search field (the
  keyboard opening, leaving it with Down) and of the dialogs' focus trap still needs a real TV.

## Logging

Warn: search failed (exception class only; never the words searched), person page failed, marking
watched/unwatched or the watchlist change failed (after its rollback), the profile's preferences
failing to load in the player, saving a track preference failed (field name and status). Debug:
no timeline thumbnails for a file, or fetching them failed. Nothing per keystroke, frame or tick.

## Checks run

- `./gradlew assembleDebug testDebugUnitTest`: build OK; 74 tests, 0 failures (the live test
  skipped). New: `SearchTest` (debounce, cancel-on-newer, failure, grouping), `TitleActionsTest`
  (optimistic change and rollback, show watched state), `PlayerRulesTest` (Up Next including
  Auto-Play off, remembered subtitle/audio languages, subtitle modes, trickplay tile lookup,
  language names), `ArcApiTest` additions (prefs PATCH body, played/watchlist PUTs, search, trickplay
  404).
- `./gradlew recordRoborazziDebug`: new screenshots `tv-search`, `tv-movie-actions`,
  `tv-movie-more-menu`, `tv-movie-media-info`, `tv-player-up-next`, `tv-player-scrubbing`, looked at once.

## v1 API gaps found

1. **`GET /items/{id}/trickplay` and `GET /people/{id}` have no response fields in
   `docs/api-v1-contract.txt`** (only the 200), so `info`/`sheets` and `person`/`credits` aren't
   promised. Apps rely on them; adding their fields to the contract would close it.
2. **No queue for apps**: Play Next / Add to Queue can't be offered. A server-side "up next" queue
   per device (or documenting playlists as the way) would let TV apps match the web's menu.
3. **Sheet URLs come back unversioned** (`/api/items/.../trickplay/...`), like artwork and streams
   (gap 1 in the first change note); the app rewrites them to `/api/v1/`.

## Home: one row at a time (owner's emulator feedback, 30 October)

- Like Plex, Home shows the overview in the top half and one row at a time in a band at the
  bottom, about 7 posters across. Because the band holds one row, focus can't find the next row
  by itself: Up and Down slide the neighbouring row in (skipping rows still loading) and return to
  the card last focused in it (`focusRestorer`), or a failed row's Retry button.
- Wide cards (Continue Watching) are bigger (236dp). Every card keeps its caption (title and year,
  or show and episode): the owner wants the details under posters too.
- Logged: nothing new (focus moves are ordinary UI).

## Home: reorder rows and choose posters or wide thumbnails (owner, 30 October)

- Holding OK on any Home card opens that row's options (Plex's long press): Move Row Up, Move
  Row Down, and Show as Posters or Wide Thumbnails. The menu stays open while moving, so a row can
  go several places, and the band follows the moved row.
- Changes show at once and are saved to the profile's Home layout (`PATCH /api/v1/me/prefs`
  `home`), the same layout the web's Edit Home writes, so the web follows. No server change.
- The app reads the layout fresh before each save and keeps every field it doesn't know (titles,
  filters, `posters`, `shows_only`, hidden rows, collections and playlists). Rows the TV doesn't
  show keep their places; a move swaps with the next row Home shows. A look that matches the
  row's default is saved as no style, as the web does.
- The web's third look, Backdrops (`arc`), still shows as wide thumbnails on the TV and isn't
  offered there yet. Season-vs-show posters (`posters`) are kept but not offered either.
- Logged: each save at info ("home layout saved (move|style)"); a failed save at warning with the
  error class, after which Home reloads as saved.
