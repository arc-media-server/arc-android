# Show missing episodes is one setting in Settings

At the owner's request, Show Missing Episodes is now one setting for the profile, not a per-show
choice. arc-media-server#495 added `show_missing_episodes` (off unless turned on) to
`/api/v1/me/prefs`, and the web made the same change in arc-media-server#497. Pictures:
[2026-11-21-show-missing-setting/](2026-11-21-show-missing-setting/).

## What changed

- **Settings:** a "Show missing episodes" switch under Playback, after Hide Spoilers. It is saved
  to `show_missing_episodes`, like the other switches, so it follows the profile to every app.
- **Show page:** it asks for `/series/{id}/seasons?missing=1` only while the setting is on. The
  app reads the setting with the other profile settings, and live updates keep it current when
  another app changes it.
- **Season menu:** "Show Missing Episodes" / "Hide Missing Episodes" are gone, along with their
  strings.
- **Test:** the setting is off when the server doesn't send it, and on when it says so.

## Decisions

- **Label:** "Show missing episodes", in sentence case, to match the web's label. The other rows in
  Settings use title case ("Hide Spoilers"). I kept the owner's wording as given.
- **Off by default**, as the menu choice was.
- **When it takes effect:** a show page opened after the setting changes uses it. Plex has no
  setting like this to follow.

## Checks

- `./gradlew assembleDebug testDebugUnitTest recordRoborazziDebug`: passed. 272 tests ran with 0
  failures. 13 were skipped: the live-server tests, because no shared library was running.
- **Pictures:** I looked at the Settings screen before and after. These are Robolectric renders,
  not emulator or device captures.
- **Not tried:**
  - no emulator or device
  - `LiveServerTest`, because the API client is unchanged apart from reading one more setting

## Logging

The menu's debug line ("missing episodes shown/hidden") is gone with the menu. Saving the setting
goes through the existing Settings save, which already reports failures.
