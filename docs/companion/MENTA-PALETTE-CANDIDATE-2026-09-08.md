# Menta palette candidate — not integrated

Used the built-in image generation/editing tool to explore a softer mint palette
matching the original motion atlas. Input 1 was the care atlas; input 2 was the
original motion atlas. The first output was RGB with a baked checkerboard and was
rejected. A background-extraction follow-up produced a 1024x1536 RGBA PNG, saved as
`evidence/art-candidates/menta-palette-v2-unapproved.png`.

The second file has 981,984 fully transparent pixels out of 1,572,864. It still
fails the existing care validator's exact-zero 15-pixel cell padding requirement:
all 24 cells contain nonzero border alpha (36–272 border samples per cell). Sampled
border maxima for frames 0/16/17/18/19/22 are only 2–4, below the home renderer's
visibility threshold, but this is still not a clean production atlas. No original
asset was replaced, no app code references the candidate, and no APK was installed.
The original-vs-care palette issue remains open. No CLI/API fallback was used.

## Exact initial prompt

Use case: identity-preserve. Asset type: Android pet sprite atlas. Image 1 is the EDIT TARGET: a 1024x1536 transparent sprite sheet, 4 columns by 6 rows of 256x256 cells, containing 24 poses of Menta the mint-green snake. Image 2 is ONLY a COLOR AND IDENTITY REFERENCE: the original Menta atlas. Edit image 1 ONLY to match the softer, less saturated, muted mint and cream palette of image 2. Preserve every pose, every face and eye state, the exact silhouette and placement of all 24 frames, all scale details and the heart marking, all anatomy, all padding and cell alignment. Do not replace the poses with those of image 2. Keep the sleeping coiled poses in row 5 and keep their eyes closed. Do not add/remove any object or pose. Keep output 1024x1536 with the same 4x6 grid and genuinely transparent alpha outside the sprites, no black/white/checkerboard backdrop baked in. No text or gridlines. Reduce the neon yellow-green saturation and use the subtle pale sage/mint greens and soft cream of image 2 while retaining clean edge detail. This is a palette-only correction, not a redesign.

## Exact extraction prompt

Use case: background-extraction. This is a 1024x1536 sprite atlas with 24 separate snake sprites in a 4-column, 6-row grid. Remove ONLY the baked gray checkerboard backdrop. Output a PNG WITH AN ACTUAL ALPHA CHANNEL: every background pixel between and around the sprites must be transparent (alpha zero), not a picture of transparency. Preserve all 24 sprites, exact positions, colors, expressions, outlines, and dimensions. Preserve the same canvas size and cell boundaries. Do not redraw the sprites. No opaque backdrop of any color and NO checkerboard pattern. Remove background checkerboard even in holes between snake coils. This is a technical cutout for an Android app, where any opaque background would cover the user's desktop.
