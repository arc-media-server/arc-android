# Google TV's Continue watching row (Watch Next)

**What changed.** Arc's Continue Watching (with Up Next) now appears in Google TV's own "Continue
watching" row on the TV's home screen, as Plex's and other media apps' titles do. Part-way titles
show as "continue" with their progress; the next episode to start shows as "next". Choosing one
opens Arc and resumes it from where Arc has it now (after the sign-in and profile screens, when
those are needed); Back goes to Home.

**How.** `WatchNextPublisher` (androidx.tvprovider) brings the row up to date whenever the app
goes to the background, and empties it on signing out. The planning (`data/WatchNext.kt`) is
plain Kotlin and unit-tested: at most 20 titles, in Arc's order; finished titles are removed; a
title the person took out of the row themselves stays out until more of it is watched (as
Google's guidelines ask). Links are `arc://play/{id}`.

**Artwork.** Arc's pictures need the TV's sign-in, which the launcher can't send, so
`ArtworkProvider` (a read-only content provider) fetches them with the app's token, keeps up to
60 in the cache for a week, and gives the launcher the file. It only serves title and collection
picture paths (`/api/items/{id}/images/{type}`, `/api/collections/{id}/images/{type}`); any other
address is refused. It's exported because the launcher must read it; another app on the TV could
read a title's picture by its id, nothing more.

**Decisions.** The row follows the profile that was signed in when the app was last left (one
profile at a time, as Plex). The row is filled only on TV devices (leanback).

**Limits.** Not yet checked on a real Google TV or emulator: the provider calls, the artwork and
choosing a title from the row need a device (#135). The row isn't refreshed when progress changes
on another device until the app is opened and left again.

**Logging.** Info when titles are added or removed (counts and time taken), debug when only
progress changed, a warning when the launcher's provider refuses (error type only), and an info
line when a title is resumed from the home screen. Never ids, paths or URLs.
