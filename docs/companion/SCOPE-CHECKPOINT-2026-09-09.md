# Full-plan scope checkpoint

The approved scope remains the entire 15-pet offline companion experience. This is a partial source review to guide remaining work, not a production certification or a claim that every requirement has been audited. The prior turn made verified progress on Taro's prop placement; that does not close the animation milestone.

## Requirements and evidence still needed

| Requirement group | Current source/evidence inspected or located | Acceptance status / required proof |
| --- | --- | --- |
| Preserve classic repository and local version; Kotlin/Views/Room architecture | Current branch remains `feature/pixelpals-living-companion`; current changes build on 94a1d1d | Continue preserving prior work; release manifest/signature must be checked on the eventual candidate |
| Warm shared aesthetic, home diorama, hourly light, 15 species habitats, 3 environments | HomeScenePainter, PetHabitatPainter, HomeModels; prior prop consistency checks | Partial; all-screen visual/color review and every habitat at different times still required |
| Four navigation areas, settings from home, illustrated diary/album | LivingHomeFragment, AdventuresFragment, current navigation tests from prior checkpoint | Object events were excluded in both DAO and UI; fixed in the accompanying change. Full current runtime flow still required |
| First adoption/name/care/home and existing-user introduction | LivingHomeFragment introduction/adoption source inspected | Dialog flow exists; onboarding end-to-end, denied/revoked permissions and recreation still need current acceptance evidence |
| Shared needs, personality, learned preferences, reversible illness and absence | CompanionRepository, CompanionViewModel; learning records persist independently of journal presentation | Source and prior targeted tests only; all 15 home/desktop consistency and absence combinations remain open |
| All 15 care actions, natural anatomy, contact, gait, turns, stable size | Care renderers, recent Corgi/Taro changes and pixel tests | Incomplete: intermediate drawings, continuous visual acceptance and other species' animation refinements remain required |
| Beds/toys/24 decorations, finger placement plus accessible controls, persistence, treasures | HomeModels, HomeSceneView, shared CareDecorationSelection | Catalogue/placement exists; full catalogue interaction and accessibility acceptance is incomplete |
| No permanent desktop ball; compatible selected props | Recent fetch retirement and shared Corgi/Ginger/Taro toys | Alternative-toy choreography for remaining species and Corgi pinwheel still incomplete |
| Three timed expeditions, paused care/desktop, reopen/clock/reboot/cancel/idempotent return | CompanionRepository refresh/start/finish and ExpeditionDestination inspected | Source contains transaction and elapsed-clock handling; current full scenario/device verification remains needed |
| Illustrated adoption/play/discovery/bond memories and postcards | AdventuresFragment, MemoryIllustration, PostcardExporter inspected | Object memories missing before this pass; postcard sharing/recreation/error handling and complete illustrated coverage still need acceptance |
| Preserve wallet/purchases/rights; previews; free/earned/coin decoration; no new paid products | Decoration/Pet/Cosmetic preview call sites; billing fulfillment ordering inspected | Local grant now precedes consume; this does not prove real Play purchase/restore recovery. Test-track evidence remains required |
| Existing app-open/banner/consent/frequency | Ads classes located; no changes in this pass | Current runtime consent/frequency matrix not revalidated here |
| Spanish/English, large text, contrast, labels, gesture alternatives, audio/haptics/reduced motion | Localized resources and existing settings/rendering code | Key checks and samples are not complete accessibility acceptance |
| Migration from supported schemas without deletion; transaction safety after restart | AppDatabase v10 and migration registration 1→10 inspected, no destructive fallback in builder | Current candidate migration/concurrency/restart tests required; previous green evidence is scoped to its revision |
| JVM/assets/lint/instrumentation; memory/battery/fluidity comparison; long physical soak; release candidate | Recent checks documented per change; `carePreview` assets are opt-in for release | Incomplete. Normal release excludes candidate care artwork. Final art review, performance/soak, signing, Play validation and explicit production approval remain open |
| Later user requirements: Yuki device heat, snowballs/shower/melt; DND/sleep schedule/dreams; sleep depth; matching props | Implemented source paths and prior targeted notes exist | Require consolidated all-state runtime acceptance, including reduced motion and desktop priority |

No requirement should be marked complete merely because its class exists or a narrower test passes. Subsequent work should close these acceptance gaps as well as concrete bugs. Failed Corgi/Menta image-generation candidates remain outside app assets; do not repeat identical failed generation requests or silently promote them.
