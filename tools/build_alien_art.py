#!/usr/bin/env python3
"""Turn approved alien artwork into app and watch-face assets (fast, Pillow only).

Reads data/aliens.json and art/aliens/<slug>.png (gitignored, personal use). Writes
generated, gitignored assets:

  app       android/app/src/generated/assets/aliens/{holo,glow,icon}/<slug>.png
              holo  256 px hologram plate: contrast-boosted luminance, alpha kept
                    (the app only multiplies it by the theme colour: cheap on the GPU)
              glow  256 px pre-blurred white silhouette (no runtime blur)
              icon   72 px white silhouette for the bezel carousel
  face      android/watchface/src/generated/res/drawable-nodpi/alien_<n>.png
              52 px white silhouette, tinted on the face

Aliens without artwork get an original Omnitrix-hourglass fallback, so a clean checkout
still builds a complete app and face.

Usage:  python3 tools/build_alien_art.py
"""
import json
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageOps

ROOT = Path(__file__).resolve().parent.parent
ROSTER = json.loads((ROOT / "data/aliens.json").read_text())["aliens"]
ART = ROOT / "art/aliens"
APP_OUT = ROOT / "android/app/src/generated/assets/aliens"
FACE_OUT = ROOT / "android/watchface/src/generated/res/drawable-nodpi"

HOLO, ICON, FACE = 256, 72, 52


def fallback(size=512):
    """Original hourglass emblem used when no artwork is available."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c, r = size / 2, size * 0.42
    d.ellipse([c - r, c - r, c + r, c + r], fill=(255, 255, 255, 255))
    w = r * 0.08
    for side in (-1, 1):
        d.polygon([(c + side * w, c), (c + side * r * 0.55, c - r * 0.84), (c + side * r * 1.2, c - r),
                   (c + side * r * 1.2, c + r), (c + side * r * 0.55, c + r * 0.84)], fill=(0, 0, 0, 0))
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse([c - r, c - r, c + r, c + r], fill=255)
    img.putalpha(Image.composite(img.getchannel("A"), Image.new("L", (size, size), 0), mask))
    return img


def fit(img, size, pad=0.06):
    """Trim to content and centre in a transparent square."""
    img = img.crop(img.getbbox() or (0, 0, *img.size))
    inner = int(size * (1 - 2 * pad))
    img.thumbnail((inner, inner), Image.LANCZOS)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.alpha_composite(img, ((size - img.width) // 2, (size - img.height) // 2))
    return out


def hologram_plate(img, contrast=1.9, lift=-48):
    """Greyscale luminance with boosted contrast: dark line work stays dark, fills glow."""
    lum = ImageOps.grayscale(img.convert("RGB")).point(lambda v: max(0, min(255, int(v * contrast + lift))))
    plate = Image.merge("RGBA", (lum, lum, lum, img.getchannel("A")))
    return plate


def silhouette(img):
    white = Image.new("RGBA", img.size, (255, 255, 255, 0))
    white.putalpha(img.getchannel("A"))
    return white


def main():
    for sub in ("holo", "glow", "icon"):
        (APP_OUT / sub).mkdir(parents=True, exist_ok=True)
    FACE_OUT.mkdir(parents=True, exist_ok=True)
    missing = []
    for n, alien in enumerate(ROSTER):
        slug = alien["slug"]
        src = ART / f"{slug}.png"
        if src.exists():
            art = Image.open(src).convert("RGBA")
        else:
            art, _ = fallback(), missing.append(slug)

        holo = fit(art, HOLO)
        hologram_plate(holo).save(APP_OUT / "holo" / f"{slug}.png", optimize=True)
        glow = silhouette(holo).filter(ImageFilter.GaussianBlur(9))
        glow.save(APP_OUT / "glow" / f"{slug}.png", optimize=True)
        silhouette(fit(art, ICON, pad=0.04)).save(APP_OUT / "icon" / f"{slug}.png", optimize=True)
        silhouette(fit(art, FACE, pad=0.02)).save(FACE_OUT / f"alien_{n}.png", optimize=True)

    print(f"alien art: {len(ROSTER) - len(missing)}/{len(ROSTER)} from artwork"
          + (f", fallback for: {', '.join(missing)}" if missing else ""))


if __name__ == "__main__":
    main()
