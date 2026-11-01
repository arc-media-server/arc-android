# Folders view for home videos

**What changed.** A home videos (Other videos) library has a Folders tab after Library, as the
web's VideoFolders: the folders in the current one, each with a video's picture, a "Folder" tag
and how many videos it holds, then its own videos, with the way down shown above ("Home Videos ›
Holidays 2024"). Choosing a folder goes in with the focus on its first card; Back goes up a
folder and lands on the one it came out of, then leaves as usual. The TV web app (arc-tv#9) has
the same.

**API.** `GET /libraries/{id}/folders?path=` (`ArcApi.libraryFolders`). Its fields become part of
the v1 contract in arc-media-server#327 (the JSON is unchanged, so older servers work too).

**Decisions.** A tab rather than replacing the Library grid, where the web puts it. Cards gained
an optional corner tag (`MediaCard.tag`) and the Folder icon.

**Limits.** The folder you're in is forgotten when the library is opened again.

**Logging.** A warning when a folder fails to load (library id and error type, never the path).

**Tests.** Screenshot `tv-library-folders`; ArcApiTest checks the request paths and parsing.
