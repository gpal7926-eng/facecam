#!/usr/bin/env python3
"""Regenerate every FaceCam icon asset from the master artwork.

Source:  tools/facecam_icon.png  (square, transparent-rounded tile)
Outputs:
  android/app/src/main/res/mipmap-<density>/{ic_launcher,ic_launcher_round,ic_launcher_foreground}.png
  docs/icon-512.png          (store master)
  web/icon-256.png           (favicon / apple-touch-icon / onboarding logo)

Usage:  python3 tools/make_icons.py     (needs Pillow)
"""
import os
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "tools", "facecam_icon.png")
RES = os.path.join(ROOT, "android", "app", "src", "main", "res")

DENSITIES = [("mdpi", 48, 108), ("hdpi", 72, 162), ("xhdpi", 96, 216),
             ("xxhdpi", 144, 324), ("xxxhdpi", 192, 432)]

# The tile is drawn large enough to fill the launcher mask, while its content
# (lens, brackets, FACE CAM text) stays inside Android's central safe circle.
CONTENT_SCALE = 0.56


def rounded_mask(size, radius_ratio=0.22):
    """Anti-aliased circle mask, drawn supersampled."""
    ss = size * 4
    m = Image.new("L", (ss, ss), 0)
    ImageDraw.Draw(m).ellipse((0, 0, ss - 1, ss - 1), fill=255)
    return m.resize((size, size), Image.LANCZOS)


def main():
    master = Image.open(SRC).convert("RGBA")

    def resized(size):
        return master.resize((size, size), Image.LANCZOS)

    for name, legacy, fg in DENSITIES:
        d = os.path.join(RES, "mipmap-" + name)
        os.makedirs(d, exist_ok=True)

        # legacy square
        resized(legacy).save(os.path.join(d, "ic_launcher.png"))

        # legacy round
        r = resized(legacy)
        r.putalpha(Image.composite(r.getchannel("A"), Image.new("L", r.size, 0),
                                   rounded_mask(legacy)))
        r.save(os.path.join(d, "ic_launcher_round.png"))

        # adaptive foreground: tile scaled into the safe zone, transparent canvas
        canvas = Image.new("RGBA", (fg, fg), (0, 0, 0, 0))
        inner = max(1, int(round(fg * CONTENT_SCALE)))
        t = master.resize((inner, inner), Image.LANCZOS)
        off = (fg - inner) // 2
        canvas.alpha_composite(t, (off, off))
        canvas.save(os.path.join(d, "ic_launcher_foreground.png"))

        print("wrote mipmap-" + name)

    resized(512).save(os.path.join(ROOT, "docs", "icon-512.png"))
    resized(256).save(os.path.join(ROOT, "web", "icon-256.png"))
    resized(128).save(os.path.join(ROOT, "web", "icon-128.png"))
    print("wrote docs/icon-512.png, web/icon-256.png and web/icon-128.png")


if __name__ == "__main__":
    main()
