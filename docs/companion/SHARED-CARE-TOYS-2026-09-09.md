# Shared selected care toys

The room care panel previously ignored the home's selected toy, and species desktop care always drew its default prop. Corgi desktop care also filtered compatibility after choosing a favorite, which could discard a usable second placed toy.

`CareDecorationSelection.careToy` now filters compatible placed toys before resolving the favorite. Corgi and Ginger support the starter toy, yarn and star toy. Other species keep their native care object; the starter `ball` decoration already resolves visually to each species' own toy. This preserves Yuki's snowballs and Imp's balloon gameplay.

Room care, Corgi desktop care and species desktop care capture the same selection. `CareStageView`, both room render paths and the species desktop renderer pass it to the existing shared prop painter, so decorative artwork is reused rather than redrawn. Food and beds retain their separate selection. Stored/unplaced toys are not equipped.

Validation: debug/test APK builds, lint and all 233 JVM tests passed. Six targeted Android rendering tests passed on the connected phone (9.947 seconds), including the 15-pet renderer checks and a new Corgi/Ginger test proving the selected yarn changes the room/desktop drawing, does not replace food and clears when unequipped. APK installed successfully. No full Android-suite rerun in this change.

Remaining: alternative-toy choreography for the other species and Corgi pinwheel gameplay are not implemented here. Their placed decorations remain available in the home. The full art, movement and interaction acceptance plan remains open.
