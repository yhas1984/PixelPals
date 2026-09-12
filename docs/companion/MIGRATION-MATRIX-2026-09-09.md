# Historical schema preservation matrix

`CompanionMigrationTest.everyExportedLegacySchemaPreservesRecordsAcrossMigrationAndReopen` now builds fixtures from each exported historical Room schema, versions 2 through 9, migrates to version 10, closes the database and opens it again. Each seeded table's original columns and rows are compared before migration and after both openings. The test uses dedicated database names on an isolated emulator and deletes only those fixtures.

Coverage includes all 15 pet status/bond rows, a wallet, two owned products, treasure inventory including a zero-count previously collected treasure, and—where the source schema supports them—granted/pending purchase-ledger rows, a rewarded collection milestone, a named home with Unicode, decoration placement/inventory, a journal event and an ongoing expedition. Nullable ledger timestamps are included in the comparisons. Before v8, the newly added `totalFound` is additionally checked against the original inventory count.

The existing v1 fixture was also executed because no exported v1 JSON is present in this checkout. That fixture verifies preservation of its treasure row and creation of later tables. Its coverage is narrower than the v2–v9 record matrix. Other existing v2, v7, v8 and v9 focused cases ran alongside it.

Results on emulator 5580:
- **6 Android tests passed in 0.784 seconds**, including the eight-version matrix with two openings per database.
- Test APK assembly, debug lint and whitespace checks passed.
- Production migration registration and migration implementations were inspected and match the chain exercised by the matrix. No migration implementation change was needed.

Evidence: `evidence/migration-matrix/android-tests.txt` and `build.txt`.

This proves the seeded historical cases for the current migration chain. It is not a claim about every possible user database, physical interruption during an upgrade, or Google Play billing fulfillment/restoration. Those broader acceptance requirements remain separate.
