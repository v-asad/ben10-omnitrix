#!/usr/bin/env python3
"""Find Alien Force / Ultimate Alien-era artwork for the roster on the Ben 10 Fandom wiki.

For each alien it reads the wiki's gallery page, keeps PNGs whose names point at the
Alien Force / Ultimate Alien era (one shared art style) and away from Omniverse, reboot,
toys, games and line art, checks a small preview for a transparent background, then
downloads the best candidates into art/candidates/<slug>/ and renders numbered contact
sheets for review. Official art is copyrighted: art/ is gitignored, never commit it.

Usage:  python3 tools/fetch_alien_art.py [--per 2] [--only slug,slug]
"""
import argparse
import io
import json
import re
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "art/candidates"
API = "https://ben10.fandom.com/api.php"
# The image CDN only serves requests coming from the wiki (hotlink protection),
# the same request a browser makes when you view the page.
UA = {"User-Agent": "Mozilla/5.0 (omnitrix-watch-fan-project; personal use)", "Referer": "https://ben10.fandom.com/"}

# (slug, display name, era). Original-series aliens use their Ultimate Alien-era designs.
ROSTER = [
    # Original series
    ("heatblast", "Heatblast", "os"), ("wildmutt", "Wildmutt", "os"), ("diamondhead", "Diamondhead", "os"),
    ("xlr8", "XLR8", "os"), ("grey-matter", "Grey Matter", "os"), ("four-arms", "Four Arms", "os"),
    ("stinkfly", "Stinkfly", "os"), ("ripjaws", "Ripjaws", "os"), ("upgrade", "Upgrade", "os"),
    ("ghostfreak", "Ghostfreak", "os"), ("cannonbolt", "Cannonbolt", "os"), ("wildvine", "Wildvine", "os"),
    ("blitzwolfer", "Blitzwolfer", "os"), ("snare-oh", "Snare-oh", "os"), ("frankenstrike", "Frankenstrike", "os"),
    ("upchuck", "Upchuck", "os"), ("ditto", "Ditto", "os"), ("eye-guy", "Eye Guy", "os"),
    ("way-big", "Way Big", "os"), ("spitter", "Spitter", "os"), ("buzzshock", "Buzzshock", "os"),
    ("arctiguana", "Arctiguana", "os"),
    # Alien Force
    ("swampfire", "Swampfire", "af"), ("echo-echo", "Echo Echo", "af"), ("humungousaur", "Humungousaur", "af"),
    ("jetray", "Jetray", "af"), ("big-chill", "Big Chill", "af"), ("chromastone", "Chromastone", "af"),
    ("brainstorm", "Brainstorm", "af"), ("spidermonkey", "Spidermonkey", "af"), ("goop", "Goop", "af"),
    ("alien-x", "Alien X", "af"), ("lodestar", "Lodestar", "af"), ("rath", "Rath", "af"),
    ("nanomech", "Nanomech", "af"),
    # Ultimate Alien
    ("water-hazard", "Water Hazard", "ua"), ("ampfibian", "AmpFibian", "ua"), ("armodrillo", "Armodrillo", "ua"),
    ("terraspin", "Terraspin", "ua"), ("nrg", "NRG", "ua"), ("fasttrack", "Fasttrack", "ua"),
    ("chamalien", "ChamAlien", "ua"), ("clockwork", "Clockwork", "ua"), ("jury-rigg", "Jury Rigg", "ua"),
    ("eatle", "Eatle", "ua"),
    # Ultimate forms
    ("ultimate-humungousaur", "Ultimate Humungousaur", "ult"), ("ultimate-swampfire", "Ultimate Swampfire", "ult"),
    ("ultimate-big-chill", "Ultimate Big Chill", "ult"), ("ultimate-echo-echo", "Ultimate Echo Echo", "ult"),
    ("ultimate-spidermonkey", "Ultimate Spidermonkey", "ult"),
]

ERA_HINT = re.compile(r"(^|[\s_(-])(af|ua|uaf|alien force|ultimate alien)([\s_).-]|$)", re.I)
OFFICIAL = re.compile(r"official|artwork|render|character|full ?body|pose", re.I)
REJECT = re.compile(
    r"(^|[\s_(-])(ov|omniverse|reboot|rb|os|negative|nega|toy|toys|figure|keychain|box|packag\w*|game|ds|wii|psp|"
    r"line ?art|lineart|sketch|concept|model ?sheet|sheet|turnaround|card|poster|promo|dvd|comic|lego|youtooz|"
    r"mcdonald\w*|screenshot|screencap|episode|vs|and|with|fan|drawing|storyboard|logo|icon|sprite|alpha|"
    r"albedo|ultimatrix|gwen|kevin|ben|vilgax|grandpa|azmuth|eunice|team|group)([\s_).,-]|$)"
    r"|,|&|\.(gif|jpe?g|svg|webm|ogv|ogg)$",
    re.I,
)


def api(**params):
    params.update(format="json")
    req = urllib.request.Request(f"{API}?{urllib.parse.urlencode(params)}", headers=UA)
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)


def fetch(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=40) as r:
        return r.read()


def page_files(title):
    data = api(action="query", titles=title, prop="images", imlimit=500, redirects=1)["query"]["pages"]
    return [img["title"] for page in data.values() for img in page.get("images", [])]


