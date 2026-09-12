# Jelly: sleep expressions

Six facial expressions generated with the built-in image_gen tool, using reference.png and the exact prompt in prompt.txt. The generated.png returned an opaque green background instead of usable transparency; its backdrop and six body silhouettes are not packaged.

prepare_rest.py extracts only the authored eyes and mouths, with one shared feature scale and fixed eye/mouth centers. It reconstructs the small facial regions of the existing medicine_0 body using local harmonic interpolation, then places the new features. All six PNGs retain the same original alpha channel, outside-face RGB, body proportions, reflections and material. Generated bright backgrounds cannot enter the app through this composition.

Final resources: tools/jelly/clean/rest_0.png through rest_5.png. prepared-review.png shows awake20 followed by the six expressions. Alpha is byte-identical across them. Eyes gradually narrow and end in relaxed closed curves. Body deformation is continuous, anchored to the ground, and retains 2D area; it is not baked into six inconsistent body silhouettes.

Only REST uses appended care cells24..29. The preceding24care cells and legacy assets are preserved. REST completion/rewards retain their original timing. The extended atlas remains in the existing carePreview build route, pending the full release art review.
