#!/usr/bin/env python3
"""Render IndieHost's legacy launcher PNGs from the same geometry as the
adaptive icon vector (res/drawable/ic_launcher_foreground.xml).

The adaptive icon (API 26+) is XML; pre-26 launchers need bitmaps, so this
draws them at 4x and downsamples. Run from the repo root:

    python3 tools/make_icons.py
"""
from PIL import Image, ImageDraw
import os

ART = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res")
BG = (27, 40, 56)
ACCENT = (102, 192, 244)
LIGHT = (242, 247, 251)
PALE = (198, 212, 223)

DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def rounded_mask(size, radius):
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, size - 1, size - 1], radius=radius, fill=255)
    return mask


def circle_mask(size):
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, size - 1, size - 1], fill=255)
    return mask


def render(px, round_shape=False):
    scale = 4
    s = px * scale
    # Flat background, not a gradient: it matches the adaptive icon's background
    # colour exactly, and a flat field survives palette PNG compression without
    # visible banding on a 720p screen.
    img = Image.new("RGBA", (s, s), BG + (255,))

    art = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(art)
    cx, cy = 0.50 * s, 0.55 * s
    r = 0.255 * s
    stroke = 0.062 * s

    # Steam first, so the ring sits in front of it.
    pr = 0.20 * r
    d.ellipse([cx + 0.80 * r - pr, cy - 1.02 * r - pr, cx + 0.80 * r + pr, cy - 1.02 * r + pr],
              fill=ACCENT + (140,))
    pr2 = 0.14 * r
    d.ellipse([cx + 1.22 * r - pr2, cy - 1.40 * r - pr2, cx + 1.22 * r + pr2, cy - 1.40 * r + pr2],
              fill=PALE + (115,))

    # Ring + play triangle.
    d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=ACCENT + (255,), width=round(stroke))
    d.polygon([(cx - 0.25 * r, cy - 0.55 * r),
               (cx + 0.75 * r, cy),
               (cx - 0.25 * r, cy + 0.55 * r)], fill=LIGHT + (255,))

    img = Image.alpha_composite(img, art)
    mask = circle_mask(s) if round_shape else rounded_mask(s, radius=round(0.22 * s))
    img.putalpha(mask)
    small = img.resize((px, px), Image.LANCZOS)
    # Palette PNG with alpha: roughly 3 KB per icon instead of 13 KB.
    return small.quantize(colors=96, method=Image.FASTOCTREE, dither=Image.FLOYDSTEINBERG)


def main():
    for density, px in DENSITIES.items():
        out = os.path.join(ART, f"mipmap-{density}")
        os.makedirs(out, exist_ok=True)
        render(px).save(os.path.join(out, "ic_launcher.png"))
        render(px, round_shape=True).save(os.path.join(out, "ic_launcher_round.png"))
        print(f"mipmap-{density}: {px}x{px}")


if __name__ == "__main__":
    main()
