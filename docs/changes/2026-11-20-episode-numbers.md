# Episode numbers written the same way everywhere

The owner's rule for every Arc app (arc-media-server `docs/AGENT-RULES.md`, "Episode numbers",
added by arc-media-server#493): an episode is "S1 · E4", with no leading zeros, no comma, and "·"
as the only separator. A special (season 0) is "Special 3". Before this, the app wrote it four
ways: "S1 · E4" on cards, "S1, E4 • Name" in the player, "Play S1 E8" and "S5 E12 · 43 min" on
the title page's button, and "Start watching · S2, E1". Pictures:
[2026-11-20-episode-numbers/](2026-11-20-episode-numbers/).

## What changed

- **One place writes the number:** `episodeCode(season, episode)` in `ui/components/Cards.kt`
  gives "S1 · E4" or "Special 3". Every screen goes through it, or through
  `episodeWithTitle(item)` ("S1 · E4 · Pilot") where the episode is the subject.
- **Strings** (`strings.xml`):
  - `episode_code` stays "S%1$d · E%2$d".
  - New: `episode_special` ("Special %1$d") and `episode_with_title` ("%1$s · %2$s").
  - `action_start_watching` is now "Start watching · %1$s".
  - Removed: `action_play_se`, `action_left_episode` and `player_episode_line`.
  - The existing `action_play_episode` ("Play %1$s") and `action_resume_episode`
    ("Resume %1$s") are now used.
- **Cards:** the line under the show's name is just "S1 · E4". Landscape episode cards (such as
  Continue Watching as Thumbs) used to add the episode's name to that line; they don't any more.
- **Player:**
  - The title line is "S1 · E3 · …And the Bag's in the River" (was "S1, E3 • …").
  - The Info card's line and the Episodes card's badges use the shared number, so a special
    reads "Special 3".
- **Title page:**
  - The episode page's line under the logo is "S1 · E4 · Pilot".
  - The Play button reads "Resume S1 · E3", "Start watching · S1 · E1" or "Play S1 · E4". A
    special reads "Play Special 1", never "Start watching".
- **Home:** the hero and the top-left summary read "S1 · E2 · Half Loop", through the same helper.
- **Up Next** ("Breaking Bad · S1 · E4") was already right. It now writes specials as "Special N".
- **Test:** `EpisodeNumbersTest` pins the wording:
  - "S1 · E4" and "S12 · E10"
  - "Special 3"
  - "S1 · E4 · Pilot"
  - an unplaced episode's name alone
  - a card's line with the number only
- **README:** `player.jpg` and `show-episodes.jpg` are retaken. They are the two README pictures
  whose episode text changed. `home.jpg` is unchanged, because its Backdrop cards already showed
  the number alone and its hero already read "S1 · E2 · Half Loop".

## Decisions

- **Resume button:** a part-watched episode's button was "S5 E12 · 43 min" (what's left, as the
  Apple TV app does). Under the rule it is "Resume S5 · E12". The progress bar on the button still
  shows how far in you are. A part-watched film still shows the time left.
- **Title page episode list:** it shows "EPISODE 4" above each episode's name, under the season
  tab. That label is only the episode number, with no season, so it doesn't fall under the rule and
  is left as it is. The Episodes card in the player is also left as it is: it shows "S1 · E8" on the
  picture with the name on its own line, so the name already has a line of its own.
- **Episodes outside the order** (Arc's season 0, episode 0) still show no number, only their
  name, as before.
- **The Watch Next row on the TV's home screen** gets the numbers, not text. The launcher writes
  them, so the app can't change how they look there.

## Checks

- `./gradlew assembleDebug testDebugUnitTest recordRoborazziDebug`: passed, 272 tests, 0 failed.
  13 were skipped because the shared library wasn't running for that run: `LiveServerTest`, the
  recorder, and `LiveScreenshotTest`'s 11 shots.
- `LiveScreenshotTest` was then run against the shared library on arc-media-server main
  (c49f7a6), started with `node tools/screenshots.mjs --serve`. Command:
  `./gradlew recordRoborazziDebug --tests '*LiveScreenshotTest*'`. All 11 shots were drawn with
  0 failed images. Those are Robolectric renders, not emulator or device captures.
- **Pictures:** I looked at
  - the README's Home, player and Breaking Bad renders
  - the regular screenshots: Home, the player, Up Next, the show page's Play button and its
    episode rows
- **Not tried:**
  - no emulator or device; the emulator can't run here
  - `LiveServerTest`, because the API client is unchanged
  - nothing about playback is claimed

## Logging

No logging changes.
