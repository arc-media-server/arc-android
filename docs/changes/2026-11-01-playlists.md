# Playlists

**What changed.** The sidebar has a Playlists entry after the libraries. It lists the profile's
playlists in the library grid, each card a two-by-two mosaic of its titles' posters (as the web's
PlaylistCover; one poster when it has fewer than four) with its title count. A playlist's page
shows its name, count and length, Play and Shuffle, and its titles in order; a title opens its
page. The TV web app (arc-tv) has the same.

**How it plays.** Play starts a play queue from the playlist (`POST /play-queues {playlist_id}`,
which Arc snapshots). Shuffle starts one from the titles in a random order and then shuffles what
follows the first. The collection page's play logic moved to `PlayQueues.playAll`, and its page
layout to a shared `TitlesPage`, which both pages use. The buttons show only when the server has
play queues.

**Decisions.** Playlists are made and edited on the web; the TV only shows and plays them, as
Plex's TV apps. The entry is always in the sidebar, with a note pointing to the web when there are
none.

**Limits.** No editing playlists on the TV. The list loads when the section opens (not again on
coming back from a playlist).

**Logging.** A warning when the playlists or a playlist fail to load, or when playing one can't
start (with whether it was shuffled); the queue's start stays at debug, as before.

**Tests.** Screenshots `tv-playlists` and `tv-playlist-page`; PlayQueuesTest checks Play sends
`playlist_id` and doesn't shuffle.
