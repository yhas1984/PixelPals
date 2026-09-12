# Postcard export without editing side effects

Sharing previously set `isEditing = false`, which cleared the pending placement and dragged object, invoked editing callbacks, and then restored only the boolean. It also retained the bitmap after writing and used millisecond filenames that could collide.

- HomeSceneView now has a postcard draw mode that suppresses the grid and drag preview while drawing committed decorations. It does not modify editing state or callbacks.
- Capture and share-intent creation run on the main dispatcher; PNG writing stays on IO. The bitmap is recycled in a finally block, including failure/cancellation paths after allocation.
- File creation uses unique temporary filenames, checks directory creation, and removes partially written output on write failure. Existing one-day cache cleanup remains.
- The share intent retains PNG MIME type, caption, content URI, ClipData and temporary read permission. A chooser launched with an application context receives NEW_TASK.

Tests export before and during placement of an existing bed and compare the readable PNGs, require unique URIs, verify intent metadata, and confirm that editing callbacks and pending objects are unchanged. A regular-file cache-directory fixture exercises storage failure without modifying real app progress. These tests prepare the share intent; they do not send anything or prove behavior inside third-party receiving apps.

Validation: final debug and instrumentation builds, JVM suite and lint passed (21 seconds). PostcardExporterTest, HomeCorgiContinuityTest and CompanionSceneTest passed all 11 tests on the physical phone in 16.155 seconds. Updated debug APK installed preserving data. The full Android suite was not rerun after this export change.
