#!/usr/bin/env python3
"""Generate mipmap PNGs and adaptive-icon layers from iOS AppIcon 1024.png."""
from __future__ import annotations

import os
import sys

try:
    from PIL import Image
except ImportError:
    print("Install Pillow: pip install Pillow", file=sys.stderr)
    sys.exit(1)

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC_PATH = os.path.join(
    os.path.dirname(ROOT),
    "KidBox",
    "KidBox",
    "Assets.xcassets",
    "AppIcon.appiconset",
    "1024.png",
)
RES_DIR = os.path.join(ROOT, "app", "src", "main", "res")

BG_COLOR = (242, 96, 10, 255)  # #F2600A

DENSITIES = {
    "mipmap-xxxhdpi": 192,
    "mipmap-xxhdpi": 144,
    "mipmap-xhdpi": 96,
    "mipmap-hdpi": 72,
    "mipmap-mdpi": 48,
}


def main() -> None:
    if not os.path.isfile(SRC_PATH):
        print(f"Source not found: {SRC_PATH}", file=sys.stderr)
        sys.exit(1)

    img = Image.open(SRC_PATH).convert("RGBA")

    for folder, size in DENSITIES.items():
        out = os.path.join(RES_DIR, folder)
        os.makedirs(out, exist_ok=True)

        resized = img.resize((size, size), Image.Resampling.LANCZOS)
        resized.save(os.path.join(out, "ic_launcher.png"))
        resized.save(os.path.join(out, "ic_launcher_round.png"))

        bg = Image.new("RGBA", (size, size), BG_COLOR)
        bg.save(os.path.join(out, "ic_launcher_bg.png"))

        fg = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        inner_size = int(size * 0.8)
        inner = img.resize((inner_size, inner_size), Image.Resampling.LANCZOS)
        offset = (size - inner_size) // 2
        fg.paste(inner, (offset, offset), inner)
        fg.save(os.path.join(out, "ic_launcher_fg.png"))

    print("Wrote launcher PNGs to mipmap-* folders.")


if __name__ == "__main__":
    main()
