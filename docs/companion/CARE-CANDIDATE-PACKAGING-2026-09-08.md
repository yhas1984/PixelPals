# Care candidate packaging

Fixed the opt-in release candidate enabling care without shipping its atlas files.
Moved 15 PNG/JSON pairs unchanged into src/carePreview/assets; debug always includes
them, release includes them only with pixelpals.companion.releaseCandidate=true.
Updated the generator and its tests. Experimental debug home/contact art stays excluded.

Validation on 2026-09-08:
- Eight Python care asset tests passed.
- mergeDebugAssets, mergeReleaseAssets and lintRelease passed.
- Normal release contained zero care packs; debug contained all 15.
- Candidate mergeReleaseAssets passed; all 30 files matched source bytes and all
  atlasPath references resolved. Debug companion/review and companion/pets absent.
- Switching back to normal release removed every care pack from merged assets.
- git diff --check passed.

Full signed release packaging remains unverified: the preceding assembleRelease
failed because signingConfig release lacked storeFile in this environment.
No publication or visual promotion performed. These checks cover packaging, not
physical animation acceptance or Play purchase/restore validation.
