#!/usr/bin/env python3
"""Downloads the artwork the screenshot tests show, from TMDB, into
app/src/test/resources/artwork/<kind>/<slug>.{jpg,png} (ignored by git: the artwork belongs to
its owners and isn't ours to redistribute). The titles match Fixtures.kt.

    TMDB_API_KEY=<your v3 key> python3 tools/fetch-screenshot-art.py

Then ./gradlew recordRoborazziDebug. Without the artwork, the screenshots show drawn stand-ins.
"""
import json, os, re, sys, urllib.parse, urllib.request
from pathlib import Path

KEY = os.environ.get("TMDB_API_KEY") or os.environ.get("AMS_TMDB_API_KEY")
OUT = Path(__file__).resolve().parent.parent / "app/src/test/resources/artwork"

MOVIES = {  # title as in Fixtures.kt -> TMDB movie id
    "Dune: Part Two": 693134, "Oppenheimer": 872585, "Everything Everywhere All at Once": 545611,
    "Interstellar": 157336, "Spider-Man: Across the Spider-Verse": 569094, "Blade Runner 2049": 335984,
    "The Grand Budapest Hotel": 120467, "Mad Max: Fury Road": 76341, "Arrival": 329865, "Parasite": 496243,
}
SHOW = ("Severance", 95396, [1, 2])
COLLECTIONS = {"Dune Collection": 726871, "Spider-Man: Spider-Verse Collection": 573693}
PEOPLE_FROM = 693134  # cast and crew photos come from this film's credits
EXTRA_PEOPLE = ["Denis Villeneuve", "Jon Spaihts"]


def slug(title):
    return re.sub(r"[^a-z0-9]+", "-", title.lower()).strip("-")


def api(path, **q):
    q["api_key"] = KEY
    url = "https://api.themoviedb.org/3" + path + "?" + urllib.parse.urlencode(q)
    with urllib.request.urlopen(url, timeout=30) as r:
        return json.load(r)


def save(kind, title, path, size):
    if not path:
        print(f"  no {kind} for {title}")
        return
    ext = ".png" if path.endswith(".png") else ".jpg"
    dest = OUT / kind / (slug(title) + ext)
    dest.parent.mkdir(parents=True, exist_ok=True)
    for old in dest.parent.glob(slug(title) + ".*"):
        old.unlink()
    with urllib.request.urlopen(f"https://image.tmdb.org/t/p/{size}{path}", timeout=60) as r:
        dest.write_bytes(r.read())


def logo(images):
    logos = [l for l in images.get("logos", []) if l["iso_639_1"] in ("en", None)]
    return logos[0]["file_path"] if logos else None


def main():
    if not KEY:
        sys.exit("Set TMDB_API_KEY to a TMDB v3 API key.")
    for title, mid in MOVIES.items():
        m = api(f"/movie/{mid}", append_to_response="images", include_image_language="en,null")
        save("poster", title, m["poster_path"], "w500")
        save("backdrop", title, m["backdrop_path"], "w1280")
        save("logo", title, logo(m["images"]), "w500")
        print("film:", title)
    name, tid, seasons = SHOW
    t = api(f"/tv/{tid}", append_to_response="images", include_image_language="en,null")
    save("poster", name, t["poster_path"], "w500")
    save("backdrop", name, t["backdrop_path"], "w1280")
    save("logo", name, logo(t["images"]), "w500")
    for n in seasons:
        s = api(f"/tv/{tid}/season/{n}")
        save("poster", f"{name} Season {n}", s["poster_path"], "w500")
        for e in s["episodes"]:
            save("still", e["name"], e["still_path"], "w780")
    print("show:", name)
    for title, cid in COLLECTIONS.items():
        save("poster", title, api(f"/collection/{cid}")["poster_path"], "w500")
        print("collection:", title)
    credits = api(f"/movie/{PEOPLE_FROM}/credits")
    people = {p["name"]: p["profile_path"] for p in credits["cast"][:8] + credits["crew"]}
    for person in [p["name"] for p in credits["cast"][:8]] + EXTRA_PEOPLE:
        save("person", person, people.get(person), "w342")
    print("people:", len(credits["cast"][:8]) + len(EXTRA_PEOPLE))
    print("Saved to", OUT)


if __name__ == "__main__":
    main()
