# Person page, collection overview, Settings and About

Following the Apple TV app (arc-apple `PersonView`, `CollectionView`, `SettingsView`, 7 November),
as the Roku app did on 9 November.

## What changed

**Person page** (artwork-led):
- **Top:** their photo in a large circle beside their name, with "Acting · 4 titles in your libraries" under it.
- **Focused title:** a line names the title in focus and their part in it ("Paper Lanterns (2021) · as Ada", or "· Director" behind the camera). The background is that title's backdrop, faded at the left and bottom.
- **Rows:** Movies and TV Shows they act in, then Behind the Camera (directing, writing, creating). Each row is newest first, with each title once. The focus starts on the first title.
- **Code:** the rows, the count, the main role and their part are in `data/PersonRows.kt`, with tests.

**Collection page:** the collection's overview (from `GET /collections/{id}`, already in /api/v1) is under its name, two lines at most. `CollectionRef` gains `overview`.

**Settings** (tvOS's own layout):
- **Layout:** "Settings" at the top, the profile's circle, name and server centred in the left half, and the list in the right half.
- **Profile:** comes from `GET /auth/status`.
- **About row:** replaces the Version row, shows the version and opens About.

**About**, in the same layout:
- **Left:** Arc's logo, "Arc for Android TV", the version and the TMDB/TheTVDB credit.
- **Right:**
  - this app's version;
  - the server's name, Arc version and API version (`GET /health`);
  - the address (scheme, host and port only), and the connection, Local network or Remote (`/auth/status`'s `local`, read into `AuthStatus`);
  - Open Source Licences, a list:
    - Arc for Android TV (AGPL-3.0);
    - AndroidX, Compose and Media3, OkHttp, Coil, Kotlin and kotlinx (Apache 2.0);
    - ass-media (MIT);
    - libass with FreeType, HarfBuzz and FriBidi.
- **Back:** returns to Settings on the About row.

## Limits

- **Logos:** the TMDB and TheTVDB logos wait on the files (arc-media-server#64). About has the credit's words for now.
- **Licences:** the screen names each component and its licence. The full licence texts are still needed before a Play Store release.
- **Person API:** it has no biography or birth details, so the page has none.

## Checks

- `./gradlew assembleDebug testDebugUnitTest recordRoborazziDebug`: the build passes and all 153 tests pass, including `PersonRowsTest`.
- **Screenshots I looked at:**
  - new: `tv-person` and `tv-settings-about`;
  - updated: `tv-settings` and `tv-collection-play`.
- Not tried on a TV or emulator.

## Logging

- **Settings:** a debug line when the sign-in status or the server details don't load (error type only). That part of the page is then left out.
- **Person page:** its failed loads are logged as before.
- Nothing logs the address.
