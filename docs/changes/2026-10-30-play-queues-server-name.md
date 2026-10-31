# Play queues and the server's name

The TV side of the server's handoff (arc-media-server docs/BACKEND-API-HANDOFF.md):
`/capabilities`, `/identity` and play queues. Pairs with arc-media-server#316 (the web side).

## What changed

- **Server name.** The profile picker shows the server's name under "Who's watching?" (as set in
  Arc's Settings → Access & devices). Arc only gives it once a profile is chosen
  (`GET /identity`), so the TV remembers the last name it learnt for the picker. The name is
  forgotten when the server address changes.
- **Capabilities.** `GET /capabilities` is read once per profile. Queue features only show when
  `play_queues` is supported, available and allowed. An older Arc gets none of this, and every
  queue call does nothing.
- **Play queues** (`data/PlayQueues.kt`, one store in AppContainer). Calls run one at a time with
  the latest revision. A 409 (the queue changed elsewhere) reads the queue again and retries the
  edit once on it.
  - Playing a title starts a new queue of it, replacing the last one, as the web player does.
  - The player plays on through the queue. Next, the Up Next countdown and the credits button go
    to the queue's next playable entry, whatever Auto-Play is set to, because it was queued on
    purpose. Moving on advances the queue, or selects when it isn't the next entry.
  - When the queue has nothing after the current title, Auto-Play's next episode is added to the
    end, so the queue stays a true record.
  - A queue that plays to its end is deleted. Leaving the player with Back keeps it: the TV has no
    mini player, so Back is how you browse to add more.
  - **Home's Queue row** comes first, while there's a queue to carry on. It shows the current title
    and what follows (unavailable entries are left out), and it's there again after the app
    restarts (`GET /play-queues/current`). Picking a card plays on from that entry.
  - **Play Next / Add to Queue** are in every card's menu and the title page's ⋯ menu, for movies,
    episodes and shows (a show adds its episodes). A note at the bottom says what happened. With no
    queue yet, they start one without starting playback, and the Queue row appears.
  - **Shuffle** is on a show's page (after Play): its available episodes in a random order, as a
    new queue, starting with the first.

## Decisions (logged on arc-media-server#80)

- Queue entries play on even with Auto-Play off.
- Back keeps the queue; playing it to the end deletes it.
- Home's Queue row is the app's own row (the server has no Home row for queues), placed first.

## Logging

- Queue failures are warnings with the operation and exception type (e.g. "adding to the queue
  failed: IOException").
- A refused edit that was reloaded is logged at info. Starting a queue, with its entry count, and
  shuffling are logged at debug. A capabilities failure is logged at debug and asked again later.
- No ids, tokens or URLs are logged beyond what ArcApi already logs (route names only).

## Limits

- Queue entries can't be removed or reordered from the TV yet; the web can. Clearing happens by
  starting something new or playing to the end.
- Collections and playlists don't have Play All / Shuffle on the TV yet.
- Checked with unit tests against a stand-in server (`PlayQueuesTest`), screenshots, and the real
  server's answers from a scratch Arc via curl. Not yet on a TV or emulator.

## Library header (owner, 30 October)

- The library's name and tabs no longer move when switching tabs. The Library tab's Sort and
  Filters buttons are taller than the tabs, and the header row was centring on whichever was
  tallest. The row now has a fixed 48dp height.
- The titles tab is called **Library** (Recommended, Library, Collections), as in Plex, instead of
  Movies / Shows / Videos, which repeated the library's own name. The web still says Movies /
  Shows; that's logged on arc-media-server#80.

## Fixes from the first real-TV test (owner, 31 October)

- **Optimised builds.** Release builds go through R8 (19 MB instead of 35 MB). Debug builds
  lagged badly on a Chromecast with Google TV.
- **HDR, Dolby Vision and passthrough are reported to Arc.**
  - `hdr_formats` comes from the display's HDR types; Dolby Vision also needs a decoder.
  - `dolby_vision_profiles` comes from the decoder's profile flags.
  - `audio_passthrough` lists TrueHD and DTS by passthrough, which work only when the file plays
    as it is. Dolby Digital (Plus) by passthrough still counts as playable in converted streams.
  - A display that reports nothing is sent as unknown, not SDR only, so HDR films aren't converted
    needlessly.
- **Subtitles use the profile's style** (colour, box/solid/none, size), as on the web. Before,
  they used Android's caption settings, which default to a solid black box. ASS/SSA drawn by
  libass keep their own styling.
  - The check that sends Arc's ASS script to libass now tolerates the id prefix Media3 can add,
    and logs which path a subtitle took.
  - Top position isn't supported yet.
- **Title page buttons:** Play from Beginning and Shuffle are filled like the round buttons, not
  outlined.
- **Episode row:** the focused episode's description has its space kept, so the cast below
  doesn't jump.
- **Coming back to Home** returns focus to the row last used (or the first one), so the sidebar
  no longer opens.
