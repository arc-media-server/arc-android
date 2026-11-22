# Watch Together leaves the sidebar

## What changed

- The sidebar no longer has a Watch Together entry or page.
- With Watch Together on, choosing the profile at the top of the sidebar opens a small menu:
  Switch Profile and Join Watch Together. Without it, the profile still switches at once.
- Join Watch Together opens a dialog with the code box. A code that works closes it and opens
  the player on the room's lobby, as before.
- Rooms are still started from a card's or title's menu, and shown on Home's Watch Together row.

## Why

Only this app and LG/Samsung had Watch Together in the sidebar. The web keeps Join Watch Together
in its profile menu, and Apple TV, Roku and Kodi show rooms on Home and start them from a title.
The sidebar stays a list of places to browse (owner, 22 November).

## Decisions

- **Join Watch Together under the profile**, as the web's profile menu. The cost is one extra
  press for Switch Profile while Watch Together is on. The alternative, a sidebar entry for
  joining only, would put it back in the list of places.

## Checks

- New screenshots `homeSidebarTogether` and `profileMenuTogether` (`2026-11-22-together-out-of-sidebar/`).
- `./gradlew assembleDebug testDebugUnitTest recordRoborazziDebug`: build and screenshots pass;
  the 3 `SeasonTabsFocusTest` failures also fail on main on this machine.
- Not tried on a real TV, or against a server with rooms.

## Logging

None added: joining already logs its failures.
