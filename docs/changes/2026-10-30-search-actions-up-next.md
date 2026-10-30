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

## Home follows the library order; arranged on the web only (owner, 30 October)

- A row menu (hold OK: move rows, choose each row's look) was tried and removed the same day. The
  owner chose one model for the web and the TV (arc-media-server, "Home follows the library
  order"): Continue Watching, Up Next and Watchlist first, then each library's Recently Added and
  its filter rows in the profile's library order. Only the rows carrying on watching take a saved
  look (posters, season posters, thumbnails or Backdrops); every other row keeps its usual one.
- The TV has no Home options: the layout and library order are arranged on the web (Edit Home,
  Reorder libraries) and the TV follows them, so every app shows the same Home.
- Backdrops cards (the web's ArcArtwork: background art, darkened low, the logo or name) and
  season posters are drawn as the web draws them.
- The sidebar's entries are 40dp (48dp for the profile's two lines) with 18dp icons, as Google
  TV's own menu, drawn by Arc because Compose for TV's drawer item is 56dp; about ten libraries
  fit without scrolling.
- Logged: nothing new.

## Emulator fixes: episode captions, episodes open in their show, rows keep their margin (30 October)

- A show's page scrolls to show the seasons whole while they have focus, so episode names and
  details under the pictures aren't cut off at the bottom.
- An episode's card (Continue Watching, Recently Added, search, a person's page) opens its show's
  page on that episode's season with the focus on it, as Plex does, rather than a page for the
  episode alone. `DetailsDest` carries the episode; the show's Play button is unchanged.
- Scrolling keeps a 48dp margin beside the focused card (`minimalBringIntoView`), so coming back
  to a row's first card puts it at the row's usual start instead of against the edge. Home's band
  moves in whole rows with no margin; the rows inside it keep theirs.
- Logged: nothing new (navigation and scrolling).

## Library view: six across, two rows (owner, 30 October)

- A library shows six posters across (home videos four) with two whole rows on screen, as Plex's
  TV app appears to (Plex publishes no figure; Plex-style TV clients use 6 wide and 2 rows). The
  posters are about 112dp wide, up from about 96dp, and the heading is a little tighter. The top
  of a third row shows below, so it's clear there's more.
- The grid keeps a 12dp margin when scrolling (not the rows' 48dp), so focusing the second row
  doesn't push the grid up.
- Logged: nothing new.

## Sign Out moves to the profile picker (owner, 30 October)

- The sidebar no longer has Sign Out: Switch Profile opens "Who's watching?", which has Sign Out
  under the profiles, as Plex does. A friend's TV (signed in to someone else's server, no
  picker) keeps Sign Out in the sidebar. Signing out is shared in `AppContainer.signOut`.
- Logged: "signed out" at info, as before.

## Title pages: cast, collections, More Like This and extras (owner, 30 October)

- Under a movie's or show's page, as the web's: Cast (round pictures with the character; opens
  the person's page), a row for each collection it's in (without the title itself; a folder
  collection as thumbnails), More Like This (`/items/{id}/similar`), then Extras.
- The rows load after the page, so it shows without waiting; a row that fails or is empty is
  left out. Collection pages need arc-media-server#303 (their fields in the v1 contract).
- Extras play from their file, as the web plays them, in a simple player with Media3's own
  controls; no progress is kept. Back or the end closes it (only once).
- Search's rows and the cast share one `Shelf` row and one `PersonCard`; the player and the
  extras player share `arcPlayer`.
- Logged: an extra that fails to play, at warning with the error code. Related rows that fail
  are left out quietly; the API client already logs server errors.

## Library tabs: Recommended, titles, Collections (owner, 30 October)

- A library has tabs beside its name, as Plex's TV app and the web: Recommended (the server's
  rows, titled in the app's language from each row's type, in the style the server owner chose),
  its titles (Movies / Shows / Videos, the 6×2 grid) and Collections (posters; each opens a page
  with the collection's titles in the same grid). Moving along the tabs switches what's shown.
- A library opens on Recommended, as Plex's TV app does (the web opens on the titles; logged on
  #80). Home videos have no Recommended, as on the web; Collections shows only when the library
  has some (the TV can't make collections).
- Recommended and the collections load alongside the titles, each on its own; a failure shows
  Try Again in that tab only. Season and library tabs share `PillTabs`; the library grid and a
  collection's page share `TitleGrid`. Collection pages need arc-media-server#303.
- Logged: a library's recommended or collections failing, at warning with the error class.

## Home: seasonal, collection and playlist rows (30 October)

- The rows the TV skipped now show, as on the web: the seasonal row after Watchlist in its season
  (Romance for Valentine's Day 1–14 Feb, Horror for Halloween in October, Christmas Films in
  December; films across the libraries, shuffled daily, the web's own table), and collection and
  playlist rows last, in the order they were added. A collection or playlist row is titled with
  the name saved with it. Collection and playlist rows need arc-media-server#303.
- Logged: a row that fails, at warning with its id and the error class, as other rows.

## Half the gap beside the sidebar (owner, 30 October)

- Content starts 48dp from the left edge plus each screen's own 48dp (it was 72dp plus 48dp):
  the gap between the rail's icons and the content is about half what it was. The library grid
  gains the width, so its six posters are bigger (about 116dp wide); its rows are 14dp apart so
  two whole rows still fit inside the TV's safe area.
- Logged: nothing new.

## Background art in the top right (owner, 30 October)

- Home's and a title page's background art no longer fills the screen under a dark wash. As
  Plex's TV app, it sits in the top right, whole (16:9, 70% of the width), and only its left and
  bottom edges fade into the page, so the picture stays clear and the logo, details and rows sit
  on plain dark beside and under it.
- Logged: nothing new.

## Card menu: Watched and Watchlist from any card (owner, 30 October)

- Holding OK on any card (Home, a library, Recommended, search, a title's rows, a collection)
  opens that title's ⋯ menu, the same one as on its page (`MoreMenu`): Mark as Watched /
  Unwatched, Add to / Remove from Watchlist, and Media Info when the card's title carries its
  files. Back or a choice closes it and the focus returns to the card.
- A change shows straight away on every card of that title, whichever screen loaded it
  (`AppContainer.itemChanges`, also fed by a title page's buttons), and is saved to Arc; if Arc
  refuses, the cards go back as they were. Playing a title clears its change, since playing
  changes it on the server.
- Fixed in passing: Home now reloads its rows when you come back to it (from playing or a
  title's page), so Continue Watching is up to date.
- Logged: a card change Arc refused, at warning with the error class.

## Library: sort, filters and the A–Z bar (owner, 30 October)

- A library's titles tab has Sort and Filters buttons in its header, as the web's library page:
  - Sort: Title, Recently Added, Recently Watched, Release Date, Year, Rating, Length or Shuffle,
    then which way (A–Z / Z–A, Newest / Oldest first…). Rotten Tomatoes waits while rating
    sources are paused.
  - Filters: only things I haven't watched, and a genre, decade or quality from what the library
    has (`/libraries/{id}/facets`), with Clear Filters. The button says how many are on.
  - Choices apply at once and are kept per library while the app runs. "Nothing matches" when a
    filter leaves nothing.
- With title sort, an A–Z bar runs down the right (letters with no titles left out, # for the
  rest), as the web's LetterRail and Plex's. Choosing a letter moves to its first title.
- The grid now loads pages wherever it is, not only from the top: a position not loaded yet shows
  an empty card that fills in, and the focus stays on it. That lets a jump to a letter (or a long
  scroll) skip loading everything before it. It relies on `/items`' `total`, `offset` and
  `letters`, promised by arc-media-server#304.
- Logged: a page or the first load failing, at warning with the library id and error class, as
  before; facets failing quietly leave only the unwatched filter.

## Library: whole rows when scrolling (owner, 30 October)

- Moving down past the second row cut off the titles under the cards: the grid scrolled only far
  enough to show the focused card, not its caption. The grid now moves a whole row at a time
  (`rowsBringIntoView`), as Plex's: the next row comes up into the bottom row's place, with its
  caption, and the row above stays whole; moving up brings the row above in at the top. A
  screenshot test presses Down twice to check (`tv-library-scrolled.png`).
- Logged: nothing new.

## ASS/SSA subtitles drawn with libass (owner, 30 October)

- An ASS or SSA subtitle is now loaded as its own script from Arc
  (`/subtitles/{track}/ass`, arc-media-server#306, shifted for a converted stream as `/vtt` is,
  with the video's fonts packed in) and drawn by libass through `io.github.peerless2012:ass-media`
  0.5.1 (MIT; the library Jellyfin's Android TV app uses), so it keeps its fonts, colours,
  positions, karaoke and other effects. Other text subtitles stay WebVTT as before; picture
  subtitles are still burned in by Arc.
- libass draws in its OpenGL overlay mode on the player's subtitle view, rendering at most at
  1080p so 4K TVs aren't slowed. The app wires the library's handler itself (its one-call
  builder wants the subtitle view before the player exists): only the script the app loaded
  ("arc-" ids) goes to libass; any ASS track inside a direct-played file goes to Media3's own
  parser, and the app's track is selected explicitly over one of the same language in the file,
  so libass only ever has the one track it's drawing.
- Not checked on a device yet: rendering, timing after a seek on a converted stream, HDR, and
  performance on a real TV (#135). The library's OpenGL overlay doesn't draw HDR colours in the
  subtitles themselves (its known limit); the video is unaffected.
- Native libraries add about 3 MB per ABI to the APK; a release build can split by ABI.
- Logged: subtitle failures surface as before through playback errors; the server logs scripts
  and fonts it can't read.

## Title pages match Home, and the cast shows on arrival (owner, 30 October)

- A title page's header is now the same summary as Home's overview (`TitleSummary`): the logo at
  72dp (was 84), one details line with the genres in it (they had their own line), and three
  lines of description in the same type, 36dp from the top and 48dp from the edge (Google TV's
  edge; Home's 96dp is past its sidebar, which a title page doesn't have, and left an empty strip). The tagline is left out, as on Home. The page's rows and season tabs line up with it.
- The shorter header leaves room for the cast row whole under the buttons when the page opens
  (it was cut off).
- Logged: nothing new.

## Labels on title pages (owner, 30 October)

- A title's page shows the web's labels as small badges under its details line: the age rating,
  then for one version the picture (4K, Dolby Vision, HDR10+, HDR10, HLG) and the main sound
  (e.g. TrueHD Atmos 7.1), or how many versions there are. They come from the title's files
  (`formatLabels`, with the naming already used by Media Info and the player, now shared in
  Controls.kt). Owner: labels belong on the title page only, so Home's overview keeps the age
  rating in its details line and shows no badges.
- Logged: nothing new.

## Rows return to their own start (owner, 30 October)

- Moving along a row and back left the first card out of line with the text above it (owner's
  screenshot: a show's episodes). Two causes: a row's scrolling kept the page's margin rather
  than the row's own start (the title page briefly had a 96dp start with 48dp scrolling), and a
  focused card is drawn up to 8% larger, so lining up its enlarged edge stopped a few dp short.
  Every row (`Shelf`, a show's episodes) now scrolls with its own start, and the scrolling keeps
  8dp of slack, so going back to the first card stops at the row's real start. Screenshot tests
  go along the cast and the episodes and back (`tv-movie-row-back.png`, `tv-show-row-back.png`).
- Logged: nothing new.

## Ultra Blur behind the art (owner, 30 October)

- As Plex's "Ultra Blur": behind Home's and a title page's art, the screen is now a soft wash of
  the art's own colours instead of black. A tiny copy of the backdrop (48×27) gives the average
  colour of each quarter; each is darkened (its brightest channel capped at 42%, then mixed a
  quarter towards the page colour) so white text stays readable, and drawn as a glow from its
  corner. The colours fade over 0.7 s as the focus moves. The art in the top right now fades to
  see-through at its left and bottom edges, so the wash shows through instead of black.
- Plex's server works these colours out; Arc's app does it from the artwork it already loads,
  so there's no server change (logged on #80). Until the colours are known the page is plain.
- Logged: nothing new (a failed colour read leaves the plain page).
