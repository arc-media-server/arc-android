# Play Version… on title pages (1 November)

From the to-do list (arc-android#2). A movie or episode page with more than one version on disk
now has **Play Version…** in its ⋯ menu, as Plex does (the TV web app has the same in arc-tv#4).
It lists each version by edition (else resolution) with its HDR, codec and size; choosing one
plays it from where the title was left.

- `PlayerDest.fileId` carries the choice; the player asks for that file on its first
  `POST /api/v1/items/{id}/playback` (the `file_id` its Version menu already sends).
- Show pages don't offer it (versions are per episode; the episode's page does). Missing files
  and later parts of multi-part files aren't listed (`versionsOf`).

Tests: screenshots `tv-movie-more-menu-versions` and `tv-movie-play-version`.

Logging: none added; the player's existing playback-decision log records the request.
