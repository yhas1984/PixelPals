# Audit corrections — 2026-09-06

The historical findings in AUDIT-2026-09-05.md now have these implementation changes on the living-companion branch. This checkpoint does not certify the full product plan or production release acceptance.

| Finding | Correction | Evidence |
| --- | --- | --- |
| A1 | Status read/reconcile/write and active-minute updates share Room transactions with care. | Concurrent refresh/feeding regression passes. |
| A2 | Idempotent local fulfillment happens before Play consumption, with the consumed marker applied after success. Production code uses the tested settlement function. | JVM failure-injection tests cover interruption after grant, retry without duplicate balance, and no consumption after a failed grant. Real Play-track validation remains pending. |
| A3 | Expedition return/cancellation also shift interaction, care and medicine timestamps, preserving the paused intervals. | Eight-day return regression passes. |
| A4 | Room movement tracks both coordinates, depth and facing; approaches reuse shipped locomotion frames, including Corgi and Taro. | Rear-row arrival and bounded-movement tests pass; all 15 home loaders/render checks pass. Final artistic review of transitions remains pending. |
| A5 | Failed queries terminate only their attempt; refresh/selection starts new world and journal observers. | Injected failed-query/retry test passes. |
| A6 | Real completed object care learns a favorite through persisted observations. Bounded traits from play, touch, feeding, species curiosity and bond are shared with desktop timing. Journal and route cards have vector illustrations. | Learned-favorite integration test passes. These additions do not replace final review of the personality/art direction. |
| A7 | Missing emoji glyphs use a vector treasure symbol instead of a replacement rectangle. | Glyph check and fallback reviewed; asset/home rendering suite passes. |

Final local checks: **194 JVM tests passed; 115 Android instrumentation tests passed (64.585 seconds); debug build and lint passed.** Instrumentation ran only on the disposable API 26 emulator. No database reset or destructive tests were run on the user's phone. The 115 passing tests include both previously failing audit regressions.

No new dependencies, database schema changes, real-money products or production publication were introduced. Physical soak, comprehensive all-species visual approval and actual Play purchase/restoration validation remain external acceptance work.
