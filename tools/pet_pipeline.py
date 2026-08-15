#!/usr/bin/env python3
"""Shared V2 atlas quality gates, preview generation, and promotion checks.

The pet-specific build scripts own art selection and frame naming.  This module
owns the invariant checks that must be identical for every V2 atlas.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
from collections import deque
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable

import numpy as np
from PIL import Image, ImageDraw


@dataclass(frozen=True)
class AtlasPolicy:
    minimum_padding: int = 16
    maximum_interior_hole_pixels: int = 128
    maximum_interior_hole_component: int = 64
    maximum_detached_component: int = 64
    maximum_shadow_pixels: int = 120
    maximum_light_shadow_pixels: int = 64


def load_spec(path: Path) -> dict[str, object]:
    return json.loads(path.read_text(encoding="utf-8"))


def _components(mask: np.ndarray) -> list[list[tuple[int, int]]]:
    height, width = mask.shape
    visited = np.zeros_like(mask, dtype=bool)
    result: list[list[tuple[int, int]]] = []
    for y, x in np.argwhere(mask):
        if visited[y, x]:
            continue
        visited[y, x] = True
        queue: deque[tuple[int, int]] = deque([(int(y), int(x))])
        component: list[tuple[int, int]] = []
        while queue:
            current_y, current_x = queue.popleft()
            component.append((current_y, current_x))
            for next_y, next_x in (
                (current_y - 1, current_x),
                (current_y + 1, current_x),
                (current_y, current_x - 1),
                (current_y, current_x + 1),
            ):
                if (
                    0 <= next_y < height
                    and 0 <= next_x < width
                    and mask[next_y, next_x]
                    and not visited[next_y, next_x]
                ):
                    visited[next_y, next_x] = True
                    queue.append((next_y, next_x))
        result.append(component)
    return result


def _border_connected_transparent(solid: np.ndarray) -> np.ndarray:
    transparent = ~solid
    height, width = transparent.shape
    exterior = np.zeros_like(transparent, dtype=bool)
    queue: deque[tuple[int, int]] = deque()

    def enqueue(y: int, x: int) -> None:
        if transparent[y, x] and not exterior[y, x]:
            exterior[y, x] = True
            queue.append((y, x))

    for x in range(width):
        enqueue(0, x)
        enqueue(height - 1, x)
    for y in range(height):
        enqueue(y, 0)
        enqueue(y, width - 1)
    while queue:
        y, x = queue.popleft()
        for next_y, next_x in ((y - 1, x), (y + 1, x), (y, x - 1), (y, x + 1)):
            if 0 <= next_y < height and 0 <= next_x < width:
                enqueue(next_y, next_x)
    return exterior


def _frame_image(atlas: Image.Image, index: int, width: int, height: int, columns: int) -> Image.Image:
    column = index % columns
    row = index // columns
    return atlas.crop((column * width, row * height, (column + 1) * width, (row + 1) * height))


def _allowed_hole_pixels(spec: dict[str, object], index: int, width: int, height: int) -> int:
    exceptions = spec.get("qualityExceptions", {})
    if not isinstance(exceptions, dict):
        return 0
    regions = exceptions.get("interiorAlphaRegions", [])
    if not isinstance(regions, list):
        return 0
    total = 0
    for region in regions:
        if not isinstance(region, dict) or int(region.get("frame", -1)) != index:
            continue
        bounds = region.get("bounds", [])
        if isinstance(bounds, list) and len(bounds) == 4:
            left, top, right, bottom = (max(0, int(value)) for value in bounds)
            total += max(0, min(width, right) - min(width, left)) * max(0, min(height, bottom) - min(height, top))
    return total


def _allowed_hole_mask(spec: dict[str, object], index: int, width: int, height: int) -> np.ndarray:
    mask = np.zeros((height, width), dtype=bool)
    exceptions = spec.get("qualityExceptions", {})
    if not isinstance(exceptions, dict):
        return mask
    regions = exceptions.get("interiorAlphaRegions", [])
    if not isinstance(regions, list):
        return mask
    for region in regions:
        if not isinstance(region, dict) or int(region.get("frame", -1)) != index:
            continue
        bounds = region.get("bounds", [])
        if not isinstance(bounds, list) or len(bounds) != 4:
            continue
        left, top, right, bottom = (int(value) for value in bounds)
        left, right = sorted((max(0, min(width, left)), max(0, min(width, right))))
        top, bottom = sorted((max(0, min(height, top)), max(0, min(height, bottom))))
        mask[top:bottom, left:right] = True
    return mask


def _exception_region_mask(spec: dict[str, object], key: str, index: int, width: int, height: int) -> np.ndarray:
    mask = np.zeros((height, width), dtype=bool)
    exceptions = spec.get("qualityExceptions", {})
    if not isinstance(exceptions, dict):
        return mask
    regions = exceptions.get(key, [])
    if not isinstance(regions, list):
        return mask
    for region in regions:
        if not isinstance(region, dict) or int(region.get("frame", -1)) != index:
            continue
        bounds = region.get("bounds", [])
        if not isinstance(bounds, list) or len(bounds) != 4:
            continue
        left, top, right, bottom = (int(value) for value in bounds)
        left, right = sorted((max(0, min(width, left)), max(0, min(width, right))))
        top, bottom = sorted((max(0, min(height, top)), max(0, min(height, bottom))))
        mask[top:bottom, left:right] = True
    return mask


def validate_atlas(atlas_path: Path, spec_path: Path, policy: AtlasPolicy = AtlasPolicy()) -> dict[str, object]:
    spec = load_spec(spec_path)
    configured_policy = spec.get("qualityPolicy", {})
    ignore_hole_below_y = None
    ignore_hole_frames: set[int] = set()
    ignore_detached_frames: set[int] = set()
    if isinstance(configured_policy, dict):
        raw_hole_y = configured_policy.get("ignoreInteriorAlphaBelowY")
        ignore_hole_below_y = int(raw_hole_y) if raw_hole_y is not None else None
        ignore_hole_frames = {int(value) for value in configured_policy.get("ignoreInteriorAlphaFrames", [])}
        ignore_detached_frames = {int(value) for value in configured_policy.get("ignoreDisconnectedFrames", [])}
        policy = AtlasPolicy(
            minimum_padding=int(configured_policy.get("minimumPadding", policy.minimum_padding)),
            maximum_interior_hole_pixels=int(configured_policy.get("maximumInteriorHolePixels", policy.maximum_interior_hole_pixels)),
            maximum_interior_hole_component=int(configured_policy.get("maximumInteriorHoleComponent", policy.maximum_interior_hole_component)),
            maximum_detached_component=int(configured_policy.get("maximumDetachedComponent", policy.maximum_detached_component)),
            maximum_shadow_pixels=int(configured_policy.get("maximumShadowPixels", policy.maximum_shadow_pixels)),
            maximum_light_shadow_pixels=int(configured_policy.get("maximumLightShadowPixels", policy.maximum_light_shadow_pixels)),
        )
    atlas = Image.open(atlas_path)
    if atlas.mode != "RGBA":
        raise ValueError(f"Atlas must be RGBA, got {atlas.mode}")
    width = int(spec["frameWidth"])
    height = int(spec["frameHeight"])
    columns = int(spec["columns"])
    rows = int(spec["rows"])
    count = int(spec["frameCount"])
    if atlas.size != (width * columns, height * rows):
        raise ValueError(f"Atlas size {atlas.size} does not match the manifest grid")
    frame_metadata = spec.get("frames", [])
    indices = [int(frame["index"]) for frame in frame_metadata if isinstance(frame, dict)]
    frame_names = {
        int(frame["index"]): str(frame.get("name", ""))
        for frame in frame_metadata
        if isinstance(frame, dict)
    }
    if indices != list(range(count)):
        raise ValueError("Frame metadata must contain every index exactly once")
    clips = spec.get("clips", [])
    clip_ids = [str(clip["id"]) for clip in clips if isinstance(clip, dict)]
    if len(clip_ids) != len(set(clip_ids)):
        raise ValueError("Clip ids must be unique")
    referenced = {
        int(frame)
        for clip in clips
        if isinstance(clip, dict)
        for frame in clip.get("frames", [])
    }
    if referenced != set(range(count)):
        raise ValueError(f"Clips do not cover all frames: missing={sorted(set(range(count)) - referenced)}")
    pivot = spec.get("pivot")
    if isinstance(pivot, dict):
        if not (0 <= int(pivot.get("x", -1)) <= width and 0 <= int(pivot.get("y", -1)) <= height):
            raise ValueError("Pivot is outside the frame")

    frames_report: list[dict[str, object]] = []
    digests: dict[str, int] = {}
    violations: list[str] = []
    for index in range(count):
        frame = _frame_image(atlas, index, width, height, columns)
        rgba = np.asarray(frame, dtype=np.uint8)
        alpha = rgba[:, :, 3]
        solid = alpha > 8
        bbox = frame.getchannel("A").getbbox()
        if bbox is None:
            violations.append(f"frame {index}: empty")
            continue
        margins = (bbox[0], bbox[1], width - bbox[2], height - bbox[3])
        if min(margins) < policy.minimum_padding:
            violations.append(f"frame {index}: padding={margins}")
        exterior = _border_connected_transparent(solid)
        holes = (~solid) & (~exterior)
        hole_components_raw = _components(holes)
        hole_components = []
        considered_holes = np.zeros_like(holes)
        allowed_region = _allowed_hole_mask(spec, index, width, height)
        for component in hole_components_raw:
            ys = [point[0] for point in component]
            xs = [point[1] for point in component]
            bbox_top = min(ys)
            if index in ignore_hole_frames or (ignore_hole_below_y is not None and bbox_top >= ignore_hole_below_y):
                continue
            if all(allowed_region[y, x] for y, x in component):
                continue
            hole_components.append(len(component))
            considered_holes[ys, xs] = True
        hole_components.sort(reverse=True)
        allowed = _allowed_hole_pixels(spec, index, width, height)
        hole_pixels = int(considered_holes.sum())
        largest_hole = hole_components[0] if hole_components else 0
        if hole_pixels > allowed + policy.maximum_interior_hole_pixels or largest_hole > policy.maximum_interior_hole_component:
            violations.append(f"frame {index}: interior-alpha={hole_pixels}, largest={largest_hole}")

        solid_components = _components(solid)
        solid_components.sort(key=len, reverse=True)
        components = [len(component) for component in solid_components]
        disconnected_region = _exception_region_mask(spec, "disconnectedRegions", index, width, height)
        detached = []
        for component in solid_components[1:]:
            if index in ignore_detached_frames or all(disconnected_region[y, x] for y, x in component):
                continue
            detached.append(len(component))
        largest_detached = detached[0] if detached else 0
        if largest_detached > policy.maximum_detached_component:
            violations.append(f"frame {index}: detached-component={largest_detached}")

        rgb = rgba[:, :, :3]
        hsv_like_saturation = rgb.max(axis=2).astype(np.int16) - rgb.min(axis=2).astype(np.int16)
        value = rgb.max(axis=2)
        lower_region = np.zeros_like(solid)
        lower_region[int(height * 0.72):, :] = True
        detached_mask = np.zeros_like(solid)
        for component in solid_components[1:]:
            for component_y, component_x in component:
                detached_mask[component_y, component_x] = True
        shadow_mask = detached_mask & lower_region & (hsv_like_saturation < 28) & (value < 225)
        shadow_pixels = int(shadow_mask.sum())
        if shadow_pixels > policy.maximum_shadow_pixels:
            violations.append(f"frame {index}: neutral-shadow={shadow_pixels}")

        # A white/near-white ground shadow is visually just as harmful as a
        # neutral gray one, but the old gate only looked for value < 225 and
        # therefore accepted the common white-source matte. Protect pixels
        # close to chromatic/dark character features (leg highlights and
        # white fur), then flag bright neutrals isolated in the contact band.
        from PIL import ImageFilter
        chromatic_anchor = ((alpha > 8) & ((hsv_like_saturation >= 50) | (value < 145))).astype(np.uint8) * 255
        protected = np.asarray(Image.fromarray(chromatic_anchor).filter(ImageFilter.MaxFilter(17))) > 0
        y_grid = np.indices(alpha.shape)[0]
        ground_frame = frame_names.get(index, "").startswith(("idle_", "walk_"))
        light_shadow_mask = (
            ground_frame
            &
            solid
            & (hsv_like_saturation < 50)
            & (value > 145)
            & (y_grid >= int(height * 0.80))
            & ~protected
        )
        light_shadow_pixels = int(light_shadow_mask.sum())
        if light_shadow_pixels > policy.maximum_light_shadow_pixels:
            violations.append(f"frame {index}: light-ground-shadow={light_shadow_pixels}")

        digest = hashlib.sha256(frame.tobytes()).hexdigest()
        duplicate = digests.get(digest)
        if duplicate is not None:
            violations.append(f"frames {duplicate} and {index}: exact duplicate")
        digests[digest] = index
        frames_report.append({
            "index": index,
            "bbox": list(bbox),
            "margins": list(margins),
            "interiorTransparentPixels": hole_pixels,
            "largestInteriorComponent": largest_hole,
            "detachedComponentPixels": largest_detached,
            "neutralShadowPixels": shadow_pixels,
            "lightGroundShadowPixels": light_shadow_pixels,
        })

    # Optional per-clip contact gates keep locomotion rooted even when every
    # individual frame passes the usual alpha/padding checks.  A source board
    # can otherwise contain a valid pose with a different camera crop, which
    # becomes a visible jump when the animation advances.
    contact_anchors = spec.get("contactAnchors", [])
    if isinstance(contact_anchors, list):
        report_by_index = {int(frame["index"]): frame for frame in frames_report}
        for anchor in contact_anchors:
            if not isinstance(anchor, dict):
                continue
            clip_id = str(anchor.get("clip", ""))
            clip = next((item for item in clips if isinstance(item, dict) and str(item.get("id")) == clip_id), None)
            if clip is None:
                violations.append(f"contact-anchor: unknown clip={clip_id}")
                continue
            clip_frames = [int(index) for index in clip.get("frames", []) if int(index) in report_by_index]
            if len(clip_frames) < 2:
                continue
            bottoms = [int(report_by_index[index]["bbox"][3]) for index in clip_frames]
            centers = [
                (int(report_by_index[index]["bbox"][0]) + int(report_by_index[index]["bbox"][2])) / 2
                for index in clip_frames
            ]
            bottom_tolerance = float(anchor.get("bottomTolerance", 1))
            center_tolerance = float(anchor.get("centerTolerance", 1))
            if max(bottoms) - min(bottoms) > bottom_tolerance:
                violations.append(
                    f"clip {clip_id}: contact-line-drift={max(bottoms) - min(bottoms)}"
                )
            if max(centers) - min(centers) > center_tolerance:
                violations.append(
                    f"clip {clip_id}: pivot-drift={max(centers) - min(centers):g}"
                )

    return {
        "atlas": str(atlas_path),
        "spec": str(spec_path),
        "frameCount": count,
        "frames": frames_report,
        "violations": violations,
        "passed": not violations,
    }


def _background(size: tuple[int, int], kind: str) -> Image.Image:
    if kind == "checker":
        result = Image.new("RGBA", size, (232, 232, 238, 255))
        draw = ImageDraw.Draw(result)
        tile = max(8, size[0] // 24)
        for y in range(0, size[1], tile):
            for x in range(0, size[0], tile):
                if (x // tile + y // tile) % 2:
                    draw.rectangle((x, y, x + tile, y + tile), fill=(198, 200, 210, 255))
        return result
    colors = {"white": (255, 255, 255, 255), "black": (8, 8, 12, 255), "magenta": (255, 0, 180, 255)}
    return Image.new("RGBA", size, colors[kind])


def write_previews(atlas_path: Path, spec_path: Path, output_dir: Path) -> None:
    spec = load_spec(spec_path)
    atlas = Image.open(atlas_path).convert("RGBA")
    width = int(spec["frameWidth"])
    height = int(spec["frameHeight"])
    columns = int(spec["columns"])
    count = int(spec["frameCount"])
    output_dir.mkdir(parents=True, exist_ok=True)
    for kind in ("checker", "white", "black", "magenta"):
        sheet = Image.new("RGBA", (columns * width, ((count + columns - 1) // columns) * height), (0, 0, 0, 0))
        background = _background((width, height), kind)
        for index in range(count):
            frame = _frame_image(atlas, index, width, height, columns)
            sheet.alpha_composite(background, ((index % columns) * width, (index // columns) * height))
            sheet.alpha_composite(frame, ((index % columns) * width, (index // columns) * height))
        sheet.convert("RGB").save(output_dir / f"atlas_{kind}.png", optimize=True)
    for clip in spec.get("clips", []):
        if not isinstance(clip, dict):
            continue
        frames = [
            _frame_image(atlas, int(index), width, height, columns).convert("RGBA")
            for index in clip.get("frames", [])
        ]
        if frames:
            frames[0].save(
                output_dir / f"clip_{clip['id']}.gif",
                save_all=True,
                append_images=frames[1:],
                duration=int(clip.get("frameDurationMs", 120)),
                loop=0 if clip.get("loop", True) else 1,
                disposal=2,
            )


def write_approval(report_path: Path, approval_path: Path, approver: str) -> None:
    report = json.loads(report_path.read_text(encoding="utf-8"))
    if not report.get("passed"):
        raise ValueError("Cannot approve an atlas with quality violations")
    atlas_digest = hashlib.sha256(Path(report["atlas"]).read_bytes()).hexdigest()
    spec_digest = hashlib.sha256(Path(report["spec"]).read_bytes()).hexdigest()
    approval_path.write_text(
        json.dumps(
            {
                "status": "approved",
                "approver": approver,
                "approvedAt": datetime.now(timezone.utc).isoformat(),
                "atlasSha256": atlas_digest,
                "specSha256": spec_digest,
                "report": str(report_path),
            },
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )


def promote(approval_path: Path, target_atlas: Path, target_spec: Path) -> None:
    approval = json.loads(approval_path.read_text(encoding="utf-8"))
    if approval.get("status") != "approved":
        raise ValueError("Approval file is not approved")
    report = json.loads(Path(approval["report"]).read_text(encoding="utf-8"))
    atlas = Path(report["atlas"])
    spec = Path(report["spec"])
    if hashlib.sha256(atlas.read_bytes()).hexdigest() != approval["atlasSha256"]:
        raise ValueError("Atlas changed after approval")
    if hashlib.sha256(spec.read_bytes()).hexdigest() != approval["specSha256"]:
        raise ValueError("Spec changed after approval")
    target_atlas.parent.mkdir(parents=True, exist_ok=True)
    target_spec.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(atlas, target_atlas)
    shutil.copy2(spec, target_spec)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)
    validate_parser = subparsers.add_parser("validate")
    validate_parser.add_argument("atlas", type=Path)
    validate_parser.add_argument("spec", type=Path)
    validate_parser.add_argument("--report", type=Path)
    preview_parser = subparsers.add_parser("preview")
    preview_parser.add_argument("atlas", type=Path)
    preview_parser.add_argument("spec", type=Path)
    preview_parser.add_argument("output", type=Path)
    approval_parser = subparsers.add_parser("approve")
    approval_parser.add_argument("report", type=Path)
    approval_parser.add_argument("approval", type=Path)
    approval_parser.add_argument("--approver", required=True)
    promote_parser = subparsers.add_parser("promote")
    promote_parser.add_argument("approval", type=Path)
    promote_parser.add_argument("atlas", type=Path)
    promote_parser.add_argument("spec", type=Path)
    args = parser.parse_args()
    if args.command == "validate":
        report = validate_atlas(args.atlas, args.spec)
        if args.report:
            args.report.parent.mkdir(parents=True, exist_ok=True)
            args.report.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        print(json.dumps(report, indent=2))
        return 0 if report["passed"] else 1
    if args.command == "preview":
        write_previews(args.atlas, args.spec, args.output)
        return 0
    if args.command == "approve":
        write_approval(args.report, args.approval, args.approver)
        return 0
    promote(args.approval, args.atlas, args.spec)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
