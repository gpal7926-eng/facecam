#!/usr/bin/env python3
"""Generate the Android camera-preset JSONs for the decade / B&W / new beauty
looks, computing a 4x5 ColorMatrix from saturation, warmth, gain, lift and
contrast so the native build matches the web build's intent."""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "android", "app", "src", "main", "assets", "cameras")

LR, LG, LB = 0.2126, 0.7152, 0.0722


def matrix(sat, warmth, gain=1.0, lift=0.0, contrast=1.0):
    s = sat
    k = gain * contrast
    sr, sg, sb = (1 - s) * LR, (1 - s) * LG, (1 - s) * LB
    base = [
        sr + s, sg, sb,
        sr, sg + s, sb,
        sr, sg, sb + s,
    ]
    off = 127.5 * (1 - contrast) + lift * 255.0
    off_r = off + warmth * 0.55 * 255.0
    off_g = off + warmth * 0.05 * 255.0
    off_b = off - warmth * 0.55 * 255.0
    m = [
        base[0] * k, base[1] * k, base[2] * k, 0.0, off_r,
        base[3] * k, base[4] * k, base[5] * k, 0.0, off_g,
        base[6] * k, base[7] * k, base[8] * k, 0.0, off_b,
        0.0, 0.0, 0.0, 1.0, 0.0,
    ]
    return [round(v, 4) for v in m]


def vintage(pid, name, tag, desc, sat, warmth, gain, lift, contrast,
            grain, leak, vignette, frame="35mm", date_stamp=True):
    return {
        "id": pid, "name": name, "tag": tag, "description": desc,
        "group": "vintage",
        "colorMatrix": matrix(sat, warmth, gain, lift, contrast),
        "grain": grain, "leak": leak, "vignette": vignette,
        "frame": frame, "dateStamp": date_stamp, "instant": False, "overlay": None,
    }


def beauty(pid, name, tag, desc, sat, warmth, b):
    return {
        "id": pid, "name": name, "tag": tag, "description": desc,
        "group": "beauty",
        "colorMatrix": matrix(sat, warmth, 1.0, 0.0, 1.0),
        "grain": 0.0, "leak": 0.0, "vignette": 0.0,
        "frame": "none", "dateStamp": False, "instant": False, "overlay": None,
        "beauty": b,
    }


PRESETS = [
    # ---- decades -------------------------------------------------------
    vintage("decade_50s", "1950s", "Sepia Cinema",
            "Soft, faded sepia with deep corners - a hand-tinted 1950s print.",
            0.58, 0.10, 1.03, 0.06, 0.94, 0.50, 0.34, 0.56, "35mm", False),
    vintage("decade_60s", "1960s", "Kodachrome",
            "Vivid, saturated Kodachrome warmth with a gentle vintage fade.",
            1.26, 0.06, 1.03, 0.03, 1.06, 0.34, 0.30, 0.36, "35mm", True),
    vintage("decade_70s", "1970s", "Warm Fade",
            "Warm brown-orange cast, lifted blacks and heavy grain. Pure 70s.",
            1.06, 0.12, 1.04, 0.075, 0.94, 0.56, 0.50, 0.42, "35mm", True),
    vintage("decade_80s", "1980s", "Neon VHS",
            "Magenta-and-cyan neon with a VHS glow and blotchy chroma noise.",
            1.30, 0.04, 1.04, 0.0, 1.14, 0.50, 0.40, 0.38, "35mm", True),
    vintage("decade_90s", "1990s", "Point & Shoot",
            "Cool, contrasty compact-camera colour with a slight modern fade.",
            1.02, -0.03, 1.00, -0.01, 1.12, 0.34, 0.20, 0.34, "35mm", True),
    # ---- black & white -------------------------------------------------
    vintage("mono_bw", "B&W Classic", "Black & White",
            "Classic black-and-white film: full tonal range, grain and a soft vignette.",
            0.0, 0.0, 1.02, 0.01, 1.08, 0.44, 0.06, 0.44, "35mm", False),
    vintage("mono_noir", "B&W Noir", "High-Contrast B&W",
            "Dramatic high-contrast monochrome with crushed blacks and heavy falloff.",
            0.0, 0.0, 1.00, -0.04, 1.38, 0.50, 0.05, 0.62, "cinema", False),
    # ---- new beauty looks ---------------------------------------------
    beauty("beauty_soft", "SOFT GLAM", "Beauty Soft Glam",
           "Heavy, silky skin smoothing with a strong soft-glow - a glamour look.",
           1.09, 0.06,
           {"exposure": 0.09, "contrast": 0.16, "saturation": 0.10, "warmth": 0.06,
            "smooth": 0.55, "sharpen": 0.25, "glow": 0.30}),
    beauty("beauty_vivid", "VIVID POP", "Beauty Vivid Pop",
           "Bright, saturated and crisp - colours that jump off the screen.",
           1.26, 0.02,
           {"exposure": 0.06, "contrast": 0.30, "saturation": 0.24, "warmth": 0.02,
            "smooth": 0.30, "sharpen": 0.45, "glow": 0.12}),
    beauty("beauty_cool", "COOL CLEAN", "Beauty Cool Clean",
           "A cool white balance with clean, sharp detail. Modern and airy.",
           1.02, -0.05,
           {"exposure": 0.09, "contrast": 0.22, "saturation": 0.02, "warmth": -0.05,
            "smooth": 0.32, "sharpen": 0.38, "glow": 0.12}),
    beauty("beauty_golden", "GOLDEN HOUR", "Beauty Golden Hour",
           "Warm golden light with a rich highlight glow - the hour before dusk.",
           1.13, 0.12,
           {"exposure": 0.10, "contrast": 0.16, "saturation": 0.14, "warmth": 0.12,
            "smooth": 0.40, "sharpen": 0.28, "glow": 0.32}),
    beauty("beauty_mono", "MONO BEAUTY", "Beauty Clean B&W",
           "A clean black-and-white beauty look: smooth skin, crisp detail, no colour.",
           0.0, 0.0,
           {"exposure": 0.09, "contrast": 0.24, "saturation": 0.0, "warmth": 0.0,
            "smooth": 0.40, "sharpen": 0.38, "glow": 0.16}),
]


def main():
    os.makedirs(OUT, exist_ok=True)
    for p in PRESETS:
        path = os.path.join(OUT, p["id"] + ".json")
        with open(path, "w", encoding="utf-8") as f:
            json.dump(p, f, indent=2, ensure_ascii=False)
            f.write("\n")
        print("wrote", os.path.basename(path))


if __name__ == "__main__":
    main()
