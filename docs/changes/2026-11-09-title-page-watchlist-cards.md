# Title page cards, quality capsules, Play labels, bigger cards, Watchlist pills

The rest of the Apple TV app's 7 November changes, on Android TV.

## What changed

**Title page:**
- **Play button:**
  - It is glass like the round buttons beside it, and turns white only while it has the focus.
  - Its label says what will play:
    - "Play" for a film;
    - "Start watching · S2, E1" for a season's first episode;
    - "Play S1 E8" for another episode.
  - Part watched, it shows a bar of how far through, then what's left ("S1 E3 · 31 min", or "1 h 20 min" for a film).
- **Badges:**
  - The age rating is in an outlined box. The quality follows as capsules: the resolution filled white ("4K"), then outlined capsules split by a line ("DV | HDR10", "TrueHD | 7.1", "Atmos"), and "SDR" for a picture without HDR.
  - They sit on their own line under the details.
  - `playback/Quality.kt` has the rules (`capsules`, `summary`), with tests.
- **About section:** the old Information block becomes the About section:
  - an About card across the top, with the title in bold and the overview;
  - then Details (Released, Run Time, Rated, Genre, Original Language), Crew (Directed By, Written By, Studio) and Playback (Server, Quality as words such as "4K · HDR10 · Dolby Atmos", Bitrate, Size, Edition, File) side by side.

**Cards:** library posters are 132 dp wide (were 116) and wide cards 220 dp (were 172). Home's posters are 132 dp (were 118). Fewer fit across, so art and names read from a sofa.

**Watchlist:** two pills at the right of its name:
- All, Movies or TV Shows;
- Date Added (Arc's order), Title (ignoring a leading "The", "A" or "An"), or Release Date (newest first).

Each opens a floating list under it, as the library's Sort does. The rules are in `data/WatchlistView.kt`, with tests.

## Decisions

- **The sidebar still dims the page when it opens.** The Apple TV app doesn't dim, because its glass blurs what's behind. Compose for TV's drawer has no blur, and it draws the page over the panel's edge, so without the shade the page's words showed through the names. Logged on arc-media-server#80.
- **Opened pages already have no sidebar.** Titles, collections, playlists and people are pages of their own, so the Apple TV app's "sidebar steps aside" needed nothing here.
- **No Ratings card,** as on Apple TV, because Arc's ratings are paused.
- **The Playback card doesn't show Profile or Connection.** They're on Settings' About page.

## Checks

- `./gradlew assembleDebug testDebugUnitTest recordRoborazziDebug`: the build passes and all 159 tests pass, including `QualityTest` and `WatchlistViewTest`.
- **Screenshots I looked at:**
  - new: `tv-movie-about-cards`;
  - updated: `tv-movie`, `tv-show-episodes`, `tv-watchlist`, `tv-movie-related` and the others with cards.
- Not tried on a TV or emulator.

## Logging

None added: layout and sorting only. The data comes from loads that already log their failures.
