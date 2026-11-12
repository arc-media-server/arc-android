# Home: the hero shows the focused card; Recently Added card keys

**Hero.** Home's hero was a rotating carousel of featured titles (the first two of each row,
up to six) with its own Play and More Info buttons, every nine seconds, separate from the rows.
The owner's intent, and the Apple TV app's Home, is that the hero describes the card in focus:
it now shows the focused card's logo or name, episode and details and overview over its artwork,
with Continue Watching (the first row) just under it. Focus starts on that row's first card, and
moving along a row changes the hero. The hero has no buttons: Select on a card opens its title (a
play queue card plays on) and holding Select gives its actions, as before. Coming back from a
title returns to the row last used. The carousel, its dots and the "View"/"More Info" strings are
gone.

**Recently Added keys.** The server now groups Recently Added into 24-hour batches per show
(arc-media-server#446), so the same show or season can appear twice in the row. Card keys were
type, show and season only; Compose's LazyRow requires unique keys and would crash Home. Cards
now read `added_at` and include it in their key (0 from older servers, as before).

Checks: `./gradlew assembleDebug testDebugUnitTest recordRoborazziDebug` (new HomeRowsTest
case for two batches of one season). No logging change.
