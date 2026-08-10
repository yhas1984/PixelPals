# Plan Maestro v1.5 — Corrección total + Tienda nueva

**Status:** Aprobado por el usuario el 2026-08-04.
**Decisiones locked-in:**
- Marca pública: **PixelPals** (migración completa)
- Refactor alcance: **Completo + modularización Gradle**
- Monetización: **IAPs one-time + soft coins** (sin suscripción, sin backend)
- Gadgets: **Cosmético + funcional mixto** (sistema `PetModifier`)
- Validación: **Cliente + `acknowledgeSafely`** (sin backend)
- Catálogo V1: **12-15 accesorios** (~18 con los 5 actuales migrados)

---

## FASE 0 — Migración de marca a PixelPals
**Duración:** 0.5-1 día

- [ ] `app_name`: "ScreenPets" → "PixelPals" en `values/strings.xml` y `values-es/strings.xml`.
- [ ] `Theme.ScreenPets` → `Theme.PixelPals` en `values/themes.xml`.
- [ ] Reemplazar `android:theme="@style/Theme.ScreenPets"` en `AndroidManifest.xml`.
- [ ] README: corregir `versionCode 2 / versionName 1.0.1` → versión real; cambiar `com.screenpets.app` → `com.pixelpals.app`.
- [ ] Crear `CHANGELOG.md` con v1.0.0 → v1.5.0.
- [ ] Buscar y reemplazar "ScreenPets" en código Kotlin (imports, comentarios).

**Criterios de aceptación:**
- `grep -r "ScreenPets" app/src/main` solo devuelve 0 hits (o solo en CHANGELOG).
- Compila. Launcher muestra "PixelPals".

---

## FASE 1 — Fix de bugs críticos (P0)
**Duración:** 2 días

### 1.1 Doble fuente de verdad del tesoro
- Borrar de `PetProgress`: `treasures` (pref), `getTreasureMap`, `saveTreasureMap`, `decodeTreasureMap`, `syncLegacyTreasureState`, `syncRoomTreasureState`, `addTreasureInternal`, `addTreasure`, `consumeTreasure`, métodos `maybeAwardTreasureFrom*`.
- Mover la lógica de hitos de tesoro a `PixelPalsRepository` (campos `lastTreasureInteractionMilestone`, `lastTreasureActiveMilestone` en `PetBondEntity`).
- `TreasureAlbumActivity` lee directo de `TreasureDao`.

### 1.2 Atomicidad en `PixelPalsRepository`
- Envolver `applyMutation`, `applyCareAction`, `completeDailyTask` en `db.withTransaction { }`.

### 1.3 Fix bug timezone `getMemories`
- `PixelPalsRepository.kt:212`: usar `Instant.ofEpochMilli(...).atZone(ZoneId.systemDefault()).toLocalDate()`.

### 1.4 Cachear screen metrics
- `PetView.kt:580-592`: solo refresh en `onAttachedToWindow` y `onConfigurationChanged`.

### 1.5 Subir polling foreground
- `PetService.kt`: `HOME_POLL_INTERVAL_MS = 15_000L`, `HOME_POLL_INTERVAL_SLOW_MS = 60_000L`.

### 1.6 Evitar writes redundantes en `applyDecay`
- Comparar antes de `upsert`.

### 1.7 Eliminar estados muertos en `PetState`
- Borrar `WALKING`, `LANDING`, `SECRET_IDLE`, `SYSTEM_REACTION`.

### 1.8 Eliminar `setProgress` muerto en `PetViewBridge`
- Borrar método + import `PetProgress` en la interfaz.

**Criterios de aceptación:**
- Tests existentes pasan (`MotionEngineTest`, `EntityDefaultsTest`, `AppDatabaseMigrationTest`, `PetBehaviorSmokeTest`, `EngagementLoopTest`).
- `PetProgress` solo mantiene XP/level/stats; sin tesoro.
- 1h con servicio activo: ≤4 escrituras de decay por mascota.

---

## FASE 2 — Modularización Gradle
**Duración:** 1-1.5 días

