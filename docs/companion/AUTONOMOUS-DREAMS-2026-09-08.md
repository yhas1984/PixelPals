# Autonomous desktop dreams — 2026-09-08

PetBehavior now exposes actual autonomous sleep through `isSleeping`, defaulting
to false. Ginger, Nube Michi, Piru, Menta and Tela report their explicit sleeping
mode; Lumi reports its controller's sleep mode. RuntimePetBehavior derives it
from the emitted SLEEP intent, covering the active Yuki and Taro adapters. The
legacy Yuki adapter also implements the contract.

PetView advances a separate sleep timer and draws the shared thought cloud at
the current rendered ground anchor. It requires IDLE plus actual sleep, and
does not draw during drag, interaction or care playback. Care retains its own
dream rendering. Reduced motion keeps the cloud static.

Corgi's REST mode alternates resting/blinking art, so it is deliberately not
reported as sleep. Other controllers without an explicit sleep mode retain the
false default. This change does not invent missing sleeping or waking artwork.

Validation: debug and instrumentation assembly passed. Five Android tests passed
on emulator-5580 (1.735 seconds), checking all modes of the five legacy sleep
controllers, Corgi's non-sleep rest, dream bounds/reduced motion and desktop care
continuity. The new state test does not exercise every Runtime or Lumi sleep
transition; physical visual review remains outstanding.