def gallery_files(name):
    """File titles from the alien's gallery, topped up with a file-library search."""
    files = []
    for title in (f"{name} (Classic)/Gallery", f"{name}/Gallery", f"{name} (Classic)", name):
        files = page_files(title)
        if len(files) > 3:
            break
    for query in (name, f"{name} UA", f"{name} official"):
        hits = api(action="query", list="search", srnamespace=6, srlimit=40, srsearch=query)["query"]["search"]
        files += [h["title"] for h in hits if h["title"] not in files]
    return files


def transparent(img):
    w, h = img.size
    return all(img.getpixel(p)[3] < 16 for p in [(0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1), (w // 2, 0)])


def candidates(name, era, per):
    stem = name.lower().replace("ultimate ", "")
    wanted_ultimate = era == "ult"
    titles = []
    for t in gallery_files(name):
        clean = t[5:].replace("_", " ").lower()
        if not clean.endswith(".png") or REJECT.search(clean):
            continue
        if stem.replace("-", " ").split()[0] not in clean.replace("-", " "):
            continue
        if ("ultimate" in clean) != wanted_ultimate:
            continue
        titles.append(t)
    if not titles:
        return []
    ranked = []
    for i in range(0, len(titles), 50):
        pages = api(action="query", titles="|".join(titles[i:i + 50]), prop="imageinfo",
                    iiprop="url|size|mime", iiurlwidth=200)["query"]["pages"]
        for p in pages.values():
            ii = (p.get("imageinfo") or [{}])[0]
            if ii.get("mime") != "image/png" or min(ii.get("width", 0), ii.get("height", 0)) < 220:
                continue
            score = (4 if OFFICIAL.search(p["title"]) else 0) + (3 if ERA_HINT.search(p["title"][5:]) else 0) \
                + min(ii["width"] * ii["height"], 1_500_000) / 500_000
            ranked.append((score, p["title"][5:], ii["url"], ii.get("thumburl", ii["url"])))
    ranked.sort(reverse=True)
    picks = []
    for _, title, url, thumb in ranked:
        if len(picks) == per:
            break
        try:
            if not transparent(Image.open(io.BytesIO(fetch(thumb))).convert("RGBA")):
                continue
            picks.append((title, Image.open(io.BytesIO(fetch(url))).convert("RGBA")))
        except Exception:  # noqa: BLE001 - skip unreadable files
            continue
    return picks


def contact_sheets(results, per):
    cell, label_h, cols = 170, 28, 2          # two aliens per row, `per` candidates each
    rows_per_sheet = 9
    alien_w = 30 + per * cell
    for n, start in enumerate(range(0, len(results), cols * rows_per_sheet), 1):
        chunk = results[start:start + cols * rows_per_sheet]
        rows = (len(chunk) + cols - 1) // cols
        sheet = Image.new("RGBA", (cols * alien_w + 20, rows * (cell + label_h + 18)), (14, 18, 16, 255))
        draw = ImageDraw.Draw(sheet)
        for i, (index, slug, picks) in enumerate(chunk):
            x0, y0 = (i % cols) * alien_w + 10, (i // cols) * (cell + label_h + 18)
            draw.text((x0, y0 + 2), f"{index:02d}  {slug.upper()}", fill=(150, 255, 110, 255))
            for c in range(per):
                x, y = x0 + c * cell, y0 + 16
                for cx in range(0, cell - 6, 16):
                    for cy in range(0, cell - 6, 16):
                        if (cx // 16 + cy // 16) % 2:
                            draw.rectangle([x + cx, y + cy, x + cx + 15, y + cy + 15], fill=(26, 32, 28, 255))
                if c < len(picks):
                    title, img = picks[c]
                    thumb = img.copy()
                    thumb.thumbnail((cell - 14, cell - 14))
                    sheet.alpha_composite(thumb, (x + (cell - 6 - thumb.width) // 2, y + (cell - 6 - thumb.height) // 2))
                    draw.text((x + 2, y + cell - 2), f"{index:02d}{chr(97 + c)}  {title[:22]}", fill=(190, 220, 180, 255))
                elif c == 0:
                    draw.text((x + 10, y + cell // 2), "no clean AF/UA art found", fill=(255, 120, 100, 255))
        path = OUT / f"_sheet_{n}.png"
        sheet.convert("RGB").save(path)
        print(f"contact sheet → {path.relative_to(ROOT)}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--per", type=int, default=2)
    parser.add_argument("--only", default="")
    args = parser.parse_args()
    only = set(filter(None, args.only.split(",")))
    roster = [(i + 1, *entry) for i, entry in enumerate(ROSTER) if not only or entry[0] in only]

    def work(item):
        index, slug, name, era = item
        return index, slug, candidates(name, era, args.per)

    with ThreadPoolExecutor(max_workers=6) as pool:
        results = list(pool.map(work, roster))
    for index, slug, picks in results:
        folder = OUT / slug
        folder.mkdir(parents=True, exist_ok=True)
        for old in folder.glob("*.png"):
            old.unlink()
        for c, (title, img) in enumerate(picks):
            img.save(folder / f"{chr(97 + c)}.png")
            (folder / f"{chr(97 + c)}.txt").write_text(title)
        print(f"  {index:02d} {slug:22s} " + ("  ".join(f"{chr(97 + c)}={t}" for c, (t, _) in enumerate(picks)) or "— none"))
    contact_sheets(results, args.per)


if __name__ == "__main__":
    main()