Estructura:
```
PixelPals/
├── app/                          (entrypoint)
├── core/
│   ├── common/                   (MotionEngine, PetRandom, utils)
│   ├── domain/                   (PetType, PetState, PetMood, CareAction, PetPersonality)
│   └── analytics/                (AnalyticsTracker)
├── data/
│   ├── database/                 (Room AppDatabase + DAOs + entities)
│   ├── prefs/                    (SelectedPetStore, SettingsStore)
│   ├── repository/               (PixelPalsRepository + sub-repos)
│   └── catalog/                  (Catálogo desde JSON asset)
├── feature/
│   ├── overlay/                  (PetView + PetService + Behaviors)
│   ├── selection/                (PetSelectionActivity)
│   ├── dashboard/                (PetDashboardActivity)
│   ├── treasure/                 (TreasureAlbumActivity)
│   └── store/                    (StoreActivity + BillingRepository)
└── build-logic/                  (convention plugins)
```

**Criterios de aceptación:**
- `./gradlew :app:assembleDebug` compila.
- Cada `:feature:*` se compila individualmente.
- APK ≤ +5% overhead por módulos.

---

## FASE 3 — Refactor arquitectónico
**Duración:** 3-4 días

### 3.1 Hilt
- Hilt 2.51.1, KSP, `PixelPalsApplication` con `@HiltAndroidApp`.
- Borrar `AppServices` (service locator).
- Módulos: `DatabaseModule`, `RepositoryModule`, `BillingModule`, `AnalyticsModule`.

### 3.2 Split de `PixelPalsRepository`
- `PetStatusRepository` (status, decay, mutation)
- `PetBondRepository` (interaction, care, bond, daily tasks)
- `OwnedProductRepository` (catalog, grants, ownership)
- `AccessoryCatalog` (data-only desde JSON)
- `PixelPalsRepository` → fachada fina o eliminar.

### 3.3 Migrar `PetProgress` a Room
- Nueva entidad `PetProgressEntity(petId PK, totalXp, rareEvents, updatedAt)`.
- Migración Room 2→3.
- Tests existentes siguen pasando.

### 3.4 Eliminar estados/enums muertos
- `PetState` solo 5 estados.
- Borrar `MovementStyle/IdleStyle/InteractionStyle` (no usados).

---

## FASE 4 — Catálogo nuevo de accesorios (data + assets)
**Duración:** 2 días

### 4.1 Modelo nuevo
```kotlin
data class AccessoryCatalogItem(
    val id: String,
    val productId: String,
    val displayName: String,
    val description: String,
    val emoji: String,
    val slot: AccessorySlot,        // HEAD, FACE, BACK, GADGET, BODY
    val visual: AccessoryVisual,    // EmojiOverlay | SpriteOverlay
    val modifiers: List<PetModifier>,
    val isPremium: Boolean,
    val packLabel: String,
    val supportedPetIds: Set<String>,
    val coinPrice: Int? = null,
    val bondRequired: Int = 0,
    val tags: Set<String> = emptySet(),
)

enum class AccessorySlot { HEAD, FACE, BACK, GADGET, BODY }

sealed class AccessoryVisual {
    data class EmojiOverlay(val offsetXRatio: Float, val offsetYRatio: Float, val scale: Float) : AccessoryVisual()
    data class SpriteOverlay(@DrawableRes val drawableResId: Int, val offsetX: Float, val offsetY: Float, val scale: Float, val frames: List<DrawableFrame> = emptyList()) : AccessoryVisual()
}

sealed class PetModifier {
    data class SpeedBoost(val multiplier: Float) : PetModifier()
    data class TrailParticles(val type: ParticleType) : PetModifier()
    data class SoundEffect(@RawRes val soundResId: Int) : PetModifier()
    data class AnimationOverride(val modeName: String) : PetModifier()
}
```

### 4.2 `PetModifier` aplicado
- `PetViewBridge.activeModifiers(): List<PetModifier>`.
- `BaseBehavior.getBaseSpeed()` × primer `SpeedBoost`.
- `TrailParticles` se emite en `updateIdle` → dibuja en `onDraw`.

### 4.3 Catálogo JSON
- `assets/accessories/catalog.json` con 18 accesorios (5 migrados + 13 nuevos).

### 4.4 Renderizado
- `PetView.drawAccessory()` soporta `AccessorySlot`.
- Pase doble: alas detrás del sprite, cabeza/cara encima.

