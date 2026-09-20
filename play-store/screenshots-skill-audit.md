# App Store Screenshots skill audit

The PixelPals Google Play deck uses the version 2 editor supplied by the
`app-store-screenshots` skill. The canonical project has connected-canvas
support enabled, localized copy for `es` and `en`, and dedicated decks for
Android phones, 7-inch tablets, 10-inch tablets, and the 1024 x 500 feature
graphic.

## Final quality decisions

- Every slide sells one benefit with a short headline and a real product capture.
- The first two slides form the acquisition pair: emotional home experience,
  then the pet's optional presence over another app.
- Layouts alternate between hero, device-top, and device-bottom; dark contrast
  slides break the visual rhythm without changing the warm brand palette.
- Screens remain isolated inside their exports. A cross-screen object was not
  added because Play may surface any image independently and the pet and UI must
  remain complete in every crop. Continuity comes from the Corgi, typography,
  device treatment, palette, and the benefit progression.
- The final slide retains the requested bond message instead of becoming a
  generic feature wall.
- All phone and tablet images are opaque, zero-padded, and readable at a
  160-pixel thumbnail width. No text, face, pet, or interactive UI is cut at an
  export boundary.

The feature graphic is also available inside the editor. Its supplied artwork
is intentionally rendered as a complete 1024 x 500 composition so the five-pet
scene and localized typography are reproduced exactly during export.
