# More library filters: watch status, HDR, video and audio formats, channels

The web's Filters panel can narrow a library by watch status and by what the files are (HDR,
video format, audio format, audio channels). The TV only had Unwatched, genre, decade and quality.
It now has the rest, from the same answers (arc-media-server#347, `FILTER-DISCOVERY-API.md`).

## What changed

**New rows in a library's Filters list,** each showing what it's set to, each opening its own list
(Any, then the choices; choosing goes back to Filters):
- **Watch Status:** Watched or Not Started (`watch_state` `played` / `unstarted`).
  - It and Unwatched only exclude each other, as Arc requires and the web does: choosing one turns
    the other off.
- **HDR:** Dolby Vision, HDR10+, HLG, HDR10, SDR, or "Not known yet" (`dynamic_range`).
- **Video Format** and **Audio Format:** HEVC, H.264, AV1…, Dolby TrueHD, Dolby Digital Plus,
  DTS…, using the names the app already uses (`video_codec`, `audio_codec`).
- **Audio Channels:** 7.1, 5.1, Stereo…, most first (`audio_channels`).

**When the new rows show:**
- The four media filters show only when the library has more than one value
  (`GET /api/v1/libraries/{id}/facets`, which now lists them), or one is already chosen, as the
  web's `offered`. A library that's all HEVC has no Video Format row.
- A value already chosen stays in its list even if the library no longer has it, so it can be
  seen and changed.

**Other changes:**
- **Clear Filters** now clears every filter and keeps the sort.
- The filter count beside Filters counts the new ones.

**Code:**
- `LibraryQuery` gains the five filters and their `/items` parameters, plus `withUnwatched`,
  `withWatchState` and `cleared`.
- `Facets` reads the four new lists.
- The Filters list's pages share one helper.

## Checks

- Here: `./gradlew assembleDebug` passed, and the unit tests compiled
  (`compileDebugUnitTestKotlin`). Running them was blocked: Robolectric's runtime jar redirects to
  a Maven mirror this session's network refuses (403).
- The full check is in the PR.
- New tests:
  - `LibraryQueryTest`: parameters, the Unwatched / watch status rule, Clear Filters, when a row
    is offered, channel order;
  - `ArcApiTest`: the facets' new lists.
- New screenshot: `tv-library-filters-media` (Video Format left out: one format only).
- Not tried on a TV or emulator.

## Logging

None added. The facets and titles requests already log their failures.