### Catálogo V1 (18 ítems)
1. `halo_glow` (HEAD, ya existe)
2. `royal_crown` (HEAD, ya existe)
3. `star_trail` (BODY, ya existe)
4. `cozy_scarf` (BODY, ya existe)
5. `party_spark` (BODY, ya existe)
6. `celestial_wings` (BACK, gadget funcional: +10% speed + stardust)
7. `demonic_wings` (BACK, gadget funcional: +10% speed + fire trail)
8. `duck_jetpack` (GADGET, gadget funcional: +18% speed + jetpack_hop)
9. `angel_halo` (HEAD, cosmético premium; migra `halo_glow`)
10. `round_glasses` (FACE, cosmético)
11. `pilot_glasses` (FACE, cosmético)
12. `magic_hat` (HEAD, cosmético)
13. `tiara` (HEAD, cosmético)
14. `crown` (HEAD, cosmético)
15. `bowtie` (BODY, cosmético)
16. `rainbow_scarf` (BODY, cosmético)
17. `star_trail` (BODY, ya existe)
18. `party_spark` (BODY, ya existe)

---

## FASE 5 — Tienda nueva (UI + IAPs)
**Duración:** 3-4 días

### 5.1 SKUs en Play Console
- `coins_small` — 100 monedas — €0.99
- `coins_medium` — 350 monedas — €1.99
- `coins_large` — 1000 monedas — €4.99
- `coins_mega` — 2500 monedas — €9.99 (badge +5%)
- `pack_celestial` — €2.99 (alas + halo + 100 coins)
- `pack_demonic` — €2.99 (alas + corona roja + 100 coins)
- `pack_adventure` — €2.99 (jetpack + gafas piloto + 100 coins)

### 5.2 Whitelist Billing
- Actualizar `ALLOWED_PRODUCT_IDS` con la lista completa.

### 5.3 UI rediseñado
- `activity_store_v2.xml` con `TabLayout` + `ViewPager2`.
- Tab 1 "Monedas", Tab 2 "Accesorios", Tab 3 "Packs".
- Filtros por slot/pet.

### 5.4 Modelo `CoinProduct`
```kotlin
data class CoinProduct(
    val productId: String,
    val displayName: String,
    val coinAmount: Int,
    val bonusBadge: String?,
    val formattedPrice: String,
    val bestValueFlag: Boolean,
)
```

### 5.5 Tests
- `CoinProductMapperTest`
- `StorePurchaseFlowTest` (debug billing)

---

## FASE 6 — Pulido
**Duración:** 3-4 días

- [ ] i18n: externalizar emojis hardcoded → `strings.xml` arrays.
- [ ] Tests JVM con robolectric + coroutines-test.
- [ ] CI: GitHub Actions (assembleDebug + test + lint).
- [ ] Detekt + ktlint.
- [ ] Accesibilidad: `contentDescription` en `PetView`.
- [ ] Baseline Profile + macrobenchmark.
- [ ] WebP conversion de sprites grandes.

---

## FASE 7 — Release & post-launch
**Duración:** 1-2 días

- [ ] Bundle release con keystore.
- [ ] `versionCode` increment (8 → 9) y `versionName` semántico (`1.5.0`).
- [ ] Play Console: declarar `FOREGROUND_SERVICE_SPECIAL_USE` con justificación.
- [ ] Internal testing → Closed testing → Production staged rollout.
- [ ] Monitoreo Vitals 72h.

---

## Riesgos y mitigaciones

| Riesgo | Mitigación |
|---|---|
| Hilt rompa más de lo esperado | Tener git reset a mano; PRs pequeños por módulo |
| Migración Room 2→3 falle | Test exhaustivo con `MigrationTestHelper`; red de seguridad `fallbackToDestructiveMigration` solo como último recurso |
| Play rechace `specialUse` FGS | Documentar bien + video demo |
| Asset pipeline genere PNGs gigantes | Validar con `tools/validate_pet_assets.py` |
| Gadgets funcionales rompan física | Tests unitarios para `BaseBehavior.getBaseSpeed()` + revisión visual |
| 13 accesorios nuevos requieren assets | Empezar con placeholders, mejorar post-launch |

---

## Estimación total: 16-22 días
