# Android TV design guidelines review

I checked 33 screens against Google's Android TV design guidance:
- the overscan safe area (48dp at the sides, 27dp at the top and bottom of the 960x540dp canvas);
- text size (nothing under 12sp);
- contrast (4.5:1 for text);
- focus;
- menus.

Those 33 cover every kind of screen in the app: Home, the sidebar, the library and its menus, title pages, the person page, the player and its menus, Search, Settings, profiles, PIN, sign-in, server, Watch Together and playlists. I marked the safe area on each screenshot to check it.

## What changed

- **The faintest grey text now meets 4.5:1.**
  - Hints, placeholders, "Trying again in 8s" and the sign-in server line used `#6B6B78`. That reached only 2.7–3.7:1 on Arc's surfaces. They now use `#92929F`, which reaches 4.6–6.4:1 and stays fainter than the ordinary grey.
  - A placeholder in a focused (white) field used an inline `#777777`, at 4.1:1. It now uses a theme colour at 4.8:1, so colours stay in `Theme.kt`.
- **Nothing is smaller than 12sp.** The smallest text style had fallen back to Material's 11sp. It is now 12sp. It is used by:
  - the A–Z bar;
  - the Up Next card's countdown and episode line;
  - card badges;
  - the player's method label;
  - the age-rating box;
  - Watch Together's host and status labels.

## What already met the guidelines

- **Focus:** every screen shows a clear focus (white with black text, or a white outline that scales up), and the sidebar, rows and menus move as expected with the D-pad.
- **Side margins:** headers, titles and buttons start at 48dp or more. Rows that run off the right edge do so on purpose, to show there's more.
- **Settings** already follows Google's two-pane pattern: who and what on the left, the list on the right.
- **The player's menus** already open as a panel on the right, as Google's side panels do.
- **Text sizes and contrast elsewhere:**
  - body text is 14sp and up;
  - the ordinary grey (`#A1A1AD`) reaches 5.5:1 or more on every surface.

## Left as they are, for the owner

These follow layouts the owner chose. Changing them would change the look.

- **Card menus are centred pop-overs, not side panels.** Google suggests side panels. Arc's design is shared by all its TV apps (Apple TV is the fullest), and those apps use pop-overs. Plex for Android TV uses a pop-up for "…" too. Recommendation: keep them.
- **Captions in the bottom 27dp.**
  - Home's first row and the library grid's second row put their title and year lines at about 525–535dp. That is inside the bottom overscan margin.
  - This follows the owner's "hero 60%" Home and the six-across, two-rows grid.
  - Current TVs and Google TV boxes don't crop overscan, so nothing is lost on them. An old TV with overscan switched on could crop the year line.
  - The fix would be about 17dp less height: smaller posters, or a lower hero. Recommendation: leave it unless someone sees it cropped.
- **The A–Z bar and the sidebar's icons sit in the 48dp side margins.**
  - The A–Z bar was placed in the grid's right gutter on purpose, so the posters keep their size.
  - The collapsed sidebar sits at the left edge, as Google TV's own navigation drawer does.
  - Recommendation: keep both.

## Checks

- `./gradlew assembleDebug testDebugUnitTest recordRoborazziDebug`: 310 tests, 13 skipped (live-server), 0 failed.
- I looked at the affected screenshots:
  - the library with the A–Z bar;
  - Up Next;
  - the server screen's hint;
  - the Watch Together lobby.
- **Not checked on a TV.** Whether an old TV's overscan crops anything needs a real set with overscan on.

## Logging

None: colours and text sizes only.
