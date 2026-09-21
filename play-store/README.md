# PixelPals Google Play listing

This directory is the versioned source of truth for the `es-419`, `es-ES`, and
`en-US` Google Play listings. The two Spanish listings currently share the
same visual assets and use independently localized metadata.

- `metadata.json` contains titles, short descriptions and asset paths.
- `listing/` contains the full descriptions uploaded to Google Play.
- `release-notes/` contains the localized 2.5.2 internal-track notes.
- `assets/icon/` contains the 512 px store icon.
- `assets/feature-graphic/` contains localized 1024×500 graphics.
- `assets/screenshots/` contains eight localized 1080×1920 screenshots per language.
- `assets/screenshots-tablet-7/` and `assets/screenshots-tablet-10/` contain the localized tablet campaigns.
- `measurement/` records the baseline and the decision rule for later experiments.
- `data-safety-review.md` records the privacy impact of the Google Play review library.

Run `python3 tools/validate_play_store_assets.py` before preparing a release. Uploads may be saved as pending Play Console changes, but publishing the store listing or promoting a bundle to production requires explicit approval.
