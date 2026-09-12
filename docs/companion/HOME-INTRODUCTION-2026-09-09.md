# First opening versus returning users

The home used to mark its introduction seen before confirmation, identify returning users only from a selection timestamp, and open the adoption/name dialog for everyone. It also offered the flow before the home data had loaded.

The introduction now waits for the selected home's data. A valid saved selection (including legacy selections without timestamps), existing adoption date or nickname selects the returning-user welcome. Confirming that welcome enters the home without asking for a name. A new user confirms the introduction and then receives the naming dialog. The introduction flag is saved only on confirmation. The dialog is dismissed with its view and re-offered after recreation if it was not acknowledged; repeated world emissions do not stack dialogs.

## Validation

- Debug and Android-test APK builds, 233 JVM tests and lint passed.
- Full Android suite on disposable API 26 emulator-5580 with overlay permission: **163 tests passed**, 64.431 seconds. Captured output: `evidence/introduction/android-suite.txt`.
- New UI tests exercise legacy selection without timestamp and new-user introduction through activity recreation, including the transition to naming and absence of naming for the returning user.
- The first run had two test synchronization failures: Android queues the dialog button listener, while the tests checked preferences immediately. Tests now wait for the UI queue; the entire suite was rerun successfully.
- Debug APK installed on the user's phone. Onboarding fixtures ran only on the disposable emulator; the phone's adoption/progress/preferences were not reset.

This is not complete onboarding acceptance: preservation of an in-progress typed nickname across recreation, the guided first interaction, full permission/revocation coverage and broader visual/performance/release acceptance remain open.
