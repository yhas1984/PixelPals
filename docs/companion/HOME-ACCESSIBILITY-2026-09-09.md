# Home action layout and interaction continuity

The paired home actions now stack vertically when the available width, font scale and child margins cannot accommodate readable columns. The layout returns to columns when space is available. Gone children do not reserve a cell, and unconstrained width uses intrinsic vertical measurement instead of zero-width weighted children. This applies to decoration controls, decorate/share and rename/settings. Existing callbacks and localized labels are retained.

Adventures no longer destroys and recreates travel buttons for every countdown refresh. A render key distinguishes journey identity, known/unknown data and the ready-to-return state; while those remain unchanged, the same controls stay in place. The countdown text is assigned only when its localized value changes. Callbacks use the current expedition. Unknown-data cancellation and state transitions still work.

The pet-name dialog reapplies its saving state in `onStart`. A same-fragment detach/attach can recreate its view while its lifecycle coroutine and saving flag remain active; the new controls now stay disabled during that operation. Saving is not blindly persisted across process/activity recreation, where the old coroutine is cancelled.

Validation:
- Debug app/test assembly and debug lint passed.
- **15 Android tests passed in 13.077 seconds**: adaptive rows, stable travel rendering/focus, unknown expedition recovery, name-dialog view recreation, introduction flows and decoration placement.
- The action-row test uses Spanish and English with font scale 2.0, a 280dp available width, full label/ellipsis checks, minimum touch height, clicks, resizing, hidden children, margin boundary and unconstrained measurement.
- Native enlarged-text action screenshots were inspected and saved in `evidence/adaptive-actions/home-action-rows/`.
- A 1px difference between equally weighted columns is allowed in the regression because pixel rounding legitimately distributes an odd pixel between them.
- No physical-user app data was used for the activity fixtures; those run on emulator 5580. This is not a full accessibility/TalkBack or all-screen visual certification.

Final logs: `evidence/adaptive-actions/build.txt` and `android-tests.txt`.
