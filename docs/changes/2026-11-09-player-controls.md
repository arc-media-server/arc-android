# Player controls in the Apple TV arrangement

The Android TV player's controls now follow the Apple TV app's (arc-apple `PlayerOSD`, 7–8
November). The Roku app got the same arrangement on 9 November.

## What changed

**Layout:**
- **Title:** the show's name is large at the bottom left, with "S1, E3 • the episode's name" above it. A film shows its title alone.
- **Buttons:** three round buttons sit at the right of the title: Playback, Audio and Subtitles. Each opens its menu above it. The old row (Play/Pause, Next, back and forward 10 seconds, Version, Quality, Chapters, Speed and Info) is gone: the remote and the tabs cover them.
- **Timeline:** thicker, split at the chapters, with an upright marker. The time played and a play/pause sign sit under the marker; the time left is at the right end.
- **Scrubbing:** the timeline picture shows above the marker, with the chapter's name under it.

**Tabs under the timeline:** Info, then Chapters, Cast and Episodes where the title has them. A focused tab's card replaces the title and timeline.
- **Info:** the picture, the name and overview, then "S1 · E3 · 52 min · 14 Mar 2024" with the quality badges. How the stream reaches the TV (Direct Play, Repackaged, Converting) is in small text. A round Restart button sits at the right.
- **Chapters:** a tile per chapter with its picture (from the timeline thumbnails), name and time; a play sign marks the current one. OK jumps there.
- **Cast:** a tile per actor with a round photo, name and part. It's the episode's own cast, else the show's.
- **Episodes:** the season's episodes, with "S1 · E8" on the picture and the name under it. The row opens at the one playing, and OK plays another.

**Playback menu:**
- Zoom Mode (Normal or Zoom, which fills the screen and crops the edges) and Speed (OK steps to the next speed) change in place, and the menu stays open.
- Quality and Version (when there's more than one) open their lists.
- Playback info opens the stats for nerds.

**Remote:**
- When the controls show, the timeline has the focus: Left and Right scrub, and OK pauses.
- Up goes to the buttons and Down to the tabs. Down again goes into the card's tiles.
- Up or Back from the tabs returns to the timeline. A menu closed by a choice or Back returns to its button.
- While a card is open, the controls don't hide.
- With the controls hidden, the remote works as before: OK pauses, Left and Right skip 10 seconds, and Up or Down shows the controls.

**Data:** the player loads the episode's cast (else the show's, `GET /items/{id}`) and its season (`GET /series/{id}/seasons`) after the title. Both are existing /api/v1 calls.

## Decisions

- **No Previous Episode button on the Info card** (the Apple TV app has one): the Episodes tab plays any episode of the season. Restart is there.
- **Not built, as on Apple TV:** Enhance Dialogue, Reduce Loud Sounds and a subtitle Style button. Subtitle style is in Settings, from the profile's preferences.
- **Cast tiles don't open the person's page:** that would leave the player.

## Checks

- `./gradlew assembleDebug testDebugUnitTest recordRoborazziDebug`: the build passes and all 147 tests pass. These include:
  - `PlayerOsdTest`: segments, tabs and speed steps;
  - `PlayerMenuFocusTest`, rewritten for the buttons, tabs, cards and the Playback menu.
- **Screenshots:**
  - new: `tv-player-card-info`, `-chapters`, `-cast`, `-episodes` and `tv-player-menu-playback`;
  - updated: `tv-player`, `tv-player-scrubbing`, `tv-player-skip-intro` and `tv-player-up-next`.
- Not tried on a TV or emulator yet.

## Logging

- **New:** a debug line when the show's cast or the season's episodes don't load (error type only); the tab is then left out.
- **Unchanged:** Zoom and Speed log nothing new. Speed was already silent, and zoom is a view setting.
