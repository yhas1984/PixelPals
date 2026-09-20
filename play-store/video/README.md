# PixelPals Google Play preview video

This folder contains the localized portrait preview video for Google Play and
the real Android screen recordings used to build it.

Build both localized versions from the repository root:

```bash
./tools/build_play_store_video.sh
```

Outputs:

- `exports/pixelpals-play-store-preview-es.mp4`
- `exports/pixelpals-play-store-preview-en.mp4`

The videos use real PixelPals UI footage for the home, care, overlay, adventure
and memory scenes. The ASO feature artwork appears only in the short opening
and closing cards. A two-second celebratory illustration gathers the full cast near
the end. The total duration is 28 seconds and the core experience starts after
1.5 seconds.

## Audio

`tools/generate_promo_soundtrack.py` creates the soundtrack from synthesized
tones and procedural noise. It contains no licensed samples or third-party
music. Ginger has a soft purr and dream chime; Bloop has airy bell-like
shimmers; care actions receive quiet tap cues; adventures add a subtle breeze.
The final mix targets -16 LUFS with a -1.5 dB true-peak ceiling.

The soundtrack is promotional post-production. It should not be presented as
an exact recording of sounds emitted by the application.

## AI-assisted artwork

`art/pixelpals-all-pets-party.png` is an AI-assisted promotional illustration
created from the application's complete pet reference sheet and the existing
ASO art direction. It is shown as key art, never as an app screenshot. The
video's feature artwork also contains AI-assisted artwork. These assets must be
declared in Play Console wherever its asset declaration flow requests it.

## Publishing checklist

- Upload the selected locale video to YouTube as public or unlisted.
- Disable monetization and ads for the video.
- Keep embedding enabled and do not age-restrict it.
- Paste the full YouTube URL into the corresponding Google Play listing.
- Review the Spanish and English previews in Play Console before submitting.
- Declare AI assistance for any listing artwork that Play Console identifies as
  AI-generated or AI-edited. The application footage itself is a real capture.
