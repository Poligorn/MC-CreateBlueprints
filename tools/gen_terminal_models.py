#!/usr/bin/env python3
"""Writes the block models, the blockstate and the item model of the two-block-high terminal (mockup v3).

Geometry follows the side profile of the mockup (22.5 degree screen): the lower block is a 16-high cabinet; the upper
block holds a 2-high desk slab with the keyboard on top, a back post, and a monitor whose screen face leans back by
22.5 degrees. The side silhouette is drawn by two flat cut-out faces (east_upper, west_upper) so the wedge behind the
screen needs no geometry of its own. The model faces north; the blockstate rotates it.
"""
import json
import pathlib

ASSETS = pathlib.Path(__file__).resolve().parent.parent / "src/main/resources/assets/blueprintforge"
TEX = "blueprintforge:block/terminal/"
SCREEN_PIVOT = [8, 2.1, 8.1]


def face(texture, uv=None, cull=None, rotation=None):
    face = {"texture": "#" + texture}
    if uv is not None:
        face["uv"] = uv
    if cull:
        face["cullface"] = cull
    if rotation:
        face["rotation"] = rotation
    return face


def lower_elements():
    return [{
        "from": [0, 0, 0], "to": [16, 16, 16],
        "faces": {
            "north": face("lower_front", cull="north"),
            "east": face("lower_side_out", cull="east"),
            "south": face("lower_back", cull="south"),
            "west": face("lower_side", cull="west"),
            "down": face("lower_bottom", cull="down"),
        },
    }]


def upper_elements(screen):
    return [
        # Silhouette of the side profile, drawn as the east and west faces of a box: only those two faces exist.
        {"from": [0, 0, 0], "to": [16, 16, 16], "faces": {"east": face("east_upper", uv=[16, 0, 0, 16], cull="east"), "west": face("west_upper", cull="west")}},
        # Desk slab with the keyboard on top and its lip in front.
        {"from": [0, 0, 0], "to": [16, 2, 16], "faces": {
            "north": face("desk_front", uv=[0, 0, 16, 2]),
            "up": face("lower_top", rotation=180),
            "south": face("upper_back", uv=[0, 14, 16, 16], cull="south"),
        }},
        # Back post and the strip on top of the housing.
        {"from": [0, 2, 13], "to": [16, 16, 16], "faces": {
            "south": face("upper_back", uv=[0, 0, 16, 14], cull="south"),
            "up": face("upper_top", uv=[0, 0, 16, 3], cull="up"),
        }},
        # The monitor: a plate whose front face is the screen, leaning back by 22.5 degrees.
        {"from": [0, 2.1, 8.1], "to": [16, 16, 10.1],
         "rotation": {"origin": SCREEN_PIVOT, "axis": "x", "angle": 22.5},
         "faces": {"north": face(screen, uv=[0, 1, 16, 15]), "south": face("upper_back", uv=[0, 0, 16, 14]),
                   "up": face("upper_top", uv=[0, 0, 16, 2]), "east": face("east_upper", uv=[10, 0, 8, 14]),
                   "west": face("west_upper", uv=[8, 0, 10, 14])}},
    ]


def textures(screen_name):
    names = ["lower_front", "lower_side", "lower_side_out", "lower_back", "lower_top", "lower_bottom", "upper_top", "upper_back",
             "desk_front", "east_upper", "west_upper", screen_name]
    result = {name: TEX + name for name in names}
    result["screen"] = TEX + screen_name
    result["particle"] = TEX + "lower_side"
    return result


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n")


def main():
    models = ASSETS / "models"
    write(models / "block/terminal_lower.json", {"parent": "minecraft:block/block", "render_type": "minecraft:cutout", "textures": textures("screen_on"), "elements": lower_elements()})
    for state, screen in (("idle", "screen_on"), ("active", "screen_active")):
        write(models / f"block/terminal_upper_{state}.json",
              {"parent": "minecraft:block/block", "render_type": "minecraft:cutout", "textures": textures(screen), "elements": upper_elements("screen")})
    # Item model: both halves in one model, the upper one shifted up by a block.
    shifted = []
    for element in upper_elements("screen"):
        element = json.loads(json.dumps(element))
        element["from"][1] += 16
        element["to"][1] += 16
        if "rotation" in element:
            element["rotation"]["origin"][1] += 16
        shifted.append(element)
    write(models / "item/terminal.json", {
        "parent": "minecraft:block/block",
        "textures": textures("screen_active"),
        "elements": lower_elements() + shifted,
        "display": {
            "gui": {"rotation": [30, 225, 0], "translation": [0, -2.5, 0], "scale": [0.36, 0.36, 0.36]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.22, 0.22, 0.22]},
            "fixed": {"rotation": [0, 180, 0], "translation": [0, -1.5, 0], "scale": [0.3, 0.3, 0.3]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.2, 0.2, 0.2]},
            "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 2, 0], "scale": [0.3, 0.3, 0.3]},
        },
    })
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for half in ("lower", "upper"):
            for screen in ("idle", "active"):
                model = "terminal_lower" if half == "lower" else f"terminal_upper_{screen}"
                entry = {"model": f"blueprintforge:block/{model}"}
                if y:
                    entry["y"] = y
                variants[f"facing={facing},half={half},screen={screen}"] = entry
    write(ASSETS / "blockstates/terminal.json", {"variants": variants})


if __name__ == "__main__":
    main()
