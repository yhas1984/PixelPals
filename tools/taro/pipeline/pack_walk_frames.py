#!/usr/bin/env python3
"""Pack eight individual Taro walk poses into the two 2x2 source boards."""

from __future__ import annotations

from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[3]
SOURCE_DIR = ROOT / "tools/taro/pipeline/raw/walk_frames_v3"
RAW_DIR = ROOT / "tools/taro/pipeline/raw"


def main() -> int:
    frames = [Image.open(SOURCE_DIR / f"walk_{index:02d}.png").convert("RGBA") for index in range(8)]
    boards = {
        "02_walk_a.png": frames[:4],
        "03_walk_b.png": frames[4:],
    }
    for filename, board_frames in boards.items():
        board = Image.new("RGBA", (1024, 1024), (255, 255, 255, 255))
        for index, frame in enumerate(board_frames):
            cell = frame.resize((512, 512), Image.Resampling.LANCZOS)
            board.alpha_composite(cell, ((index % 2) * 512, (index // 2) * 512))
        board.convert("RGB").save(RAW_DIR / filename, optimize=True)
        print(f"packed {filename}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
