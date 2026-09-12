# Candidate build checks — 2026-09-09

The current tree passed `:app:compileReleaseKotlin :app:mergeReleaseAssets :app:lintRelease` with `-Ppixelpals.companion.releaseCandidate=true` (57 seconds). This is compilation, packaging-input and lint evidence, not a signed release or visual approval.

`evidence/current-release-candidate/candidate-assets.json` records 30 matching care-preview files, resolving atlas paths and exclusion of experimental home art. Afterward, ordinary `:app:mergeReleaseAssets :app:generateReleaseBuildConfig` passed with no candidate flag: `CARE_SCENES_ENABLED=false`, with no care_v1 or experimental companion art in merged release assets.

No signed APK/AAB was produced. The signing variables/properties inspected in this environment were absent. An attempted `:app:validateSigningRelease` command failed because that task does not exist; it is not evidence of a signing validation failure. Signed packaging remains unverified.

Physical review of all 15 pets, extended performance testing, final artwork acceptance, and real Play-track purchases/restoration remain outstanding. Production publication is not authorized by these checks.
