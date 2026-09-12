# Recovering an unrecognised saved expedition

Both the repository and Adventures UI returned early for an unknown destination. This left the trip occupying its single slot with no visible cancellation route and kept the associated pet's care paused. Unknown pet IDs could also reach reward resolution for a known destination.

- Claims require a recognised destination and pet. Cancellation still validates the request ID, but can remove an unknown trip without rewards.
- Care/illness timestamps for a recognised pet are restored by the existing paused-time handling. Cancellation never adds a treasure, decoration or expedition journal reward.
- Adventures shows a localized explanation and a working cancel button for an unknown destination or pet, instead of an empty card.

Validation: final debug/instrumentation builds, JVM suite and lint passed (16 seconds). CompanionRepositoryTest plus UnknownExpeditionUiTest passed 14 tests on disposable emulator-5580 in 3.176 seconds. New cases cover unknown destination after repository recreation/eight days away, unknown pet claims, repeat cancellation, subsequent travel, and cancellation through the actual Adventures button. Existing cases cover normal single claims, clock/reboot bounds and paused care. This is not a physical reboot/process-death test or comprehensive hostile-device clock protection. The full Android suite was not repeated for this revision.
