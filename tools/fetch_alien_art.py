#!/usr/bin/env python3
"""Fetch candidate alien artwork from the Ben 10 Fandom wiki for personal use.

Downloads each alien's page image into art/candidates/ (gitignored: official art is
copyrighted and must never be committed) and writes a contact sheet for review.
Nothing is used by the app until approved and copied to art/aliens/.

Usage:  python3 tools/fetch_alien_art.py
"""
import io
import json
import urllib.parse
import urllib.request
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "art/candidates"
API = "https://ben10.fandom.com/api.php"
# The image CDN only serves requests coming from the wiki (hotlink protection),
# the same request a browser makes when you view the page.
UA = {"User-Agent": "Mozilla/5.0 (omnitrix-watch-fan-project; personal use)", "Referer": "https://ben10.fandom.com/"}

# Carousel order (design/omnitrix.js). Classic = original series pages; "a|b" = fallback titles.
ALIENS = [
    ("heatblast", "Heatblast (Classic)"), ("xlr8", "XLR8 (Classic)"), ("four-arms", "Four Arms (Classic)"),
    ("grey-matter", "Grey Matter (Classic)"), ("diamondhead", "Diamondhead (Classic)"), ("ripjaws", "Ripjaws (Classic)"),
    ("upgrade", "Upgrade (Classic)"), ("ghostfreak", "Ghostfreak"), ("stinkfly", "Stinkfly (Classic)"),
    ("wildmutt", "Wildmutt (Classic)|Wildmutt"), ("cannonbolt", "Cannonbolt (Classic)"), ("echo-echo", "Echo Echo (Classic)|Echo Echo"),
    ("clockwork", "Clockwork (Classic)|Clockwork"),
]


def get(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=30) as r:
        return r.read()


def page_images(titles):
    query = urllib.parse.urlencode({
        "action": "query", "titles": "|".join(titles), "prop": "pageimages",
        "pithumbsize": 800, "redirects": 1, "format": "json",
    })
    data = json.loads(get(f"{API}?{query}"))["query"]
    # map requested title -> final page title through normalisation and redirects
    alias = {}
    for step in ("normalized", "redirects"):
        for entry in data.get(step, []):
            alias[entry["from"]] = entry["to"]
    by_title = {p["title"]: p for p in data["pages"].values()}
    result = {}
    for t in titles:
        final = t
        while final in alias:
            final = alias[final]
        page = by_title.get(final, {})
        result[t] = page.get("thumbnail", {}).get("source")
    return result


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    urls = page_images([t for _, titles in ALIENS for t in titles.split("|")])
    tiles = []
    for slug, titles in ALIENS:
        title = titles.split("|")[0]
        url = next((urls[t] for t in titles.split("|") if urls.get(t)), None)
        if not url:
            print(f"  ✗ {slug:12s} no page image for '{title}'")
            tiles.append((slug, None))
            continue
        img = Image.open(io.BytesIO(get(url))).convert("RGBA")
        img.save(OUT / f"{slug}.png")
        print(f"  ✓ {slug:12s} {img.width}x{img.height}  {url.split('/images/')[1].split('/revision')[0]}")
        tiles.append((slug, img))

    # contact sheet: 5 per row on a dark checker so transparency is visible
    cell, cols = 220, 5
    rows = (len(tiles) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cell, rows * (cell + 24)), (18, 22, 20, 255))
    draw = ImageDraw.Draw(sheet)
    for i, (slug, img) in enumerate(tiles):
        x, y = (i % cols) * cell, (i // cols) * (cell + 24)
        for cx in range(0, cell, 20):
            for cy in range(0, cell, 20):
                if (cx // 20 + cy // 20) % 2:
                    draw.rectangle([x + cx, y + cy, x + cx + 19, y + cy + 19], fill=(28, 34, 30, 255))
        if img:
            thumb = img.copy()
            thumb.thumbnail((cell - 16, cell - 16))
            sheet.alpha_composite(thumb, (x + (cell - thumb.width) // 2, y + (cell - thumb.height) // 2))
        draw.text((x + 8, y + cell + 4), f"{i + 1:02d} {slug.upper()}", fill=(150, 255, 110, 255))
    sheet.convert("RGB").save(OUT / "_contact_sheet.png")
    print(f"contact sheet → {(OUT / '_contact_sheet.png').relative_to(ROOT)}")


if __name__ == "__main__":
    main()
