# Phone and tablet interface: first version

The owner, 28 November: Android support for phones and tablets, following Android's design
language but still Arc. On a phone or tablet the same app now opens a Material 3 interface built
for touch. Android TV and Google TV keep the TV interface, unchanged.

## What changed

- **One app, two interfaces.** `FormFactor.isTv` picks the interface at launch: the TV's on a
  television (its UI mode, or the leanback feature every Android TV declares), the phone's
  everywhere else. The manifest no longer locks the app to landscape. Phones draw edge to edge.
- **Shared, not copied.** The phone's screens use the TV's view models: launch, server, sign-in
  code, profiles, Home rows, library pages, search, title pages, person pages, the player and
  settings. They also use its API client, session store, playback decisions and resume reporting.
  The video view (`VideoSurface`) is extracted from the TV's player so both draw video and
  subtitles the same way. Track names and episode numbers come from the same helpers.
- **Material 3 in Arc's colours** (`ArcMobileTheme`, in `ui/theme/Theme.kt` with the TV's): dark
  only, with white as the only emphasis. Filled buttons are white with black text, the chosen tab
  sits on Surface3, and the teal stays the logo's. Material's standard phone type scale.
- **Screens** (`ui/mobile/`):
  - **Server**: type an address or pick one found on the network.
  - **Sign In**: a Password tab (the household password, or a friend's name and password) and a
    Code tab (the TV's Link a Device code).
  - **Who's watching?**: a grid of avatars, with a number keyboard for a PIN.
  - **Tabs**: Home, Libraries and Search in a bottom navigation bar on a phone, or a navigation
    rail from 600dp wide. Arc's logo and the profile's avatar are at the top, and the avatar
    opens Settings.
  - **Home**: the profile's rows from Arc, each in its own card style. Tapping a queue card plays
    it.
  - **Libraries**: the libraries in Arc's order, plus the Watchlist. A library opens as a poster
    grid that loads a page at a time.
  - **Search**: results as you type, in Top Results, Episodes and People.
  - **Title page**: artwork, logo, details line, then Play or Resume (with Play from Beginning).
    Under that are Mark as Watched, the Watchlist and Shuffle, then the description (tap for all
    of it). A show adds season chips and episode rows. Cast and related rows come last. Opened
    from an episode's card, it opens on that season, scrolled to the episode.
  - **Person page**: photo, what they're known for, and their films, shows and crew work.
  - **Player**: turns sideways and hides the system bars. Tap shows or hides the controls.
    Double-tap the left or right half to go back or forward 10 seconds. It has the slider with
    time left, Skip Intro, Recap or Credits, Up Next's countdown at the end, and Audio and
    Subtitles in a bottom sheet. Leaving the app pauses.
  - **Settings**: Switch Profile, the profile's playback switches (Auto-Play, Skip Intros, Hide
    Spoilers, Show missing episodes), the server (Change, Sign Out) and About.
- **API**: `ArcApi.login` (`POST /auth/login` with the device's name) and `ArcApi.friendLogin`
  (`POST /auth/friend`). Both are existing v1 endpoints, so the server needs no change.

## Decisions

- **One app, chosen at launch, as Plex does.** Plex ships one Android app whose phone and TV
  interfaces differ. The alternative, a separate phone app, would split sign-in, settings and
  releases for no gain.
- **No `core`/`tv`/`mobile` module split yet.** `CLAUDE.md` plans that split for when the phone
  interface starts. It moves every file in the app, so doing it here would conflict with every
  open TV pull request (#81 and others). The phone's code lives in its own package,
  `ui/mobile/`, and depends only on view models and `data/`/`playback/`. The split is then a
  mechanical move in a pull request of its own once the open ones are merged. Say if you'd rather
  have it first.
- **Password first, code second.** Plex's phone app signs in with an account. Arc's equivalent is
  the household password (Who's watching next) or a friend's own name and password. The TV's code
  stays one tab away for a phone without the password. The password is sent once and never kept;
  only Arc's device token is stored, encrypted, as the TV stores it.
- **Three tabs, with the profile at the top right.** Home, Libraries and Search are what Plex's
  phone app and Google's own apps put in the bottom bar. Settings sits behind the avatar, as in
  Google's apps. The Watchlist is an entry in Libraries rather than a fourth tab. A navigation rail
  replaces the bar from 600dp, as Material's guidance says.
- **Double-tap to seek 10 seconds** is the gesture Android's players (YouTube, Plex) use. It uses
  the same 10 seconds as the TV's left and right.
- **Leaving the app pauses.** Picture-in-picture and background audio are for later.

## Not yet on the phone

Left for following pull requests, all on the TV already:

- the card menu (a long press);
- library sort, filters and tabs (Recommended, Collections, Folders);
- collection and playlist pages;
- Watch Together (its Home row is hidden on the phone until then);
- pre-roll videos and theme music;
- Play Version, ratings and Add to Playlist;
- the player's Quality, Speed, Chapters and Subtitle Timing menus;
- picture-in-picture, Chromecast and downloads.

Some shared error strings still say "this TV".

## Checks

- `./gradlew assembleDebug testDebugUnitTest recordRoborazziDebug`: passed. 347 tests, 13 skipped,
  0 failures.
- New tests:
  - `FormFactorTest`: a television gets the TV interface; a phone and a sideways tablet don't.
  - `ArcApiTest`: the password and friend sign-in requests, and a wrong password's message.
- 14 new Roborazzi screenshots, `phone-*` and `tablet-*` (`2026-11-28-phone-interface/`). They
  cover the server, sign-in, profiles, Home, a library, search, a film, a show, a show opened on
  an episode, a person, Settings, the player's controls, and a tablet's Home and show.
- The TV's screenshots were recorded again. They aren't kept in git, so there was no before and
  after to compare. The shared `VideoSurface` is the TV player's own code moved as it was, and
  Robolectric doesn't draw video anyway.
- **Nothing was tried on a phone, tablet or emulator.** Sign-in, playback, rotation, the system
  bars and double-tap seeking are untested on a device, and so is the TV, which should look
  and behave exactly as before.

## Logging

- At launch, info: which interface started.
- Sign-in, info: a password sign-in that worked (household or friend). Never the password, the
  name or the token.
- A sign-in that failed for any reason other than a wrong password is a warning, with only the
  exception's type.
- The player, info: a marker the viewer skipped.
- Everything else is logged by the shared view models, as on the TV.
