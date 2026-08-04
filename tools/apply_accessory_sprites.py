#!/usr/bin/env python3
"""Merge generated sprite specs into accessories_catalog.json.

Reads tools/accessory_sprite_specs.json (produced by build_accessory_atlas.py)
and injects the "sprite" object into matching accessories by id.

Usage:
    python3 tools/apply_accessory_sprites.py
"""

from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "app/src/main/assets/accessories_catalog.json"
SPECS = ROOT / "tools" / "accessory_sprite_specs.json"


def main() -> None:
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    specs = json.loads(SPECS.read_text(encoding="utf-8"))

    updated = 0
    for item in catalog["accessories"]:
        sprite = specs.get(item["id"])
        if sprite:
            item["sprite"] = sprite
            updated += 1

    CATALOG.write_text(json.dumps(catalog, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"Injected {updated} sprite specs into {CATALOG.name}")


if __name__ == "__main__":
    main()
