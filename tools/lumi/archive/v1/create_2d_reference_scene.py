#!/usr/bin/env python3
"""Create Lumi's Blender 2D reference and rig-scaffold scene.

The supplied artwork is a single flattened illustration. This script embeds it
in a Blender file and sets up the square atlas camera, safe-area guides,
animation markers, and named anchor points needed before separating layers.
It deliberately does not pretend that a flattened image is already a rig.
"""

from __future__ import annotations

import json
import os
from pathlib import Path

import bpy


ROOT = Path(__file__).resolve().parents[4]
REFERENCE_PATH = Path(os.environ.get("LUMI_REFERENCE_PATH", "/home/yhas/Pictures/pixelpals_refs/lumi.png"))
OUTPUT_DIR = ROOT / "tools/lumi/archive/v1/blender_2d"
BLEND_PATH = OUTPUT_DIR / "lumi_2d_reference_v1.blend"
RENDER_PATH = OUTPUT_DIR / "lumi_reference_square_v1.png"
FRAME_SIZE = 384
SOURCE_WIDTH = 1536
SOURCE_HEIGHT = 1024
PLANE_WIDTH = 1.5
PLANE_HEIGHT = 1.0
CAMERA_ORTHO_SCALE = 1.10

FRAME_MARKERS = (
    "hover_neutral",
    "hover_bob_up",
    "hover_bob_down",
    "blink",
    "glance_left",
    "glance_right",
    "light_gather",
    "light_orb",
    "light_spark",
    "paw_wave",
    "paw_wave_hold",
    "paw_wave_return",
    "touch_reach",
    "drag_resist",
    "fling_tuck",
    "recover_hover",
)

ANCHORS = {
    "head": (695, 330),
    "chest_star": (672, 625),
    "tail_base": (890, 555),
    "tail_orb": (1140, 214),
    "front_paw_left": (618, 875),
    "front_paw_right": (772, 884),
    "rear_paw": (936, 850),
}


def clear_scene() -> None:
    bpy.ops.object.select_all(action="SELECT")
    bpy.ops.object.delete(use_global=False)
    for collection in list(bpy.data.collections):
        if collection.name != "Collection" and collection.users == 0:
            bpy.data.collections.remove(collection)
    for datablocks in (bpy.data.meshes, bpy.data.materials, bpy.data.cameras, bpy.data.lights, bpy.data.armatures):
        for datablock in list(datablocks):
            if datablock.users == 0:
                datablocks.remove(datablock)


def collection(name: str) -> bpy.types.Collection:
    result = bpy.data.collections.new(name)
    bpy.context.scene.collection.children.link(result)
    return result


def link_object(obj: bpy.types.Object, target: bpy.types.Collection) -> bpy.types.Object:
    for current in list(obj.users_collection):
        current.objects.unlink(obj)
    target.objects.link(obj)
    return obj


def create_reference_material(image: bpy.types.Image) -> bpy.types.Material:
    material = bpy.data.materials.new("Lumi Reference | packed 2D artwork")
    material.use_nodes = True
    nodes = material.node_tree.nodes
    links = material.node_tree.links
    nodes.clear()

    output = nodes.new("ShaderNodeOutputMaterial")
    output.location = (460, 0)
    transparent = nodes.new("ShaderNodeBsdfTransparent")
    transparent.location = (0, -120)
    emission = nodes.new("ShaderNodeEmission")
    emission.location = (0, 120)
    emission.inputs["Strength"].default_value = 1.0
    texture = nodes.new("ShaderNodeTexImage")
    texture.location = (-260, 100)
    texture.image = image
    texture.interpolation = "Linear"
    texture.extension = "CLIP"
    mix = nodes.new("ShaderNodeMixShader")
    mix.location = (240, 0)

    links.new(texture.outputs["Color"], emission.inputs["Color"])
    links.new(texture.outputs["Alpha"], mix.inputs[0])
    links.new(transparent.outputs[0], mix.inputs[1])
    links.new(emission.outputs[0], mix.inputs[2])
    links.new(mix.outputs[0], output.inputs[0])

    if hasattr(material, "surface_render_method"):
        material.surface_render_method = "DITHERED"
    return material


