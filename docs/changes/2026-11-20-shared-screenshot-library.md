# README screenshots from Arc's shared screenshot library

The README's pictures are retaken from Arc's shared screenshot library (arc-media-server#489, merged
as daba119), the same titles every Arc app shows. They come from a new opt-in test,
`LiveScreenshotTest`, that draws the app's real screens with the real `ArcApi` against a running
copy of that library. **They are Robolectric renders of the real screens against the shared
library, not emulator or device captures.** The emulator can't run in the container this was made
in (`emulator -accel-check`: "KVM requires a CPU that supports vmx or svm"; no `/dev/kvm`).

The hand-written titles, synopses, cast and episodes the other screenshot tests used are gone. Those
tests now use responses recorded from the same library. Pictures: [the README's, and a few of the
regular screenshot tests'](2026-11-20-shared-screenshot-library/).

## What changed

- **`LiveScreenshotTest`** (`app/src/test/.../screenshots/`), in `LiveServerTest`'s style:
  - **Opt-in:** it is skipped (JUnit assumption) when nothing answers at `ARC_SCREENSHOTS_URL`
    (default `http://127.0.0.1:18299`, the library's `--serve` address).
  - **Session:** it signs in with the library's household password (`screenshots-only`), picks
    Jordan, and renders at TV size and density: 960x540 dp at xhdpi, so 1920x1080.
  - **Screens:** Home, Dune: Part Two, Breaking Bad's seasons, the Movies library (Recommended and
    Library), the profile picker, Search for "Dune", Settings, Sign In, the player's controls and
    its Episodes card. Each is drawn through the app's own route and view model (`BrowseRoute`,
    `DetailsRoute`, `ProfilesRoute`, `SignInRoute`, `SearchViewModel`), inside the same
    `ArcScreens` wrapper the app uses.
  - **Images load for real:** Coil fetches them from the server over the app's own HTTP client.
    An `EventListener` counts loads started, in flight and failed. Each shot waits until its
    screen has loaded, then until no image is in flight and none has started for a while, then
    captures. There is no fixed delay. Crossfade and hardware bitmaps are off so the capture sees
    the finished pixels. Every shot reported 0 failed images.
  - **Output:** `app/screenshots/readme-*.png`, copied into `docs/screenshots/` as 1280x720 JPEGs,
    the size the README used before.
- **Recorded responses:**
  - **Where:** `app/src/test/resources/screenshot-library/`: the Movies library, Dune: Part Two,
    Breaking Bad and its seasons, Timothée Chalamet's page and the profiles.
  - **How:** `ARC_SCREENSHOTS_RECORD=1` with the same test command re-records them.
  - **Cleaned:** the recorder drops `files`, `stream_url` and `path`, and fails if a file contains
    the server's address or the session token.
- **`Fixtures`:** builds the screenshot data from those recordings. Every title, synopsis, cast
  member, episode and profile now comes from the library. Images are still drawn stand-ins
  (`art:` keys), so the ordinary `recordRoborazziDebug` run needs no server, no network and no
  keys. `ScreenshotTest`, `SeasonTabsFocusTest` and `PlayerMenuFocusTest` use the recorded data.
  The Shuffle Season test now works its episode count out from the season instead of assuming 3.
- **Removed:** `tools/fetch-screenshot-art.py`, which downloaded TMDB artwork for the old fixtures,
  and its `.gitignore` entry.
- **README:** new pictures, alt text naming the titles, the credit line under the pictures, and
  how to retake them.
- **App code (no behaviour change):**
  - `ArcApplication.createContainer()` is overridable, and `AppContainer` takes its `SessionStore`,
    so the test can keep a session without the Android Keystore, which Robolectric doesn't have.
  - The composition locals and card menu host around every screen moved from `ArcNavHost` into
    `ArcScreens`, which the test reuses. Most of `ArcNavHost`'s diff is re-indentation.

## Decisions

- **The player is drawn over the episode's still, not video.** Robolectric can't decode video.
  - **Shot:** the real `PlayerControls` drawn over Breaking Bad S1 E3's still, at Arc's resume
    point (12:00) in its 48-minute runtime.
  - **Plan:** comes from a real `POST /playback` call, whose session is then stopped.
  - **Cast and episodes:** from the server.
- **Home is drawn only after its rows have loaded.** When Home was drawn while its rows were still
  arriving, the render sometimes ended up scrolled past the hero. Rows landing out of order moved
  the focused row. This hasn't been seen on a device. It may be worth a look on a real TV, so it's
  noted here rather than fixed blind.
- **Season 2 comes from the Missing Episodes listing.** The library has files only for Season 1.
  The recorder keeps `seasons?missing=1`'s Seasons 0–2, and `Fixtures` treats Seasons 1 and 2 as on
  disk, so the season tabs, the "missing" screens and the specials ("Not in This Order") have real
  episodes to show. Season 0's specials stand in for the episodes the order doesn't place.
- **Alex is given a PIN in the fixtures**, so the PIN screens have someone to ask for one. The
  library's profiles have none.
- **Kept hand-written:** data the library doesn't have and that isn't a title. That covers file
  and plan details (codecs, versions, bitrates), chapter names, the Dune collection's overview and
  the home-video folder names.
- **Plex:** Plex's store pictures are marketing captures with licensed artwork. Arc uses its shared
  library so every app shows the same titles, with TMDB credited.

## Server gap found

`GET /api/v1/items/{id}` for an episode returns no `series_title`, `season_number` or
`episode_number`, although `docs/api-v1-contract.txt` lists them. List endpoints such as
`/home/continue` include them, and season listings have the numbers but not the series title. In
the server, `toDTO`/`addDetails` in `internal/api/api.go` don't fill them. The app's player would
therefore show the episode title where the show's name belongs. The live test builds the
playing episode from the season listing and the show's title instead, with a comment saying why.
This needs a server fix. No server PR comes with this change.

## Checks

- `./gradlew assembleDebug testDebugUnitTest recordRoborazziDebug`: passed, 271 tests, 2 skipped
  (`LiveServerTest`, which has no scratch Arc of its own here, and the recorder, which runs only
  with `ARC_SCREENSHOTS_RECORD=1`), with the shared library running, so `LiveScreenshotTest` ran
  and drew all 11 screens with 0 failed images.
- **Pictures:** I looked at all 11 README pictures and, among the regular screenshots, Home, the
  person page, Missing Episodes, Not in This Order and the player's Cast card.
- **Recordings:** grepped for the server address, `http`, `/tmp` and the token: none.
- **Not tried:**
  - No emulator or device capture, because there is no emulator here.
  - Nothing about playback, HDR or passthrough is claimed.
  - `LiveServerTest` wasn't run, because the API client is unchanged.

## Logging

No app logging changes. The test prints only counts ("home drew 9 pictures, 0 failed") and the
exception class of a failed image, never URLs or the token.
