# Real titles and artwork in the screenshots

**What changed.** The screenshot fixtures (`app/src/test/.../screenshots/Fixtures.kt`) now show a
real library instead of made-up titles with gradient artwork: Dune: Part Two, Oppenheimer,
Everything Everywhere All at Once, Interstellar, Spider-Man: Across the Spider-Verse, Blade
Runner 2049, The Grand Budapest Hotel, Mad Max: Fury Road, Arrival and Parasite, and Severance as
the show (its real episode titles and running times). Titles, years, certificates, running times
and the Dune: Part Two cast and crew are real; the synopses are our own short ones. The admin
profile is "Alex". The README pictures in `docs/screenshots/` were re-rendered from them.

**Artwork stays out of git.** `tools/fetch-screenshot-art.py` downloads the posters, backdrops,
logos, episode stills, collection posters and cast photos from TMDB into
`app/src/test/resources/artwork/<kind>/<slug>.{png,jpg}`, which `.gitignore` excludes: the
artwork belongs to the studios and isn't ours to redistribute. It needs a TMDB v3 key in
`TMDB_API_KEY` (or `AMS_TMDB_API_KEY`), never stored in the repository. Without the files,
`Fixtures.art()` draws the old stand-ins, so the tests run anywhere. The README carries TMDB's
attribution line.

**Why.** The owner wanted the screenshots to look like a real library (16 November). The
alternative considered was public-domain films and the Blender open movies, whose artwork could
have been committed; real, recognisable titles were preferred, and keeping the files out of git
avoids redistributing them.

**Logging.** None: test fixtures and a developer script only. The script prints which titles it
fetched and never prints the key or request URLs.

## Title page and Continue Watching (owner, 16 November)

- **Title page:** the description, details and badges now sit under the logo and above the Play
  button and round buttons, instead of beside them (the 31 October layout). The description is
  capped at 600 dp wide so it reads as a block rather than one long line. MORE still opens the
  whole text, and Up from the buttons reaches it when it's cut short.
- **Screenshots:** Home's Continue Watching row uses the Backdrops card style (the title's
  background art with its logo), which the app already offers as a row style.