def create_reference_plane(image: bpy.types.Image, target: bpy.types.Collection) -> bpy.types.Object:
    vertices = (
        (-PLANE_WIDTH / 2, -PLANE_HEIGHT / 2, 0.0),
        (PLANE_WIDTH / 2, -PLANE_HEIGHT / 2, 0.0),
        (PLANE_WIDTH / 2, PLANE_HEIGHT / 2, 0.0),
        (-PLANE_WIDTH / 2, PLANE_HEIGHT / 2, 0.0),
    )
    mesh = bpy.data.meshes.new("Lumi_Reference_Plane_Mesh")
    mesh.from_pydata(vertices, [], ((0, 1, 2, 3),))
    mesh.update()
    uv_layer = mesh.uv_layers.new(name="LumiReferenceUV")
    # Blender's image texture uses the lower-left UV origin; this keeps the
    # artwork upright when viewed by the camera above the plane.
    uv_coordinates = ((0.0, 0.0), (1.0, 0.0), (1.0, 1.0), (0.0, 1.0))
    for loop, uv in zip(mesh.loops, uv_coordinates):
        uv_layer.data[loop.index].uv = uv

    obj = bpy.data.objects.new("Lumi_Master_Reference", mesh)
    target.objects.link(obj)
    mesh.materials.append(create_reference_material(image))
    obj["source_file"] = str(REFERENCE_PATH)
    obj["source_size"] = f"{SOURCE_WIDTH}x{SOURCE_HEIGHT}"
    obj["source_is_flattened"] = True
    obj["requires_layer_separation"] = True
    return obj


def source_to_plane(pixel_x: float, pixel_y: float) -> tuple[float, float, float]:
    return (
        (pixel_x / SOURCE_WIDTH - 0.5) * PLANE_WIDTH,
        (0.5 - pixel_y / SOURCE_HEIGHT) * PLANE_HEIGHT,
        0.04,
    )


def create_anchor(name: str, pixel: tuple[int, int], target: bpy.types.Collection) -> bpy.types.Object:
    obj = bpy.data.objects.new(f"Anchor_{name}", None)
    target.objects.link(obj)
    obj.empty_display_type = "CIRCLE"
    obj.empty_display_size = 0.035
    obj.color = (0.05, 0.9, 0.85, 1.0)
    obj.location = source_to_plane(*pixel)
    obj["source_pixel_x"] = pixel[0]
    obj["source_pixel_y"] = pixel[1]
    obj.hide_render = True
    return obj


def create_safe_frame(target: bpy.types.Collection) -> bpy.types.Object:
    curve = bpy.data.curves.new("Lumi_Atlas_Safe_Frame", type="CURVE")
    curve.dimensions = "3D"
    curve.bevel_depth = 0.004
    curve.bevel_resolution = 2
    spline = curve.splines.new("POLY")
    spline.points.add(3)
    points = (
        (-0.5, -0.5, 0.08, 1.0),
        (0.5, -0.5, 0.08, 1.0),
        (0.5, 0.5, 0.08, 1.0),
        (-0.5, 0.5, 0.08, 1.0),
    )
    for point, coordinate in zip(spline.points, points):
        point.co = coordinate
    spline.use_cyclic_u = True
    obj = bpy.data.objects.new("Guide_384px_Atlas_Cell", curve)
    target.objects.link(obj)
    obj.color = (0.1, 1.0, 0.85, 1.0)
    obj.hide_render = True
    obj["cell_size"] = FRAME_SIZE
    obj["safe_padding_px"] = 16
    return obj


