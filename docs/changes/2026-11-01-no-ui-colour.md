# No UI colour: white and black instead of teal

The owner dropped a UI accent colour across Arc's apps (1 November, arc-media-server#80).
`ArcColors.Accent` and `AccentStrong` are now white with `OnAccent` (near-black) on them, so
progress bars, focus borders, the PIN and text cursors, the watched tick and unwatched count, and
the title page's on-state icons follow. The spinner is white, the player's Up Next button is a
glass one that turns white when focused, and the logo keeps its teal (`AccentGradient`, now
`LogoLight` -> `LogoStrong`, and the drawables).

Checks: `./gradlew assembleDebug` passes. Unit and screenshot tests couldn't run here: Maven
Central answered 429 (Too Many Requests) when fetching Robolectric's runtime.

Logging: none (colours only).
