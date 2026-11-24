#!/usr/bin/env python3
"""Writes data/blueprintforge/research_target/*.json: one target per type and tier.

The ladder follows the owner's decision of 24 November 2026: T1 wood (leather for armor), T2 gold, T3 iron, T4 diamond,
T5 netherite. The five proposed types (bow, crossbow, trident, shield, mace) have a single vanilla item, so every tier
uses the same one. Existing files are replaced, so rerunning the script is safe.
"""
import json
import pathlib

OUT = pathlib.Path(__file__).resolve().parent.parent / "src/main/resources/data/blueprintforge/research_target"
TOOL_MATERIALS = ["wooden", "golden", "iron", "diamond", "netherite"]
ARMOR_MATERIALS = ["leather", "golden", "iron", "diamond", "netherite"]
TOOLS = ["sword", "pickaxe", "axe", "shovel", "hoe"]
ARMOR = ["helmet", "chestplate", "leggings", "boots"]
SINGLE = ["bow", "crossbow", "trident", "shield", "mace"]

OUT.mkdir(parents=True, exist_ok=True)
for old in OUT.glob("*.json"):
    old.unlink()

count = 0
for tier in range(1, 6):
    for kind, types, materials in (("tool", TOOLS, TOOL_MATERIALS), ("armor", ARMOR, ARMOR_MATERIALS)):
        for type_name in types:
            item = f"minecraft:{materials[tier - 1]}_{type_name}"
            (OUT / f"{type_name}_t{tier}.json").write_text(json.dumps({"item": item, "type": type_name, "tier": f"blueprintforge:t{tier}"}, indent=2) + "\n")
            count += 1
    for type_name in SINGLE:
        (OUT / f"{type_name}_t{tier}.json").write_text(json.dumps({"item": f"minecraft:{type_name}", "type": type_name, "tier": f"blueprintforge:t{tier}"}, indent=2) + "\n")
        count += 1
print(f"wrote {count} targets")
