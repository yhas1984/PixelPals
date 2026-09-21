#!/usr/bin/env python3
"""Derive localized 7-inch and 10-inch tablet slides from the ASO campaign."""

from __future__ import annotations

import copy
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CONFIG = ROOT / "screenshots-editor" / "app-store-screenshots.json"

SPECS = {
    "android-7": {
        "top_caption": {"x": 96, "y": 72, "width": 1008, "height": 390, "rotation": 0, "zIndex": 4},
        "bottom_caption": {"x": 96, "y": 1435, "width": 1008, "height": 390, "rotation": 0, "zIndex": 4},
        "bottom_device": {"x": 210, "y": 500, "width": 780, "height": 1248, "rotation": 0, "zIndex": 3},
        "top_device": {"x": 240, "y": 130, "width": 720, "height": 1152, "rotation": 0, "zIndex": 3},
    },
    "android-10": {
        "top_caption": {"x": 128, "y": 96, "width": 1344, "height": 510, "rotation": 0, "zIndex": 4},
        "bottom_caption": {"x": 128, "y": 1930, "width": 1344, "height": 510, "rotation": 0, "zIndex": 4},
        "bottom_device": {"x": 280, "y": 680, "width": 1040, "height": 1664, "rotation": 0, "zIndex": 3},
        "top_device": {"x": 320, "y": 170, "width": 960, "height": 1536, "rotation": 0, "zIndex": 3},
    },
}


def main() -> None:
    project = json.loads(CONFIG.read_text(encoding="utf-8"))
    phone_slides = project["slidesByDevice"]["android"]
    for device, spec in SPECS.items():
        tablet_slides = []
        for index, source in enumerate(phone_slides, start=1):
            slide = copy.deepcopy(source)
            slide["id"] = f"{source['id']}_{device}"
            slide["screenshot"] = source["screenshot"].replace("/phone/", "/tablet/")
            caption_below = slide["layout"] == "device-top"
            slide["transforms"] = {
                "caption": copy.deepcopy(spec["bottom_caption" if caption_below else "top_caption"]),
                "device": copy.deepcopy(spec["top_device" if caption_below else "bottom_device"]),
            }
            # Keep the campaign's alternating rhythm without tilting a wide tablet frame.
            slide["transforms"]["device"]["rotation"] = 0
            tablet_slides.append(slide)
        project["slidesByDevice"][device] = tablet_slides
    CONFIG.write_text(json.dumps(project, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
