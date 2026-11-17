# Theme music on title pages

The web plays a film's or show's theme tune quietly while its page is open, as Plex does, when the
profile's Theme Music is on (`theme_music` in `/me/prefs`, on unless turned off). The TV didn't.
It now does, the same way (`web/src/components/ThemeMusic.tsx`).

## What changed

- **On a film's or show's page with a tune** (`theme_url` on the title, served by
  `GET /api/v1/items/{id}/theme`):
  - the tune plays once, fading in to a quarter of the volume (the web's 0.25) over about three
    quarters of a second;
  - it fades out when the page is left, or when anything opens over it (the player, a trailer,
    another page).
- **Once per visit:** coming back from the player doesn't start it again; opening the title anew
  does.
- **Episode pages** don't play it, as on the web.
- **A tune that won't play** is skipped silently; the page carries on.
- **Settings:** a **Theme Music** row under Playback, saved with `PATCH /me/prefs`, so the web and
  other apps follow it.
- **Code:**
  - the profile's preferences that every screen follows now go through
    `AppContainer.applyPrefs` (Hide Spoilers and Theme Music);
  - `ThemeTune` (`ui/details/ThemeMusic.kt`) has the rules and the fade;
  - the tune plays on an audio-only player made like the main one (`arcPlayer`, signed in to Arc).

## Checks

- Here: `./gradlew assembleDebug compileDebugUnitTestKotlin` passed. The tests couldn't run in this
  session (a Robolectric jar comes from a mirror its network refuses).
- The full check is in the PR.
- New `ThemeTuneTest`:
  - which pages play a tune and when Theme Music stops it;
  - the fade's steps;
  - `theme_music` absent reads as on, and `theme_url` is read.
- The screenshot `tv-settings` gains the Theme Music row.
- **Needs a real TV (or an emulator with sound):** hearing it, its volume against the TV's, and
  the fades. Not tried on one.

## Logging

- Debug only: a tune that didn't play, with Media3's error code name, never the address.
- Saving the setting uses Settings' existing log lines.
