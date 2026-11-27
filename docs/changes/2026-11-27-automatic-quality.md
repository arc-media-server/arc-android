# Automatic quality that adapts to the connection

## What changed

- **Automatic now adapts, as the web player's does.** Before, Automatic simply played the
  original within Arc's away-from-home limit, and a slow connection meant buffering until you
  picked a lower quality yourself. Now:
  - it still starts at the original (within Arc's limit);
  - when the buffer stays under 2 seconds and the player keeps waiting (or each piece of the
    stream arrives slower than it plays) for 6 seconds, it asks Arc for the stream again one or
    more steps lower, picking a quality the measured delivery can sustain with room to spare;
  - after 45 seconds healthy (25 s buffered, the stream arriving 1.8 times faster than the next
    quality up needs, measured at least twice in the last 20 s) it steps back up.
- **The rules are the web player's,** ported case for case
  (arc-media-server `web/src/lib/adaptive-quality.ts`, `docs/ADAPTIVE-QUALITY.md`): a 12 s grace
  after starting, seeking or changing; 20 s between steps down and 60 s between steps up; at most
  four changes in two minutes; nothing in the last 10 seconds; never above Arc's own limit.
- **What's measured:** each downloaded piece of a converted (HLS) stream, through Media3's
  `onLoadCompleted`: its size, the time it took (Arc's conversion wait included) and how much
  video it holds. A file played as it is has no pieces to measure, so there Automatic can only
  step down, when the player keeps waiting.
- **Not while** paused, seeking, loading, failed, stopped, at the end, or in a Watch Together
  room (a replan there would hold everyone up).
- **The quality menu:** once Automatic has stepped down, its row says what it's playing at now
  ("Now 4 Mbps 720p"), and the menu explains what Automatic does. Choosing a quality yourself
  stops adapting; choosing Automatic again starts from the original. Nothing is saved: Automatic
  is already the default each time.

## Decisions

- **Replan, not a quality ladder.** Each change asks Arc for the stream again at the new limit
  (the conversion it replaces is stopped), so it briefly rebuffers. That is what the server and
  the web player do: Arc serves one rendition per conversion. Plex does the same kind of
  stepping on its apps ("Automatically adjust quality"); a multi-rendition HLS ladder would be a
  server change.
- **The same numbers as the web player,** so both behave alike and one set of tests describes
  both. They can be tuned together later with evidence from real remote connections.

## Checks

- `./gradlew assembleDebug testDebugUnitTest recordRoborazziDebug`: see the PR for results.
- New `AdaptiveQualityTest`: the web player's six policy tests, case for case (grace and
  cooldowns, measured delivery and the safe floor, stepping up, what restarts the grace, Arc's
  own limit and playback speed, the four-changes budget).
- **Not tried on a TV or a real slow connection.** Whether it steps down in time on a real
  remote connection, and how long each change rebuffers, still need a real device away from
  home.

## Logging

- Each change at info, at most four in two minutes: "automatic quality: lowering to 4000 kbit/s
  (buffer 0.4 s, delivery 2900 kbit/s)". No ids, addresses or URLs.
- The new stream's plan is logged as before ("Arc is converting: ...").
