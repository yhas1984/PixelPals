# Bloop art cleanup

The cleanup is an explicitly authorised art edit. The files under `tools/bloop/source/` remain the immutable originals and are never overwritten. `cleanup.py` receives one already placed 256x256 care cell and removes only the thin left appendage; it must preserve the cell size, body RGB, anchors, and scale. The generator calls it after resize and placement, so cleanup cannot change camera fitting or anchor coordinates.

`test_cleanup.py` compares the generated result with the packaged care atlas and checks that RGB bytes remain identical, alpha only decreases, the wire-free frames `[1, 2, 7, 18, 19]` are untouched, and the central body region `x=100..200, y=90..230` remains byte-identical. It also checks legacy Bloop sources against the packaged drawables: dimensions and RGB must match while only alpha may be reduced.

Run from the repository root with `python3 -m unittest tools.bloop.test_cleanup`. The test is a local Pillow check and does not build, install, or modify production assets.

`python3 tools/bloop/clean_legacy.py` reproduces the legacy candidates in `tools/bloop/review/` from the originals. After visual review, these candidates are copied to their corresponding drawable folders. No crop, rescale or RGB repaint is performed. `fantasma_8` keeps its broad ghost trail; `fantasma_4` keeps its vortex wisps while the flat base is removed.

The care pipeline (`tools/care/build_atlases.py`) calls the cleanup after atlas placement. Only the Bloop entry in `build_report.json` was refreshed; its care JSON, timings and contact anchors are unchanged. Home `BloopArtworkBounds` retains the original framing so removing visible pixels cannot enlarge or shift the remaining body. The raw sources and review boards are outside all packaged Android source sets.
