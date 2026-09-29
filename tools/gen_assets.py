#!/usr/bin/env python3
"""Regenerates placeholder textures and the empty GameTest structure.

Run from the repository root: python3 tools/gen_assets.py
Only the Python standard library is used, so the output is reproducible on any machine.
"""
import gzip
import os
import struct
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX = os.path.join(ROOT, "src/main/resources/assets/blueprintforge/textures")
STRUCTURE = os.path.join(ROOT, "src/gametest/resources/data/blueprintforge/structure/empty.nbt")

TRANSPARENT = (0, 0, 0, 0)
PAPER = (231, 222, 196, 255)
PAPER_SHADE = (205, 194, 164, 255)
PAPER_DARK = (150, 128, 96, 255)
PAPER_DARK_SHADE = (118, 98, 72, 255)
INK = (38, 52, 82, 255)
INK_LIGHT = (86, 104, 140, 255)
BRASS = (201, 158, 72, 255)
BRASS_DARK = (140, 104, 44, 255)
METAL = (62, 64, 70, 255)
METAL_LIGHT = (88, 90, 98, 255)
METAL_DARK = (40, 41, 46, 255)
SLOT = (22, 22, 26, 255)


def png(path, pixels):
    height = len(pixels)
    width = len(pixels[0])
    raw = b"".join(b"\x00" + b"".join(struct.pack("BBBB", *px) for px in row) for row in pixels)

    def chunk(tag, data):
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    data = b"\x89PNG\r\n\x1a\n"
    data += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    data += chunk(b"IDAT", zlib.compress(raw, 9))
    data += chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(data)


def canvas(fill=TRANSPARENT):
    return [[fill for _ in range(16)] for _ in range(16)]


def rect(img, x0, y0, x1, y1, color):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            img[y][x] = color


def outline(img, x0, y0, x1, y1, color):
    for x in range(x0, x1 + 1):
        img[y0][x] = color
        img[y1][x] = color
    for y in range(y0, y1 + 1):
        img[y][x0] = color
        img[y][x1] = color


def sheet(paper=PAPER, shade=PAPER_SHADE):
    img = canvas()
    rect(img, 2, 1, 13, 14, paper)
    for y in range(1, 15):
        img[y][13] = shade
    for x in range(2, 14):
        img[14][x] = shade
    return img


def drawing(img, ink):
    # A stylised blade drawn in ink: the blueprint is the right to make a thing.
    for i in range(6):
        img[10 - i][6 + i] = ink
    img[11][5] = ink
    img[10][5] = ink
    img[11][6] = ink
    for x in range(5, 11):
        img[12][x] = INK_LIGHT


def blank():
    img = sheet()
    for y in (4, 7, 10):
        for x in range(4, 12):
            img[y][x] = PAPER_SHADE
    return img


def original():
    img = sheet()
    outline(img, 3, 2, 12, 13, INK)
    outline(img, 4, 3, 11, 12, INK_LIGHT)
    drawing(img, INK)
    img[2][3] = BRASS
    img[2][12] = BRASS
    img[13][3] = BRASS
    img[13][12] = BRASS
    return img


def copy():
    img = sheet()
    outline(img, 4, 3, 11, 12, INK_LIGHT)
    drawing(img, INK_LIGHT)
    # clipped corner marks the sheet as a copy
    for i in range(3):
        for j in range(3 - i):
            img[1 + i][13 - j] = TRANSPARENT
    return img


def ancient():
    img = sheet(PAPER_DARK, PAPER_DARK_SHADE)
    outline(img, 3, 2, 12, 13, PAPER_DARK_SHADE)
    drawing(img, (60, 40, 26, 255))
    img[5][4] = PAPER_DARK_SHADE
    img[9][11] = PAPER_DARK_SHADE
    return img


