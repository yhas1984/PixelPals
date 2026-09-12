# Corgi contact study — 2026-09-08

Status: experimental comparison in the debug atlas laboratory. These assets do
not replace the active desktop or home walk cycle, and are not approved for release.

The original four walking frames remain available alongside eight candidate
poses in `PetAtlasLabActivity`. Launch with `--es asset corgi_original` or
`--es asset corgi_contacts_v4`, and use Atlas mode. Pause, Step and Mirror allow
inspection of individual poses. Runtime mode is not supported for these studies.
The study's Overlay action cannot switch the real desktop pet.

## Source and reproducibility

The candidate board was generated with the original `corgi_10.png` as the visual
reference, then edited to replace a painted checkerboard with a cyan extraction
background. The selected source is preserved in
`tools/companion/sources/corgi_walk_contacts_v4.png`.

Run `python3 tools/companion/build_corgi_contact_review.py` to extract and pack the
review assets. The pipeline uses a single scale for all eight poses, registers
their bottom contact at y=368, removes the cyan matte and checks for duplicate
poses, clipping and residual matte. Registration translates frames; it does not
resize each pose to its individual visible bounds. Each candidate frame lasts
160 ms; the four originals use 320 ms for the same total cycle duration.

## Evidence and limits

- Debug assembly and debug lint passed; final asset packing and assembly passed.
- The production asset validator passed for all 280 frames across 15 pets.
- Candidate and original comparisons rendered in the emulator. The candidate
  screenshot is in `evidence/contact-v4/candidate-emulator.png`.
- The phone at `192.168.1.204:41701` became inaccessible (`No route to host`).
  The new comparison build was not installed there.

Structural checks and still images do not prove a natural gait. Review the full
loop, body proportions, foot sliding, both directions, start/stop and care entry
and exit before integrating the candidate. In particular, Corgi locomotion and
fetch currently share a four-frame assumption; both contracts must be updated
together if the eight-frame study is accepted. Other species and the remaining
full-plan acceptance work are still outstanding.
