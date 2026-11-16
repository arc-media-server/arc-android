# Hide Spoilers on the TV

The profile's **Hide Spoilers** (`hide_spoilers` in `/api/v1/me/prefs`, set on the web) was read by
the TV but never used. It now works as on the web (`useSpoilerGuard` in `web/src/lib/home.ts`), and
can be switched in the TV's Settings.

## What changed

**Which episodes:** while Hide Spoilers is on, an episode not yet started (not watched, no resume
point) keeps its picture and description back. Watched or part-watched episodes, films and shows
are unchanged. The rule is in `data/Spoilers.kt`.

**Pictures** (`EpisodeArtwork`): the show's background instead of the episode's still. When Arc
has no background for the show, the still is blurred, as on the web. Used on:
- episode cards everywhere (Home's rows, Recently Added, search, a person's page) and Backdrops
  cards;
- a show's page;
- the player's Up Next card and its Episodes tab.

**Descriptions** (`spoilerSafeOverview`): "Description hidden until you watch it." (the web's
words) in place of the episode's description, on:
- a show's episode cards;
- Home's hero;
- an episode's own page (the description, its full text and the About card).

**Settings:** a Hide Spoilers row under Playback, after Skip Intros Automatically. It's saved to the
profile with `PATCH /me/prefs`, so the web and other apps follow it.

**Where the setting comes from:** `AppContainer.hideSpoilers`, filled whenever the app reads the
preferences (Home, Settings, the player) and changed at once from Settings. It's put back if saving
fails, and reset when the profile changes. Every screen reads it through `LocalHideSpoilers`.

## Decisions

- **Same rule as the web,** including "started counts as seen": a part-watched episode shows its
  still and description.
- **The player's Info card isn't hidden:** that's the episode being watched.

## Limits

- **Blur needs Android 12.** On older TVs the fallback still shows unblurred; most episodes use
  the show's background instead, so this only affects shows Arc has no background for.
- **Changed on another device:** the setting is picked up the next time Home, Settings or the
  player loads the preferences (no live updates yet).

## Checks

- `./gradlew assembleDebug testDebugUnitTest recordRoborazziDebug`: passed (163 tests, 1 skipped,
  0 failed). Built through Google's copy of Maven Central (maven-central.storage-download.googleapis.com)
  because Maven Central was rate-limiting this environment.
- New tests: `SpoilersTest` (which episodes are hidden; background, blur or still) and the
  screenshot `tv-show-spoilers-hidden`, which moves to Season 1's episodes so a started episode
  (3, as usual) sits beside unstarted ones (4 and 5 with the show's background, 6 blurred) and
  their hidden descriptions. Checked.
- The screenshot `tv-settings` gains the new row.
- Not tried on a TV or emulator.

## Logging

None added. Saving the setting uses Settings' existing log lines: "settings: saved hide_spoilers"
at info, and a warning with the exception type if it fails. Nothing per card or per frame.
