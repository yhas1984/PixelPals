# Lumi Behavior Lab

This is an art and character-behavior prototype. It does not depend on Android
or `PetService`.

Open `tools/lumi/archive/v1/behavior_lab.html` from a local web server:

```bash
python3 -m http.server 8765 --directory tools/lumi
```

Then open `http://127.0.0.1:8765/behavior_lab.html`.

The lab models Lumi as a baby fox rather than a looping sprite:

`observe -> investigate -> toddle -> stalk -> pounce -> celebrate -> social -> settle -> yawn -> sleep`

It uses the available atlas as key poses. Because the current sheet has only one
true walking pose and no dedicated landing pose, movement is intentionally made
of short prance bursts and protected action beats. The missing walk/landing
frames are art gaps, not something the behavior code should hide.
