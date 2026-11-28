# Consistent Android TV shelf positions and poster sizes

On Home and Recommended, the focused shelf scrolls to the same position from
either direction. Home's first shelf keeps the hero above it. Both screens
clip scrolling cards at the start of the shelf's viewport, clear of the sidebar.
The first card keeps its original inset and has room for its focus outline.

Opening an episode from Home focuses it within the show. The show reveals
the same seasons block that Down reveals from Play, keeping the episode strip
at the same height.

The title index now sits in the Library grid's existing right gutter. The
Library and Collections tabs use the same width for their poster grids.

## Checks

- `assembleDebug`, `testDebugUnitTest`, `recordRoborazziDebug`, and
  `git diff --check` passed. 285 tests passed and 13 skipped.
- D-pad tests compared vertical focus positions on Home and Recommended
  from both directions and compared manual and linked episode entry. The
  grid test compared poster width and height with the A–Z rail visible.
- Inspected the 1080p Robolectric screenshots. No physical TV was used.

[Home horizontal scroll](2026-11-26-tv-shelf-scroll-and-posters/home-shelf-scrolled-right.jpg) ·
[Recommended horizontal scroll](2026-11-26-tv-shelf-scroll-and-posters/recommended-shelf-scrolled-right.jpg) ·
[Episode opened from a shelf](2026-11-26-tv-shelf-scroll-and-posters/show-from-episode.jpg) ·
[Library](2026-11-26-tv-shelf-scroll-and-posters/library.jpg) ·
[Collections](2026-11-26-tv-shelf-scroll-and-posters/library-collections.jpg)

## Logging

Layout and focus only. No new logs.
