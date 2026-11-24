#!/usr/bin/env python3
"""Generates the item models of research_blueprint: one model per official icon and the override list that picks them.

The icons themselves are the official PNGs copied unchanged from the handoff package into
textures/item/blueprint/<type>/{t1..t5,copy_t1..copy_t5}.png. This script only writes JSON.
The variant index must match ResearchBlueprint.variant(): 1 + type_ordinal * 10 + (5 if copy) + (sprite_tier - 1),
with the types in the order of the ResearchType enum.
"""
import json
import pathlib

TYPES = ["sword", "pickaxe", "axe", "shovel", "hoe", "helmet", "chestplate", "leggings", "boots",
         "bow", "crossbow", "trident", "shield", "mace"]
ASSETS = pathlib.Path(__file__).resolve().parent.parent / "src/main/resources/assets/blueprintforge"
MODELS = ASSETS / "models/item"


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n")


overrides = []
for type_index, type_name in enumerate(TYPES):
    for copy in (False, True):
        for tier in range(1, 6):
            variant = 1 + type_index * 10 + (5 if copy else 0) + (tier - 1)
            name = f"{'copy_' if copy else ''}t{tier}"
            write(MODELS / "blueprint" / f"{type_name}_{name}.json", {
                "parent": "minecraft:item/generated",
                "textures": {"layer0": f"blueprintforge:item/blueprint/{type_name}/{name}"},
            })
            overrides.append({"predicate": {"blueprintforge:variant": variant}, "model": f"blueprintforge:item/blueprint/{type_name}_{name}"})

overrides.sort(key=lambda o: o["predicate"]["blueprintforge:variant"])
write(MODELS / "research_blueprint.json", {
    "parent": "minecraft:item/generated",
    "textures": {"layer0": "blueprintforge:item/blueprint/base"},
    "overrides": overrides,
})
print(f"wrote {len(overrides)} models")