def create_rig_scaffold(target: bpy.types.Collection) -> bpy.types.Object:
    armature_data = bpy.data.armatures.new("Lumi_2D_Rig_Scaffold_Data")
    armature = bpy.data.objects.new("Lumi_2D_Rig_Scaffold", armature_data)
    target.objects.link(armature)
    armature.show_in_front = True
    armature.display_type = "WIRE"
    armature.hide_render = True
    bpy.context.view_layer.objects.active = armature
    armature.select_set(True)
    bpy.ops.object.mode_set(mode="EDIT")
    bones = {
        "root": ((0.0, -0.35, 0.1), (0.0, 0.1, 0.1), None),
        "head": ((-0.05, 0.12, 0.1), (-0.05, 0.38, 0.1), "root"),
        "tail": ((0.15, -0.02, 0.1), (0.35, 0.2, 0.1), "root"),
        "tail_orb": ((0.35, 0.2, 0.1), (0.47, 0.35, 0.1), "tail"),
        "paw_left": ((-0.16, -0.37, 0.1), (-0.16, -0.18, 0.1), "root"),
        "paw_right": ((0.06, -0.38, 0.1), (0.06, -0.18, 0.1), "root"),
    }
    for name, (head, tail, parent_name) in bones.items():
        bone = armature_data.edit_bones.new(name)
        bone.head = head
        bone.tail = tail
        if parent_name:
            bone.parent = armature_data.edit_bones[parent_name]
    bpy.ops.object.mode_set(mode="OBJECT")
    armature["status"] = "scaffold_only"
    armature["bound_to_artwork"] = False
    armature["next_step"] = "separate or repaint occluded layers before binding"
    return armature


def create_camera(scene: bpy.types.Scene) -> bpy.types.Object:
    camera_data = bpy.data.cameras.new("Lumi_2D_Atlas_Camera")
    camera_data.type = "ORTHO"
    camera_data.ortho_scale = CAMERA_ORTHO_SCALE
    camera = bpy.data.objects.new("Lumi_2D_Atlas_Camera", camera_data)
    scene.collection.objects.link(camera)
    camera.location = (0.0, 0.0, 10.0)
    scene.camera = camera
    return camera


def configure_scene(scene: bpy.types.Scene) -> None:
    scene.render.engine = "BLENDER_EEVEE"
    scene.render.resolution_x = FRAME_SIZE
    scene.render.resolution_y = FRAME_SIZE
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = "PNG"
    scene.render.image_settings.color_mode = "RGBA"
    scene.render.film_transparent = True
    scene.render.filepath = str(RENDER_PATH)
    scene.render.fps = 12
    scene.frame_start = 1
    scene.frame_end = len(FRAME_MARKERS)
    scene.frame_set(1)
    scene.view_settings.view_transform = "Standard"
    scene.view_settings.look = "None"
    scene.view_settings.exposure = 0.0
    scene.view_settings.gamma = 1.0
    scene["pipeline"] = "PixelPals Blender 2D reference scaffold"
    scene["source_reference"] = str(REFERENCE_PATH)
    scene["source_reference_is_packed"] = True
    scene["atlas_target"] = "384x384 transparent cells"
    scene["stage"] = "reference_and_rig_scaffold"
    scene["blocking_note"] = "Flattened source must be separated or repainted for independent poses"
    for frame, marker in enumerate(FRAME_MARKERS, start=1):
        scene.timeline_markers.new(marker, frame=frame)


def main() -> None:
    if not REFERENCE_PATH.exists():
        raise FileNotFoundError(f"Lumi reference not found: {REFERENCE_PATH}")
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    clear_scene()
    scene = bpy.context.scene
    configure_scene(scene)
    reference_collection = collection("LUMI_REFERENCE_ART")
    guide_collection = collection("LUMI_RIG_GUIDES")

    image = bpy.data.images.load(str(REFERENCE_PATH), check_existing=False)
    image.name = "Lumi_Master_Reference_Packed"
    image.pack()
    reference_plane = create_reference_plane(image, reference_collection)
    reference_plane.parent = None
    create_camera(scene)
    create_safe_frame(guide_collection)
    for name, pixel in ANCHORS.items():
        create_anchor(name, pixel, guide_collection)
    create_rig_scaffold(guide_collection)

    bpy.ops.wm.save_as_mainfile(filepath=str(BLEND_PATH))
    scene.render.filepath = str(RENDER_PATH)
    bpy.ops.render.render(write_still=True)
    print(f"LUMI_2D_SCENE_READY blend={BLEND_PATH} render={RENDER_PATH}")
    print(f"LUMI_REFERENCE_PACKED name={image.name} size={image.size[0]}x{image.size[1]}")
    print(f"LUMI_MARKERS={json.dumps(list(FRAME_MARKERS))}")


if __name__ == "__main__":
    main()
