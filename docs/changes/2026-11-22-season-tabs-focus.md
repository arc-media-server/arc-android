# Season tabs: Down from Play lands on the season shown again

## What changed

#56 rebuilt the pills (library tabs, season tabs) as a plain row of buttons. The title page tells
the season tabs which one focus should enter on (the season shown), but that only works on a
focus group, and the new row wasn't one: Down from Play landed on whichever tab was nearest,
which switched season. The pills' row is a focus group again, so Down from Play and Up from the
episodes land on the season shown.

`SeasonTabsFocusTest` caught it; its three failures also failed the manual Build workflow.
Its scrolling test also looked for the first episode, but focus enters the episodes on the one
Play would start (S1 · E3 in the fixture); it now checks that one.

## Checks

- `SeasonTabsFocusTest`: all 8 pass.
- `./gradlew assembleDebug testDebugUnitTest recordRoborazziDebug`: everything passes, no failures.
- Not tried on a real TV.

## Logging

None needed: focus only.
