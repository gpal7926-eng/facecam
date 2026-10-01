#!/usr/bin/env python3
"""Bundle web/ (index.html + css/styles.css + js/*.js) into the single-file
FaceCam-preview.html. Pure stdlib, no dependencies."""
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WEB = os.path.join(ROOT, "web")
OUT = os.path.join(ROOT, "FaceCam-preview.html")

JS_ORDER = ["cameras.js", "effects.js", "video.js", "pro.js", "app.js"]

HEADER = """<!DOCTYPE html>
<!--
  FaceCam - standalone single-file web preview.
  HTML + CSS + JavaScript in one file: no build, no dependencies, no network.
  19 vintage film cameras (incl. 1950s-1990s decade looks + B&W) and 9 beauty
  cameras, photo + video, slow motion, subtitles, manual camera mode, 3D intro.
  Free, no paywall, no ads.
-->
"""


def read(path):
    with open(path, "r", encoding="utf-8") as f:
        return f.read()


def main():
    html = read(os.path.join(WEB, "index.html"))
    css = read(os.path.join(WEB, "css", "styles.css"))

    # drop the old doctype/comment lines from the template and prepend ours
    html = re.sub(r"^<!DOCTYPE html>\s*", "", html, count=1)

    html = html.replace(
        '<link rel="stylesheet" href="css/styles.css">',
        "<style>\n" + css + "\n</style>",
    )

    for name in JS_ORDER:
        code = read(os.path.join(WEB, "js", name))
        code = code.replace("</script", "<\\/script")
        tag = '<script src="js/%s"></script>' % name
        html = html.replace(tag, "<script>\n" + code + "\n</script>")

    out = HEADER + html
    with open(OUT, "w", encoding="utf-8") as f:
        f.write(out)
    print("wrote", OUT, len(out), "bytes")


if __name__ == "__main__":
    main()
