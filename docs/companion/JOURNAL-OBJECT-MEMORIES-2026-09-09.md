# Recover object memories in the journal

Object interactions already persisted for learned preferences, but `observeJournal` excluded them and Adventures had no presentation branch for them. They now appear using the most recent event per object and local calendar day. Existing adoption, expedition and care events remain in the same date-ordered 100-entry view. Raw events are retained, so preference learning and historical data are unchanged.

Titles are localized in English and Spanish. Illustrations reuse the actual decoration and the pet recorded in the event, including species-specific starter toys. Retired/unknown object IDs receive a readable generic title and illustration instead of exposing an internal ID or disappearing silently.

Validation: debug/test builds, 233 JVM tests and lint passed (55 seconds). Three targeted Android tests passed on the phone (0.319 seconds): isolated Room query with repeated/split-day/other-pet events, presentation and species illustration, and localization resources. The isolated database does not modify the phone's saved pets. A supplemental desktop SQLite check processed 10,000 synthetic object rows into five daily entries in 6.24 ms; this is not Android performance acceptance. Debug APK installed successfully.

No schema migration or data rewrite is required: the change reads existing events. Full app visual/runtime acceptance and the broader plan remain open; see SCOPE-CHECKPOINT-2026-09-09.md.
