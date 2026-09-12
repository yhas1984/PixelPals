# Decoration gesture and picker ownership

- Changing the scene pet cancels its pending placement, releases parent interception and suppresses the release of the old gesture. A new touch still places normally.
- The placement callback captures the scene's pet before launching repository work and ignores a mismatch with the selected pet. Environment edits likewise retain the pet chosen when the dialog opened.
- Decoration titles and selection handlers use the same filtered inventory snapshot. Unknown legacy IDs can no longer shift a displayed label onto a different inventory entry, and later inventory emissions do not reorder an open picker.
- Touch placement and care callbacks from an old pet's decoration dialog cannot act on a newly selected pet.

Validation: debug/instrumentation builds, JVM suite and lint passed (31 seconds). Physical phone: DecorationTouchPlacementTest, PostcardExporterTest and HomeCorgiContinuityTest passed 8 tests in 1.442 seconds. The new gesture test changes Corgi to Ginger between DOWN and UP, verifies no placement/care callback, and verifies a subsequent yarn placement. These tests instantiate views and do not alter pet progress. Picker snapshot mapping and environment capture were source-reviewed; the full activity-level dialog/selection matrix was not rerun. Updated debug APK installed preserving data.
