#!/usr/bin/env python3
"""Rebuilds the 16x16 face textures of the terminal v3 block from the preview sheet `sheet_faces.png`.

The sheet shows every texture magnified 8 times, with a 1x thumbnail drawn over one corner of each tile. A texel is read
from the centre of its 8x8 block; blocks that are not uniform are the ones the thumbnail covers, and those texels are
taken from the thumbnail itself, which is the texture at 1:1. The script checks that the thumbnail agrees with every
uncovered texel before it trusts it.

Pure black (0, 0, 0) in the four cut-out textures (east_upper, west_upper, upper_top, desk_front) becomes transparent:
the sheet has lost its alpha channel and the owner chose black as the stand-in for it. The rows of desk_front below its
16x2 strip are filler and become transparent too.

Usage: extract_terminal_v3_textures.py <sheet_faces.png> <output directory>
"""
import pathlib
import sys
from collections import Counter

from PIL import Image

SCALE = 8
# name -> (tile origin x, y, texel width, texel height) on the sheet
TILES = {
    "lower_front": (28, 74, 16, 16), "lower_side": (204, 74, 16, 16), "lower_side_out": (380, 74, 16, 16), "lower_back": (556, 74, 16, 16),
    "lower_top": (28, 240, 16, 16), "lower_bottom": (204, 240, 16, 16), "upper_top": (380, 240, 16, 16), "upper_back": (556, 240, 16, 16),
    "screen_off": (28, 406, 16, 16), "screen_on": (204, 406, 16, 16), "screen_active": (380, 406, 16, 16), "desk_front": (556, 406, 16, 16),
    "east_upper": (28, 572, 16, 16), "west_upper": (204, 572, 16, 16),
}
CUTOUT = {"east_upper", "west_upper", "upper_top", "desk_front"}


def read_tile(sheet, ox, oy, w, h):
    texels = {}
    bad = set()
    for y in range(h):
        for x in range(w):
            block = [sheet.getpixel((ox + x * SCALE + i, oy + y * SCALE + j)) for i in range(SCALE) for j in range(SCALE)]
            colour, count = Counter(block).most_common(1)[0]
            # A one-pixel frame is drawn over the edge blocks, so a block is read by its dominant colour. A block with no
            # dominant colour is covered by the thumbnail.
            if count >= 40:
                texels[(x, y)] = colour
            else:
                bad.add((x, y))
    return texels, bad


def find_thumbnail(sheet, ox, oy, w, h, texels, bad):
    """Offset of the 1x thumbnail that agrees with every uncovered texel, searched in the tile's top-right quarter."""
    best = None
    for sy in range(oy - 4, oy + 40):
        for sx in range(ox + w * SCALE - 48, ox + w * SCALE + 8):
            miss = 0
            for (x, y), colour in texels.items():
                if x < 8 and y > 4:
                    continue
                if sheet.getpixel((sx + x, sy + y)) != colour:
                    miss += 1
                    if best is not None and miss > best[0]:
                        break
            if best is None or miss < best[0]:
                best = (miss, sx, sy)
    return best


def main(sheet_path, out_dir):
    sheet = Image.open(sheet_path).convert("RGB")
    out = pathlib.Path(out_dir)
    out.mkdir(parents=True, exist_ok=True)
    for name, (ox, oy, w, h) in TILES.items():
        texels, bad = read_tile(sheet, ox, oy, w, h)
        thumb = None
        if bad:
            miss, sx, sy = find_thumbnail(sheet, ox, oy, w, h, texels, bad)
            thumb = (sx, sy)
            # Agreement over the whole texture except the covered texels: the thumbnail is the texture, so it must match.
            compared = [(x, y) for (x, y) in texels if sheet.getpixel((sx + x, sy + y)) != texels[(x, y)]]
            if len(compared) > 2:
                raise SystemExit(f"{name}: thumbnail at {thumb} disagrees with {len(compared)} texels")
            for x, y in bad:
                texels[(x, y)] = sheet.getpixel((sx + x, sy + y))
        image = Image.new("RGBA", (w, h))
        for (x, y), (r, g, b) in texels.items():
            # desk_front is a 16x2 strip on a 16x16 sheet tile: everything below the strip is filler, not texture.
            transparent = (r, g, b) == (0, 0, 0) or (name == "desk_front" and y >= 2)
            alpha = 0 if name in CUTOUT and transparent else 255
            image.putpixel((x, y), (r, g, b, alpha))
        image.save(out / f"{name}.png")
        print(f"{name}: {len(bad)} covered texels, thumbnail {thumb}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
