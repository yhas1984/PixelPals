"""Reviewed alpha-only removal of Bloop's thin wire from placed care cells.

The pose board and its placement stay unchanged. This is deliberately specific
to the reviewed artwork, not a general silhouette filter: hands, body lobes and
dream bubbles must survive intact.
"""
import numpy as np
from PIL import Image


# Frame: (first masked row, last masked row, wire/body junction row).
# Coordinates refer to the final 256px cell, after scaling and placement.
WIRE_REGIONS = {
    0: (130, 176, 167), 3: (139, 180, 171), 4: (129, 180, 171),
    5: (132, 184, 175), 6: (126, 172, 163), 8: (178, 217, 194),
    9: (161, 196, 178), 10: (127, 178, 169), 11: (166, 201, 182),
    12: (154, 193, 184), 13: (148, 187, 178), 14: (153, 188, 179),
    15: (156, 194, 185), 16: (164, 197, 185), 17: (162, 203, 194),
    20: (160, 196, 187), 21: (156, 194, 185), 22: (155, 190, 179),
    23: (156, 190, 181),
}


def clean_care_cell(cell: Image.Image, index: int) -> Image.Image:
    if cell.size != (256, 256) or cell.mode != "RGBA":
        raise ValueError("Bloop cleanup needs a placed 256px RGBA cell")
    if index not in WIRE_REGIONS:
        return cell.copy()
    rgba = np.array(cell)
    first, last, junction = WIRE_REGIONS[index]
    edges = {}
    for y in range(first, last + 1):
        xs = np.flatnonzero(rgba[y, :, 3] >= 16)
        runs = np.split(xs, np.flatnonzero(np.diff(xs) > 1) + 1)
        body = next((run for run in runs if len(run) >= 40), None)
        if body is None:
            raise ValueError(f"Bloop frame {index}, row {y}: expected body not found")
        edges[y] = float(body[0])
    # Rejoin the uninterrupted outline on either side of the wire's root.
    start, end = junction - 1, junction + 7
    for y, edge in zip(range(start, end + 1), np.linspace(edges[start], edges[end], end - start + 1)):
        edges[y] = edge
    for y, edge in edges.items():
        edge -= 0.5
        x = int(edge)
        rgba[y, :x, 3] = 0
        rgba[y, x, 3] = int(rgba[y, x, 3] * (1 - (edge - x)))
    return Image.fromarray(rgba)
