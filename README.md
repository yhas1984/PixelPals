# PixelPals

[Español](#español) | [English](#english)

Privacy Policy / Politica de Privacidad: [PRIVACY_POLICY.md](PRIVACY_POLICY.md)

---

## Español

PixelPals es una app Android de mascotas virtuales flotantes que viven sobre la pantalla mediante una superposición (overlay).  
Cada mascota tiene lógica de movimiento propia, animaciones, interacción táctil, cosméticos equipables y progreso persistente.

### Características principales

- **14 mascotas** con comportamientos personalizados (terrestres, voladoras, acuáticas y trepadoras de bordes).
- Servicio en primer plano para mantener el pet activo sobre otras apps, con visibilidad por política (solo lanzador con acceso de uso).
- Sistema de interacción táctil (tap, arrastre y fling con física por especie).
- **Tienda**: cosméticos (tint, aura, float), mascotas premium y monedas con monedero global (Room) + Google Play Billing.
- Álbum de tesoros con persistencia local (Room + `SharedPreferences`).
- Progreso de mascota (XP, minutos activos, interacciones, estado con decay, humor y vínculo).
- **Consumo mínimo**: frame pacing adaptativo (60/30/12 FPS), timestep fijo 1/60 s con acumulador, caches de bitmaps acotados (`LruCache`) y escrituras de progreso con batching.

### Stack técnico

- Kotlin
- `compileSdk 36`, `targetSdk 36`, `minSdk 26`
- Room (KSP) + Coroutines
- Foreground Service + Overlay (`SYSTEM_ALERT_WINDOW`, `specialUse`)
- Google Play Billing
- Release con R8 y `shrinkResources` activados; los frames se referencian directamente (`R.drawable.*`), sin `getIdentifier()`.

### Estructura del proyecto

- `app/src/main/java/com/pixelpals/app/`: `MainActivity`, `PetSelectionActivity`, `PetService`, `PetView` y soporte del overlay (`DesktopForegroundHelper`, `ScreenStateReceiver`).
- `app/src/main/java/com/pixelpals/app/core/`: dominio (`PetType`, `PetState`), física (`MotionEngine`, `PetBounds`, `MokiMotionController`), analytics y servicios (`AppServices`).
- `app/src/main/java/com/pixelpals/app/feature/overlay/behavior/`: lógica por mascota (`BaseBehavior` + 14 behaviors) y contrato con la vista (`PetViewBridge`).
- `app/src/main/java/com/pixelpals/app/feature/store/`: tienda (cosméticos, mascotas, monedas) y billing.
- `app/src/main/java/com/pixelpals/app/feature/treasure/`: álbum de tesoros.
- `app/src/main/java/com/pixelpals/app/data/`: catálogos, prefs y repositorios (`PixelPalsRepository`, `PetProgress`).
- `app/src/main/java/com/pixelpals/app/database/`: entidades Room y DAOs.
- `app/src/main/java/com/pixelpals/app/status/`: dashboard de estado, humor, cuidados y vínculo.
- `app/src/main/assets/pets/`: atlas por mascota (`*_sheet_v1.json` + PNG), referenciados desde los behaviors.
- `tools/`: scripts de importación y validación de assets (PIL).

### Versiones publicadas (referencia)

Definidas en [`app/build.gradle.kts`](app/build.gradle.kts):

- `versionName`: `2.1.0`
- `versionCode`: `16` (Play exige subir el código en cada nuevo envío)

### Requisitos

- JDK 17
- Android SDK (instalar la plataforma de `compileSdk`, p. ej. Android API 36)
- Gradle Wrapper incluido (`./gradlew`)
- Dispositivo físico o emulador Android 8.0+ (`minSdk` 26).

### Configuración local

Crea o verifica `local.properties`:

```properties
sdk.dir=/ruta/a/Android/Sdk
```

### Build e instalación

Debug:

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

Release:

```bash
./gradlew :app:assembleRelease
./gradlew :app:bundleRelease
```

Salidas típicas:

- APK: `app/build/outputs/apk/release/`
- AAB: `app/build/outputs/bundle/release/`

Tests:

```bash
./gradlew testDebugUnitTest               # tests JVM (motor físico, etc.)
./gradlew connectedDebugAndroidTest       # tests instrumentados (requiere dispositivo/emulador)
```

### Publicidad (AdMob)

La app muestra un **App Open desde el tercer uso, como máximo una vez por proceso**, y la tienda conserva un **banner adaptativo no inmersivo** al pie (`StoreFragment`; `StoreActivity` solo redirige por compatibilidad). El App Open nunca se dispara al mostrar mascotas ni al abrir el overlay flotante. Funcionamiento por build:

- **Debug**: banner habilitado con el **ID de prueba de Google**; App Open usa su ID de prueba solo cuando se activa con `-Ppixelpals.ads.debugAppOpen=true` (por defecto queda apagado para que las pruebas instrumentadas sean deterministas).
- **Release**: App Open usa `pixelpals.admob.appOpenId` o el ID real configurado y respeta la frecuencia desde el tercer uso; el banner solo aparece si se configura su unidad.

No existe una compra de monedas para eliminar anuncios; la tienda solo contiene mascotas premium, cosméticos y packs de monedas.

Para activar anuncios reales:

1. Crea la cuenta en [admob.google.com](https://admob.google.com) (se puede vincular a una cuenta AdSense existente). Si la app está en prueba cerrada y Play no la detecta, añádela manualmente con el nombre y paquete (`com.pixelpals.app`).
2. En AdMob: **Apps** → tu app → **Ad units** → crea un **Banner adaptativo** (anclado).
3. Añade en `gradle.properties` (NO comitear estos valores):

```properties
pixelpals.admob.appId=ca-app-pub-XXXXXXXXXXXXXXXX~XXXXXXXX
pixelpals.admob.bannerId=ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXX
pixelpals.admob.appOpenId=ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXX
```

3b. **app-ads.txt**: sube el archivo [`app-ads.txt`](app-ads.txt) (en la raíz del repo) a un dominio web propio y verifícalo en AdMob (**Settings → Website verification**). En apps Android este archivo NO puede ir dentro del APK: Google lo lee desde `https://tu-dominio.com/app-ads.txt`.

4. Antes de la revisión de Play: declara **Anuncios** en la ficha (Data safety) e integra el SDK UMP (consentimiento EEE/UK).

> **Importante**: durante pruebas con anuncios reales (closed testing incluida), no generar impresiones/clicks desde tus propios dispositivos (riesgo de suspensión de cuenta). Usa los test ads del build debug o [Ad Inspector](https://support.google.com/admob/answer/13072382).

### Permisos sensibles

- `SYSTEM_ALERT_WINDOW`: necesario para mostrar la mascota flotante.
- `POST_NOTIFICATIONS`: para notificaciones (Android 13+).
- `PACKAGE_USAGE_STATS`: opcional; solo detecta si el lanzador está en primer plano para ocultar el pet sobre otras apps.
La app no está diseñada para capturar contraseñas, texto sensible ni contenido privado de otras apps.

### Google Play Submission Notes (ES)

Texto recomendado para la ficha o formulario de revisión:

**Uso de superposición (`SYSTEM_ALERT_WINDOW`)**
- La función principal de la app es mostrar una mascota virtual flotante en pantalla.
- Este permiso se usa exclusivamente para dibujar la mascota y sus animaciones sobre otras apps.
- No se utiliza para leer contenido de otras apps, capturar texto ni interactuar con contraseñas.

**Declaración de privacidad**
- No se venden datos personales.
- El usuario puede desactivar permisos en cualquier momento desde Ajustes.
- Si no se concede overlay, la app puede funcionar con experiencia limitada.

### Estado del proyecto

V1 estable; versiones recientes alineadas con **Google Play** (`targetSdk 36`, documentación y política de privacidad).  
Línea de trabajo actual: física por especies (perfiles terrestre/volador/acuático sobre el núcleo compartido `PetBounds` + `MotionEngine`).

---

## English

PixelPals is an Android virtual pet app that displays floating pets on top of other apps using an overlay.  
Each pet has its own movement logic, animation set, touch interactions, equippable cosmetics, and persistent progression.

### Main features

- **14 pets** with custom behaviors (ground, flying, aquatic, and edge-crawling species).
- Foreground service to keep pets active above other apps, with policy-based visibility (launcher-only when usage access is granted).
- Touch interactions (tap, drag, and species-aware fling physics).
- **Store**: cosmetics (tint, aura, float), premium pets, and coins with a global wallet (Room) + Google Play Billing.
- Treasure album with local persistence (Room + `SharedPreferences`).
- Pet progression (XP, active minutes, interactions, decaying stats, mood, and bond).
- **Low consumption**: adaptive frame pacing (60/30/12 FPS), fixed 1/60 s timestep with accumulator, bounded bitmap caches (`LruCache`), and batched progress writes.

### Tech stack

- Kotlin
- `compileSdk 36`, `targetSdk 36`, `minSdk 26`
- Room (KSP) + Coroutines
- Foreground Service + Overlay (`SYSTEM_ALERT_WINDOW`, `specialUse`)
- Google Play Billing
- Release with R8 and `shrinkResources` enabled; frames are referenced directly (`R.drawable.*`), no `getIdentifier()`.

### Project structure

- `app/src/main/java/com/pixelpals/app/`: `MainActivity`, `PetSelectionActivity`, `PetService`, `PetView`, and overlay support (`DesktopForegroundHelper`, `ScreenStateReceiver`).
- `app/src/main/java/com/pixelpals/app/core/`: domain (`PetType`, `PetState`), physics (`MotionEngine`, `PetBounds`, `MokiMotionController`), analytics, and services (`AppServices`).
- `app/src/main/java/com/pixelpals/app/feature/overlay/behavior/`: per-pet logic (`BaseBehavior` + 14 behaviors) and the view contract (`PetViewBridge`).
- `app/src/main/java/com/pixelpals/app/feature/store/`: store (cosmetics, pets, coins) and billing.
- `app/src/main/java/com/pixelpals/app/feature/treasure/`: treasure album.
- `app/src/main/java/com/pixelpals/app/data/`: catalogs, prefs, and repositories (`PixelPalsRepository`, `PetProgress`).
- `app/src/main/java/com/pixelpals/app/database/`: Room entities and DAOs.
- `app/src/main/java/com/pixelpals/app/status/`: status dashboard, mood, care, and bond.
- `app/src/main/assets/pets/`: per-pet atlases (`*_sheet_v1.json` + PNG), referenced from behaviors.
- `tools/`: asset import and validation scripts (PIL).

### Published versions (reference)

Defined in [`app/build.gradle.kts`](app/build.gradle.kts):

- `versionName`: `2.1.0`
- `versionCode`: `16` (Play requires a higher code for each new upload)

### Requirements

- JDK 17
- Android SDK (install the `compileSdk` platform, e.g. Android API 36)
- Included Gradle Wrapper (`./gradlew`)
- Android 8.0+ device or emulator (`minSdk` 26).

### Local setup

Create or verify `local.properties`:

```properties
sdk.dir=/path/to/Android/Sdk
```

### Build and install

Debug:

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

Release:

```bash
./gradlew :app:assembleRelease
./gradlew :app:bundleRelease
```

Typical outputs:

- APK: `app/build/outputs/apk/release/`
- AAB: `app/build/outputs/bundle/release/`

Tests:

```bash
./gradlew testDebugUnitTest               # JVM tests (physics engine, etc.)
./gradlew connectedDebugAndroidTest       # instrumented tests (requires device/emulator)
```

### Google Play (AAB)

Current Play policy requires **minimum target API 36**; this project uses `targetSdk 36`.

- Build: `./gradlew :app:bundleRelease`
- The release task requires the configured release keystore and writes
  `app/build/outputs/bundle/release/app-release.aab`.
- **Do not upload** an AAB signed only with the **debug** key; Play will reject it.
- Sign with your **release keystore** (or configure `signingConfigs.release` in Gradle and use Play App Signing).
- Each upload needs a **higher `versionCode`** than the last one accepted in the console.

### Advertising (AdMob)

The app shows an **App Open ad from the third use, at most once per process**, and the store shows a **non-intrusive adaptive banner** at the bottom (`StoreFragment`; `StoreActivity` is a compatibility redirect). The App Open ad is never triggered when pets are rendered or when the floating overlay opens. Behavior per build:

- **Debug**: the banner is enabled with its **Google test ID**; App Open uses its test ID only when enabled with `-Ppixelpals.ads.debugAppOpen=true` (off by default so instrumentation remains deterministic).
- **Release**: App Open uses `pixelpals.admob.appOpenId` or the configured production unit and respects the third-use frequency; the banner is shown only when its unit is configured.

There is no coin purchase to remove ads; the store only contains premium pets, cosmetics, and coin packs.

To enable real ads:

1. Create the account at [admob.google.com](https://admob.google.com) (can be linked to an existing AdSense account). If the app is in closed testing and Play does not detect it, add it manually with name and package (`com.pixelpals.app`).
2. In AdMob: **Apps** → your app → **Ad units** → create an **Adaptive banner** (anchored).
3. Add to `gradle.properties` (do NOT commit these values):

```properties
pixelpals.admob.appId=ca-app-pub-XXXXXXXXXXXXXXXX~XXXXXXXX
pixelpals.admob.bannerId=ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXX
pixelpals.admob.appOpenId=ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXX
```

3b. **app-ads.txt**: host the [`app-ads.txt`](app-ads.txt) file (repo root) on your own web domain and verify it in AdMob (**Settings → Website verification**). For Android apps this file CANNOT ship inside the APK: Google reads it from `https://your-domain.com/app-ads.txt`.

4. Before Play review: declare **Ads** in the listing (Data safety) and integrate the UMP SDK (EEA/UK consent).

> **Important**: when testing with real ads (including closed testing), do not generate impressions/clicks from your own devices (account suspension risk). Use the debug build's test ads or [Ad Inspector](https://support.google.com/admob/answer/13072382).

### Sensitive permissions

- `SYSTEM_ALERT_WINDOW`: required to render floating pets.
- `POST_NOTIFICATIONS`: notifications support (Android 13+).
- `PACKAGE_USAGE_STATS`: optional; only detects when the launcher is in the foreground to hide the pet over other apps.
The app is not intended to capture passwords, sensitive text, or private content from other apps.

### Google Play Submission Notes (EN)

Suggested wording for store listing or review forms:

**Overlay permission (`SYSTEM_ALERT_WINDOW`)**
- The app's core feature is displaying a floating virtual pet on top of other apps.
- This permission is used strictly to render the pet and its animations.
- It is not used to read other apps' content, capture text, or interact with passwords.

**Privacy statement**
- No personal data is sold.
- Users can revoke permissions at any time in system settings.
- If overlay permission is denied, the app can still run with limited experience.

### Project status

V1 stable; recent releases aligned with **Google Play** (`targetSdk 36`, docs and privacy policy).  
Current work line: species-aware physics (ground/flying/aquatic profiles over the shared `PetBounds` + `MotionEngine` core).
