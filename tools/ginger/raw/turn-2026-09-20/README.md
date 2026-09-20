# Ginger: planted turn supplement

Source drawings generated with the built-in imagegen editor on 2026-09-20,
using frame 18 of `ginger_rest_v2.png` as the visual reference. The brief
requested two standing poses (three-quarter left and frontal), preserving
Ginger's adult orange coat, green eyes, teal collar, gold bell, drawing style,
and grounded paws, on genuine transparency. The frontal tail was subsequently
edited to pass behind the body rather than switch sides abruptly at the turn.

- `generated.png`: original two-pose output (`exec-ed98cd69-8038-4470-978a-f2126c822d11`).
- `front-tail.png`: frontal tail edit (`exec-4a9ffe3c-ae10-4042-91a6-bcd213f7e4ec`).
- Preparation: `../../build_turn_atlas.py`. Uses one uniform 0.365 camera scale
  for both new poses, translates their paw contacts to y=368, removes the
  isolated frontal tail tip above the forehead, and appends cells 22/23.
- The builder asserts pixel equality for every original cell 0–21.
- Review board: `../../review/turn-contact.png`.

The shared motion timeline uses the original standing profile, the new
three-quarter view, frontal view, and the mirrored return to standing. These
drawings cover a planted heading change; they do not replace the care atlas.
