#!/usr/bin/env python3
"""Builds data.js for the BitCraft crafting planner from the community game data.

Usage:
    git clone --depth 1 -b cereal/cs https://github.com/BitCraftToolBox/BitCraft_GameData gamedata
    python3 tools/build_data.py gamedata/static data.js
"""
import json
import os
import sys

# Recipes that only move items between forms (packages, scrapping, re-crafting) and would
# create cycles in the crafting tree.
SKIPPED_VERBS = {"Unpack", "Package", "Scrap", "Recraft", "Reforge", "Pack"}
SKIPPED_TAGS = {"DEVELOPER ITEM", "Premium Cosmetic", "Premium Cosmetic Clothes", "Premium Item"}
MIN_EXTRACTION_PROBABILITY = 0.001
MAX_SOURCES_PER_ITEM = 8


def load(static_dir, name):
    with open(os.path.join(static_dir, name + ".json"), encoding="utf-8") as f:
        return json.load(f)


def key(stack):
    return ("c" if stack["item_type"] == "Cargo" else "i") + str(stack["item_id"])


def main(static_dir, out_path):
    items = {i["id"]: i for i in load(static_dir, "item_desc")}
    cargo = {c["id"]: c for c in load(static_dir, "cargo_desc")}
    item_lists = {l["id"]: l for l in load(static_dir, "item_list_desc")}
    resources = {r["id"]: r for r in load(static_dir, "resource_desc")}
    building_types = {b["id"]: b["name"] for b in load(static_dir, "building_type_desc")}
    tool_types = {t["id"]: t["name"] for t in load(static_dir, "tool_type_desc")}
    skills = {s["id"]: s["name"] for s in load(static_dir, "skill_desc")}

    def desc(k):
        return (cargo if k[0] == "c" else items).get(int(k[1:]))

    # The same item exists once per rarity (Common, Uncommon, ...). For planning they are the
    # same thing, so every variant maps to one canonical key (lowest rarity, listed in the compendium).
    rarity_rank = {"Common": 0, "Uncommon": 1, "Rare": 2, "Epic": 3, "Legendary": 4, "Mythic": 5}
    canonical_by_name = {}
    for prefix, table in (("i", items), ("c", cargo)):
        for d in table.values():
            if d.get("item_list_id"):
                continue
            rank = (rarity_rank.get(d.get("rarity"), 9), not d.get("compendium_entry", True), d["id"])
            name_key = (prefix, d["name"])
            if name_key not in canonical_by_name or rank < canonical_by_name[name_key][0]:
                canonical_by_name[name_key] = (rank, prefix + str(d["id"]))

    def canon(k):
        d = desc(k)
        if not d:
            return k
        return canonical_by_name.get((k[0], d["name"]), (None, k))[1]

    def expand(stack, factor=1.0, depth=0):
        """Resolves "... Output" item lists into the items they turn into (expected amounts)."""
        k = key(stack)
        d = desc(k)
        qty = stack["quantity"] * factor
        if k[0] == "i" and d and d.get("item_list_id") and depth < 4:
            lst = item_lists.get(d["item_list_id"])
            if lst:
                total = sum(p["probability"] for p in lst["possibilities"]) or 1.0
                result = {}
                for p in lst["possibilities"]:
                    for s in p["items"]:
                        for kk, q in expand(s, qty * p["probability"] / total, depth + 1).items():
                            result[kk] = result.get(kk, 0.0) + q
                return result
        return {canon(k): qty}

    used = set()
    recipes = []
    for r in load(static_dir, "crafting_recipe_desc"):
        verb = r["name"].split(" ")[0]
        if verb in SKIPPED_VERBS or not r["crafted_item_stacks"] or not r["consumed_item_stacks"]:
            continue
        outputs = {}
        for s in r["crafted_item_stacks"]:
            for k, q in expand(s).items():
                outputs[k] = outputs.get(k, 0.0) + q
        inputs = [[canon(key(s)), s["quantity"] * s["consumption_chance"]] for s in r["consumed_item_stacks"]]
        if any(desc(k) is None for k in list(outputs) + [i[0] for i in inputs]):
            continue
        if any(desc(k).get("tag") in SKIPPED_TAGS for k in outputs):
            continue
        br = r["building_requirement"]
        recipes.append({
            "v": verb,
            "b": br["building_type"] if br else 0,
            "bt": br["tier"] if br else 0,
            "t": [[t["tool_type"], t["level"]] for t in r["tool_requirements"]],
            "s": [[s["skill_id"], s["level"]] for s in r["level_requirements"] if s["skill_id"] > 1],
            "in": [[k, round(q, 4)] for k, q in inputs],
            "out": [[k, round(q, 4)] for k, q in sorted(outputs.items(), key=lambda kv: -kv[1]) if q > 0],
            "a": r["actions_required"],
        })
        used.update(outputs)
        used.update(i[0] for i in inputs)

    # Where raw materials come from: extraction (chop, mine, gather...) and resource destruction.
    sources = {}

    def add_source(k, src):
        lst = sources.setdefault(k, [])
        if len(lst) < MAX_SOURCES_PER_ITEM and src not in lst:
            lst.append(src)

    for e in load(static_dir, "extraction_recipe_desc"):
        res = resources.get(e["resource_id"])
        if not res:
            continue
        src = {
            "r": res["name"],
            "v": e["verb_phrase"],
            "t": [[t["tool_type"], t["level"]] for t in e["tool_requirements"]],
            "s": [[s["skill_id"], s["level"]] for s in e["level_requirements"] if s["skill_id"] > 1],
            "tier": res["tier"],
        }
        found = set()
        for s in e["extracted_item_stacks"]:
            if s["probability"] < MIN_EXTRACTION_PROBABILITY:
                continue
            found.update(k for k, q in expand(s["item_stack"]).items() if q > 0)
        for s in res.get("on_destroy_yield") or []:
            found.update(k for k, q in expand(s).items() if q > 0)
        for k in found:
            if desc(k):
                add_source(k, src)
                used.add(k)

    # Creatures drop their bodies (cargo) and trophies when hunted.
    for enemy in load(static_dir, "enemy_desc"):
        src = {"r": enemy["name"], "v": "Hunt" if enemy.get("huntable") else "Fight", "t": [], "s": [], "tier": enemy["tier"]}
        for s in enemy.get("extracted_item_stacks") or []:
            if s["probability"] < MIN_EXTRACTION_PROBABILITY:
                continue
            for k, q in expand(s["item_stack"]).items():
                if q > 0 and desc(k):
                    add_source(k, src)
                    used.add(k)

    out_items = {}
    for k in sorted(used):
        d = desc(k)
        out_items[k] = [d["name"], d.get("tier", 0), d.get("tag", "")]

    data = {
        "items": out_items,
        "recipes": recipes,
        "sources": sources,
        "buildings": {str(k): v for k, v in building_types.items()},
        "tools": {str(k): v for k, v in tool_types.items()},
        "skills": {str(k): v for k, v in skills.items()},
    }
    with open(out_path, "w", encoding="utf-8") as f:
        f.write("// Generated by tools/build_data.py from BitCraftToolBox/BitCraft_GameData. Do not edit.\n")
        f.write("window.BITCRAFT_DATA = ")
        json.dump(data, f, ensure_ascii=False, separators=(",", ":"))
        f.write(";\n")
    print(f"{len(out_items)} items, {len(recipes)} recipes, {len(sources)} raw sources -> {out_path}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
