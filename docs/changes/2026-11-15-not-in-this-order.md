# Not in This Order: episodes outside a show's episode order

A show's episode order (TheTVDB aired, DVD, a TMDB group…) can leave out episodes you have on
disk. Arc lists them as `unplaced` in `GET /api/v1/series/{id}/seasons`, and the web shows them in
a last tab so nothing goes missing (`web/src/pages/Series.tsx`). The TV ignored them, so those
episodes simply weren't on the show's page. It now shows them, as the web does (and as arc-roku#12
and arc-tv#16).

## What changed

**The show's page:**
- **Tab:** a last tab, "Not in This Order", after the seasons, only when there are any.
- **Note:** under the tabs, the web's words: "You have these episodes, but *TheTVDB Aired Order*
  doesn't include them. They're still here so nothing goes missing." Without the order's name, it
  says "this episode order".
- **Cards:** the episodes show without an "EPISODE n" line. They play like any other.
- **Opening the page:** an episode opened from a row (Continue Watching, Recently Added, search)
  that's outside the order opens the page on this tab, on that episode.

**Play, Resume, Shuffle and Up Next follow the order:**
- The show's Play button, Resume and Shuffle only use the seasons, as before.
- Up Next and Auto-Play use Arc's `GET /items/{id}/next`, which follows the order.
- An episode outside the order is played only when chosen.

**The player's Episodes tab:**
- **Which list:** it now finds the season that holds the playing episode, not the season number
  in its file name. Playing an episode outside the order, it lists the episodes outside the order,
  without numbers.
- **Code:** the rule is `episodesAround` in `data/EpisodeOrder.kt`.

**No "S0 · E0":** Arc numbers an episode outside the order season 0, episode 0, so nothing shows
that number. `Item.orderNumbers` is null for 0 and 0; specials (season 0, episode 1 on) keep
theirs. It covers:
- card captions (`episodeCode`), the player's title line and its Info card;
- the episode tiles, the title page's Play label;
- Google TV's Continue watching row, which gets no season or episode for them.

## Decisions

- **Same tab and words as the web.** The web colours the tab in its warning colour; the TV has no
  UI colour (owner, 1 November), so the tab looks like the others and the note explains it.

## Limits

- **No Missing Episodes:** the web's "show missing episodes" (`missing=1`) isn't on the TV.
- **Not on a TV yet:** not tried on a TV or emulator.

## Checks

See the PR. New tests:
- `EpisodeOrderTest`: which list the player shows; the tab a page opens on; Play staying in the
  order; no number for 0 and 0, while specials keep theirs.
- `ArcApiTest`: `unplaced` read, and `null` read as none.
- Screenshot `tv-show-not-in-order`.

## Logging

None added. The seasons request is the existing one, whose failures are already logged (page
failure as a warning; the player's list at debug).