def fragment():
    img = canvas()
    rows = {4: (5, 10), 5: (4, 11), 6: (4, 11), 7: (5, 12), 8: (4, 11), 9: (5, 10), 10: (6, 11), 11: (7, 9)}
    for y, (x0, x1) in rows.items():
        rect(img, x0, y, x1, y, PAPER)
    for y, (x0, x1) in rows.items():
        img[y][x1] = PAPER_SHADE
    img[6][6] = INK
    img[7][7] = INK
    img[8][8] = INK
    return img


def metal_block():
    img = canvas(METAL)
    outline(img, 0, 0, 15, 15, BRASS_DARK)
    outline(img, 1, 1, 14, 14, BRASS)
    for x in range(2, 14):
        img[2][x] = METAL_LIGHT
    for y in range(3, 14):
        img[y][13] = METAL_DARK
    return img


def archive_side():
    img = metal_block()
    for y in (5, 10):
        for x in range(3, 13):
            img[y][x] = METAL_DARK
    return img


def archive_front():
    img = metal_block()
    rect(img, 3, 5, 12, 8, SLOT)
    rect(img, 4, 6, 11, 7, PAPER)
    rect(img, 6, 11, 9, 12, BRASS)
    return img


def archive_top():
    img = canvas(BRASS)
    outline(img, 0, 0, 15, 15, BRASS_DARK)
    rect(img, 3, 3, 12, 12, PAPER_SHADE)
    outline(img, 3, 3, 12, 12, BRASS_DARK)
    for y in (5, 7, 9):
        for x in range(5, 11):
            img[y][x] = INK_LIGHT
    return img


def archive_bottom():
    img = metal_block()
    rect(img, 5, 5, 10, 10, SLOT)
    outline(img, 5, 5, 10, 10, BRASS_DARK)
    return img


# --- minimal NBT writer for the GameTest template ---

def nbt_string(s):
    b = s.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def nbt_named(tag_id, name, payload):
    return struct.pack(">b", tag_id) + nbt_string(name) + payload


def nbt_int_list(values):
    return struct.pack(">bi", 3, len(values)) + b"".join(struct.pack(">i", v) for v in values)


def nbt_compound(entries):
    return b"".join(entries) + b"\x00"


def empty_structure(size=5):
    air = nbt_compound([nbt_named(8, "Name", nbt_string("minecraft:air"))])
    palette = struct.pack(">bi", 10, 1) + air
    blocks = []
    for x in range(size):
        for y in range(size):
            for z in range(size):
                blocks.append(nbt_compound([
                    nbt_named(9, "pos", nbt_int_list([x, y, z])),
                    nbt_named(3, "state", struct.pack(">i", 0)),
                ]))
    blocks_payload = struct.pack(">bi", 10, len(blocks)) + b"".join(blocks)
    root = nbt_compound([
        nbt_named(3, "DataVersion", struct.pack(">i", 3955)),
        nbt_named(9, "size", nbt_int_list([size, size, size])),
        nbt_named(9, "palette", palette),
        nbt_named(9, "blocks", blocks_payload),
        nbt_named(9, "entities", struct.pack(">bi", 0, 0)),
    ])
    data = struct.pack(">b", 10) + nbt_string("") + root
    os.makedirs(os.path.dirname(STRUCTURE), exist_ok=True)
    with gzip.GzipFile(STRUCTURE, "wb", mtime=0) as f:
        f.write(data)


if __name__ == "__main__":
    png(os.path.join(TEX, "item/blueprint_blank.png"), blank())
    png(os.path.join(TEX, "item/blueprint_original.png"), original())
    png(os.path.join(TEX, "item/blueprint_copy.png"), copy())
    png(os.path.join(TEX, "item/blueprint_ancient.png"), ancient())
    png(os.path.join(TEX, "item/blueprint_fragment.png"), fragment())
    png(os.path.join(TEX, "block/blueprint_archive_side.png"), archive_side())
    png(os.path.join(TEX, "block/blueprint_archive_front.png"), archive_front())
    png(os.path.join(TEX, "block/blueprint_archive_top.png"), archive_top())
    png(os.path.join(TEX, "block/blueprint_archive_bottom.png"), archive_bottom())
    empty_structure()
    print("assets generated")
