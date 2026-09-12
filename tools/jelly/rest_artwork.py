"""Place the reviewed Jelly rest expressions in the care atlas."""
from __future__ import annotations

import hashlib
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
CELL = 256
REST_FRAMES = list(range(24, 30))
REST_CLIP = [20, 24, 25, 26, 27, 28, 29, 29, 29, 29, 29, 29, 29, 29, 29, 28, 27, 26, 25, 24]
REST_ANCHOR = {
    "mouth": [132.5 / CELL, 183 / CELL],
    "head": [128 / CELL, 130 / CELL],
    "body": [128 / CELL, 180 / CELL],
    "ground": [128 / CELL, 230 / CELL],
}


def sources() -> list[Path]:
    return [ROOT / "tools/jelly/clean" / f"rest_{index}.png" for index in range(6)]


def apply_rest(atlas: Image.Image, anchors: list[dict] | None = None) -> list[dict]:
    """Paste exact 256px sources into cells 24..29 and return source transforms."""
    transforms = []
    for frame, source in zip(REST_FRAMES, sources()):
        pose = Image.open(source).convert("RGBA")
        if pose.size != (CELL, CELL) or pose.getbbox() is None:
            raise ValueError(f"Invalid Jelly rest source: {source}")
        atlas.paste(pose, (frame % 4 * CELL, frame // 4 * CELL))
        if anchors is not None:
            anchors.append({name: list(values) for name, values in REST_ANCHOR.items()})
        transforms.append({"source": str(source.relative_to(ROOT)),
                           "sourceSha256": hashlib.sha256(source.read_bytes()).hexdigest(),
                           "sourceBounds": [0, 0, CELL, CELL], "offset": [0, 0], "scale": 1.0})
    return transforms
