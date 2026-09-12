# Manual purchase restoration and integrated validation

The store now offers a visible Restore purchases action, serialized with purchase/equipment operations. Reconciliation runs in the ViewModel, survives view recreation, refreshes the local catalog and wallet, reports restored count or an empty result, and offers a retry after failure. Automatic reconciliation remains once per ViewModel. Internal billing errors are not exposed in restoration messages. English and Spanish strings are provided.

The real API 26 store review exposed unsupported coin glyphs in prices. Display strings now use the same money-bag symbol as the wallet. Persisted treasure identifiers and emoji are unchanged. `evidence/store-restore-current/store-final.png` shows the final store with visible prices, restoration, privacy controls and banner.

## Checks

- Full JVM suite: 252 tests, zero failures/errors/skips. Debug app/test assembly and lint passed in 1m10s.
- Full Android suite: 212 tests passed in 81.478s on emulator-5580. This includes manual restoration/result retention after recreation, buying a cosmetic for the selected pet, migrations, expeditions, home, care and motion tests.
- The earlier full Android run had one failure: a legacy perimeter test still required climbing in 3.6–5.2 seconds. It now checks the deliberate minimum and size-relative peak speed instead of forcing a long wall to be crossed too quickly. Full-distance traversal tests remain in the suite.
- Production assets: 280 frames across 15 species passed the existing validator. Care/tool tests: 17 passed.
- After the final display-string-only change, app/test assembly and lint passed in 49s; four targeted Android store/localization tests passed in 6.69s and the rendered store was inspected. The 212-test full run predates that string-only change; both artifact hashes are retained.

Logs, before/after screenshots and APK hashes are in `evidence/store-restore-current/`. All mutations in test suites used the disposable emulator, not the physical user's database.

## Remaining acceptance work

This does not prove live Play billing/restoration: the UI test uses debug billing and the JVM tests use fakes. A Play test-track build is still required for localized pricing, pending/cancelled purchases, acknowledgement and restoration. Complete visual acceptance of all pets/frames, care transitions, shop variants, physical desktop interaction and battery/performance remains open. No production promotion is authorized by these checks.
